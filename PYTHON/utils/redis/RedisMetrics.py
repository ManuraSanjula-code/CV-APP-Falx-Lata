import time

from utils.redis.RedisConnectionPool import RedisConnectionPool


class RedisMetrics:
    """Monitor Redis performance during bulk operations."""

    def __init__(self, connection_pool: RedisConnectionPool):
        self.pool = connection_pool
        self.start_time = None
        self.operation_count = 0

    def start_monitoring(self):
        """Start monitoring Redis operations."""
        self.start_time = time.time()
        self.operation_count = 0

    def record_operation(self, operation_type: str, count: int = 1):
        """Record an operation for metrics."""
        self.operation_count += count
