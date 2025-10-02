import gc
import threading
import time

import psutil


class ResourceMonitor:
    """Monitor system resources during bulk processing."""


    def __init__(self, max_memory_mb: int = 4096, max_cpu_percent: float = 80.0):
        self.max_memory_mb = max_memory_mb
        self.max_cpu_percent = max_cpu_percent
        self._monitoring = False
        self._monitor_thread = None
        self.stats = {
            'peak_memory_mb': 0,
            'peak_cpu_percent': 0,
            'average_memory_mb': 0,
            'average_cpu_percent': 0,
            'sample_count': 0
        }


    def start_monitoring(self):
        """Start resource monitoring in background thread."""
        if not self._monitoring:
            self._monitoring = True
            self._monitor_thread = threading.Thread(target=self._monitor_loop)
            self._monitor_thread.daemon = True
            self._monitor_thread.start()


    def stop_monitoring(self):
        """Stop resource monitoring."""
        self._monitoring = False
        if self._monitor_thread:
            self._monitor_thread.join(timeout=1)


    def _monitor_loop(self):
        """Monitor system resources continuously."""
        total_memory = 0
        total_cpu = 0

        while self._monitoring:
            try:
                # Get memory usage
                memory_info = psutil.virtual_memory()
                memory_mb = memory_info.used / (1024 * 1024)

                # Get CPU usage
                cpu_percent = psutil.cpu_percent(interval=1)

                # Update statistics
                self.stats['sample_count'] += 1
                self.stats['peak_memory_mb'] = max(self.stats['peak_memory_mb'], memory_mb)
                self.stats['peak_cpu_percent'] = max(self.stats['peak_cpu_percent'], cpu_percent)

                total_memory += memory_mb
                total_cpu += cpu_percent

                self.stats['average_memory_mb'] = total_memory / self.stats['sample_count']
                self.stats['average_cpu_percent'] = total_cpu / self.stats['sample_count']

                # Trigger garbage collection if memory is high
                if memory_mb > self.max_memory_mb * 0.8:
                    gc.collect()

                time.sleep(5)  # Sample every 5 seconds

            except Exception as e:
                print(f"Error in resource monitoring: {e}")
                time.sleep(10)


    def is_resources_available(self) -> tuple[bool, str]:
        """Check if system has enough resources for processing."""
        try:
            memory_info = psutil.virtual_memory()
            memory_mb = memory_info.used / (1024 * 1024)
            cpu_percent = psutil.cpu_percent(interval=0.1)

            if memory_mb > self.max_memory_mb:
                return False, f"Memory usage ({memory_mb:.0f}MB) exceeds limit ({self.max_memory_mb}MB)"

            if cpu_percent > self.max_cpu_percent:
                return False, f"CPU usage ({cpu_percent:.1f}%) exceeds limit ({self.max_cpu_percent}%)"

            return True, "Resources available"

        except Exception as e:
            return False, f"Error checking resources: {e}"


    def get_stats(self) -> dict:
        """Get current resource statistics."""
        return self.stats.copy()
