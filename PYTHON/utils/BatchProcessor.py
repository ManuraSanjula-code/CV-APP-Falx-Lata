import json
import threading
import time
from datetime import datetime
from typing import List

from utils.BulkProcessingQueue import get_processing_queue
from utils.ProcessingJob import ProcessingJob
from utils.cv_utils import CVUtils


class BatchProcessor:
    """High-level batch processor that coordinates queue operations with Redis storage."""

    def __init__(self, redis_conn, cv_processor, audit_logger):
        self.redis_conn = redis_conn
        self.cv_processor = cv_processor
        self.audit_logger = audit_logger
        self.queue = get_processing_queue()

    def process_batch_async(self, batch_id: str, file_list: List[tuple],
                            user_upload_dir: str, current_user_username: str,
                            request_ip: str) -> bool:
        """Process a batch of files asynchronously using the queue system."""

        # Create processing jobs
        jobs = []
        for i, (filename, temp_filepath) in enumerate(file_list):
            job = ProcessingJob(
                job_id=f"{batch_id}_job_{i}",
                batch_id=batch_id,
                filename=filename,
                temp_filepath=temp_filepath,
                user_upload_dir=user_upload_dir,
                priority=1  # High priority
            )
            jobs.append(job)

        # Add jobs to queue
        added_count = self.queue.add_bulk_jobs(jobs)

        if added_count < len(jobs):
            print(f"Warning: Only {added_count}/{len(jobs)} jobs added to queue")

        # Start monitoring thread for this batch
        monitor_thread = threading.Thread(
            target=self._monitor_batch_completion,
            args=(batch_id, len(jobs), current_user_username, request_ip)
        )
        monitor_thread.daemon = True
        monitor_thread.start()

        return added_count == len(jobs)

    def _monitor_batch_completion(self, batch_id: str, expected_jobs: int,
                                  username: str, request_ip: str):
        """Monitor batch completion and store results in Redis."""

        while True:
            status = self.queue.get_batch_status(batch_id)

            if status['status'] == 'completed' or status['completed_jobs'] + status['failed_jobs'] == expected_jobs:
                # Batch is complete, store results
                self._store_batch_results(batch_id, username, request_ip)
                break

            time.sleep(10)  # Check every 10 seconds

    def _store_batch_results(self, batch_id: str, username: str, request_ip: str):
        """Store completed batch results in Redis and clean up."""

        # Collect all results for this batch
        completed_results = []
        failed_results = []

        for job_id, data in self.queue.completed_jobs.items():
            if data['job'].batch_id == batch_id:
                cv_data = data['result']['cv_data']
                cv_id = CVUtils.generate_cv_id(cv_data['filename'])

                # Store CV in Redis
                try:
                    redis_data = {}
                    for key, value in cv_data.items():
                        if isinstance(value, (dict, list)):
                            redis_data[key] = json.dumps(value)
                        else:
                            redis_data[key] = str(value)

                    with self.redis_conn.client.pipeline() as pipe:
                        pipe.hset(cv_id, mapping=redis_data)
                        self.cv_processor.index_cv_data(cv_id, cv_data, pipe=pipe)
                        pipe.execute()

                    completed_results.append({
                        'id': cv_id,
                        'filename': cv_data['filename'],
                        'name': cv_data.get('personal_info', {}).get('name', ''),
                        'status': 'success',
                        'processing_time': cv_data.get('processing_time', 0)
                    })

                except Exception as e:
                    failed_results.append({
                        'filename': cv_data['filename'],
                        'status': 'error',
                        'error': f"Redis storage error: {str(e)}"
                    })

        # Collect failed jobs
        for job_id, data in self.queue.failed_jobs.items():
            if data['job'].batch_id == batch_id:
                failed_results.append({
                    'filename': data['job'].filename,
                    'status': 'error',
                    'error': data['error']
                })

        # Store final results
        final_result = {
            'batch_id': batch_id,
            'processed': completed_results,
            'errors': failed_results,
            'total_files': len(completed_results) + len(failed_results),
            'success_count': len(completed_results),
            'error_count': len(failed_results),
            'completed_at': datetime.now().isoformat(),
            'processing_mode': 'asynchronous_queue'
        }

        self.redis_conn.client.setex(
            f"batch_result:{batch_id}",
            7200,  # Store for 2 hours
            json.dumps(final_result)
        )

        # Log completion
        self.audit_logger.log_action(
            user=username,
            action='BULK_UPLOAD_COMPLETED',
            details={
                'batch_id': batch_id,
                'total_files': final_result['total_files'],
                'success_count': final_result['success_count'],
                'error_count': final_result['error_count']
            },
            ip_address=request_ip
        )

        print(
            f"Batch {batch_id} completed: {final_result['success_count']} success, {final_result['error_count']} errors")