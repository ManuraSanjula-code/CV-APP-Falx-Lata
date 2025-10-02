from utils.redis.RedisConnectionPool import RedisConnectionPool


class RedisConnection:
    """Backward compatibility wrapper for existing code."""

    def __init__(self):
        self._pool_manager = RedisConnectionPool()

    @property
    def client(self):
        return self._pool_manager.client

    def ping(self) -> bool:
        return self._pool_manager.ping()

    def close(self):
        pass  # Connection pool handles this

