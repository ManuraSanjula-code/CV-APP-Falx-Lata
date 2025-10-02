import queue
import threading
import time
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta
from typing import List

from utils.ProcessingJob import ProcessingJob
from utils.ResourceMonitor import ResourceMonitor


class BulkProcessingQueue:
    """Manages a queue of CV processing jobs with priority and retry logic."""

    def __init__(self, max_workers: int = 8, max_queue_size: int = 1000):
        self.max_workers = max_workers
        self.max_queue_size = max_queue_size
        self.job_queue = queue.PriorityQueue(maxsize=max_queue_size)
        self.active_jobs = {}
        self.completed_jobs = {}
        self.failed_jobs = {}
        self.executor = ThreadPoolExecutor(max_workers=max_workers)
        self._shutdown = False
        self._worker_threads = []
        self.resource_monitor = ResourceMonitor()

        # Start worker threads
        self._start_workers()
        self.resource_monitor.start_monitoring()

    def _start_workers(self):
        """Start background worker threads."""
        for i in range(self.max_workers):
            worker = threading.Thread(target=self._worker_loop, name=f"CVWorker-{i}")
            worker.daemon = True
            worker.start()
            self._worker_threads.append(worker)

    def _worker_loop(self):
        """Main worker loop for processing jobs."""
        while not self._shutdown:
            try:
                # Get next job with timeout
                try:
                    priority, job = self.job_queue.get(timeout=1)
                except queue.Empty:
                    continue

                # Check if we have enough resources
                resources_ok, resource_msg = self.resource_monitor.is_resources_available()
                if not resources_ok:
                    print(f"Deferring job {job.job_id}: {resource_msg}")
                    # Put job back in queue with lower priority
                    self.job_queue.put((priority + 1, job))
                    time.sleep(5)  # Wait before trying again
                    continue

                # Mark job as active
                self.active_jobs[job.job_id] = job

                # Process the job
                try:
                    result = self._process_job(job)
                    self.completed_jobs[job.job_id] = {
                        'job': job,
                        'result': result,
                        'completed_at': datetime.now()
                    }
                    print(f"Completed job {job.job_id}: {job.filename}")

                except Exception as e:
                    job.attempts += 1
                    print(f"Job {job.job_id} failed (attempt {job.attempts}): {e}")

                    if job.attempts < job.max_attempts:
                        # Retry with lower priority
                        self.job_queue.put((priority + job.attempts, job))
                    else:
                        # Mark as permanently failed
                        self.failed_jobs[job.job_id] = {
                            'job': job,
                            'error': str(e),
                            'failed_at': datetime.now()
                        }

                finally:
                    # Remove from active jobs
                    self.active_jobs.pop(job.job_id, None)
                    self.job_queue.task_done()

            except Exception as e:
                print(f"Worker error: {e}")
                time.sleep(1)

    def _process_job(self, job: ProcessingJob):
        """Process a single CV job."""
        # Import here to avoid circular imports
        from utils.pdf_parser import extract_cv_info
        from utils.cv_utils import CVUtils
        import shutil
        import os

        start_time = time.time()

        # Copy to user directory
        user_pdf_filepath = os.path.join(job.user_upload_dir, job.filename)
        shutil.copy2(job.temp_filepath, user_pdf_filepath)

        # Extract CV data
        cv_data = extract_cv_info(job.temp_filepath)

        if not cv_data or not cv_data.get('personal_info', {}).get('name'):
            if os.path.exists(job.temp_filepath):
                os.remove(job.temp_filepath)
            raise ValueError('No valid CV data extracted - possibly not a CV')

        # Add processing metadata
        cv_data.update({
            'filename': job.filename,
            'upload_date': datetime.now().isoformat(),
            'saved_pdf_path': user_pdf_filepath,
            'content_hash': CVUtils.generate_cv_content_hash(cv_data),
            'processing_time': time.time() - start_time,
            'batch_id': job.batch_id,
            'job_id': job.job_id
        })

        # Clean up temp file
        if os.path.exists(job.temp_filepath):
            os.remove(job.temp_filepath)

        return {
            'cv_data': cv_data,
            'processing_time': cv_data['processing_time']
        }

    def add_job(self, job: ProcessingJob) -> bool:
        """Add a job to the processing queue."""
        try:
            if self.job_queue.qsize() >= self.max_queue_size:
                return False

            self.job_queue.put((job.priority, job))
            return True

        except queue.Full:
            return False

    def add_bulk_jobs(self, jobs: List[ProcessingJob]) -> int:
        """Add multiple jobs to the queue. Returns number of jobs added."""
        added_count = 0
        for job in jobs:
            if self.add_job(job):
                added_count += 1
            else:
                break  # Queue is full
        return added_count

    def get_queue_status(self) -> dict:
        """Get current queue status."""
        return {
            'queue_size': self.job_queue.qsize(),
            'active_jobs': len(self.active_jobs),
            'completed_jobs': len(self.completed_jobs),
            'failed_jobs': len(self.failed_jobs),
            'max_queue_size': self.max_queue_size,
            'max_workers': self.max_workers,
            'resource_stats': self.resource_monitor.get_stats()
        }

    def get_batch_status(self, batch_id: str) -> dict:
        """Get status for a specific batch."""
        batch_jobs = {
            'queued': [job for _, job in list(self.job_queue.queue) if job.batch_id == batch_id],
            'active': [job for job in self.active_jobs.values() if job.batch_id == batch_id],
            'completed': [data['job'] for data in self.completed_jobs.values() if data['job'].batch_id == batch_id],
            'failed': [data['job'] for data in self.failed_jobs.values() if data['job'].batch_id == batch_id]
        }

        total = len(batch_jobs['queued']) + len(batch_jobs['active']) + len(batch_jobs['completed']) + len(
            batch_jobs['failed'])
        completed = len(batch_jobs['completed'])
        failed = len(batch_jobs['failed'])

        return {
            'batch_id': batch_id,
            'total_jobs': total,
            'completed_jobs': completed,
            'failed_jobs': failed,
            'active_jobs': len(batch_jobs['active']),
            'queued_jobs': len(batch_jobs['queued']),
            'progress_percentage': (completed / total * 100) if total > 0 else 0,
            'status': 'completed' if completed == total else 'failed' if failed == total else 'processing'
        }

    def cleanup_old_jobs(self, hours_old: int = 24):
        """Clean up old completed and failed jobs."""
        cutoff_time = datetime.now() - timedelta(hours=hours_old)

        # Clean completed jobs
        to_remove = [
            job_id for job_id, data in self.completed_jobs.items()
            if data['completed_at'] < cutoff_time
        ]
        for job_id in to_remove:
            del self.completed_jobs[job_id]

        # Clean failed jobs
        to_remove = [
            job_id for job_id, data in self.failed_jobs.items()
            if data['failed_at'] < cutoff_time
        ]
        for job_id in to_remove:
            del self.failed_jobs[job_id]

        return len(to_remove)

    def shutdown(self, wait_for_completion: bool = True):
        """Shutdown the processing queue."""
        print("Shutting down bulk processing queue...")
        self._shutdown = True

        if wait_for_completion:
            # Wait for current jobs to complete
            self.job_queue.join()

        # Shutdown executor
        self.executor.shutdown(wait=wait_for_completion)

        # Stop resource monitoring
        self.resource_monitor.stop_monitoring()

        # Wait for worker threads
        for worker in self._worker_threads:
            worker.join(timeout=5)


# Global queue instance
_global_processing_queue = None


def get_processing_queue() -> BulkProcessingQueue:
    """Get the global processing queue instance."""
    global _global_processing_queue
    if _global_processing_queue is None:
        from config import Config
        config = Config()
        _global_processing_queue = BulkProcessingQueue(
            max_workers=config.MAX_WORKERS,
            max_queue_size=config.MAX_FILES_PER_UPLOAD * 2
        )
    return _global_processing_queue

