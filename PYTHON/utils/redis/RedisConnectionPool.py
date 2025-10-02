import redis
import redis.connection
from config import Config
import threading
from contextlib import contextmanager


class RedisConnectionPool:
    """Enhanced Redis connection manager with connection pooling for bulk operations."""

    _instance = None
    _lock = threading.Lock()
    _connection_pool = None

    def __new__(cls):
        if cls._instance is None:
            with cls._lock:
                if cls._instance is None:
                    cls._instance = super(RedisConnectionPool, cls).__new__(cls)
        return cls._instance

    def __init__(self):
        if self._connection_pool is None:
            config = Config()
            self._connection_pool = redis.ConnectionPool(
                host=config.REDIS_HOST,
                port=config.REDIS_PORT,
                db=config.REDIS_DB,
                max_connections=config.REDIS_MAX_CONNECTIONS,
                decode_responses=False,
                socket_connect_timeout=30,
                socket_timeout=30,
                retry_on_timeout=True,
                health_check_interval=30
            )

            # Create main client
            self._client = redis.Redis(connection_pool=self._connection_pool)

    @property
    def client(self):
        """Get the main Redis client."""
        return self._client

    def get_client(self):
        """Get a Redis client from the connection pool."""
        return redis.Redis(connection_pool=self._connection_pool)

    @contextmanager
    def bulk_pipeline(self, transaction=True):
        """Context manager for bulk operations with pipeline."""
        client = self.get_client()
        pipeline = client.pipeline(transaction=transaction)
        try:
            yield pipeline
            pipeline.execute()
        except Exception as e:
            pipeline.reset()
            raise e
        finally:
            # Connection automatically returned to pool
            pass

    def ping(self) -> bool:
        """Test Redis connectivity."""
        try:
            return self._client.ping()
        except redis.ConnectionError:
            return False

    def get_connection_stats(self):
        """Get connection pool statistics."""
        pool = self._connection_pool
        return {
            'max_connections': pool.max_connections,
            'created_connections': len(pool._created_connections),
            'available_connections': len(pool._available_connections),
            'in_use_connections': len(pool._in_use_connections)
        }

    def close_all(self):
        """Close all connections in the pool."""
        if self._connection_pool:
            self._connection_pool.disconnect()

