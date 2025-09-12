import redis
from config import Config

class RedisConnection:
    _instance = None
    _client = None

    def __new__(cls):
        if cls._instance is None:
            cls._instance = super(RedisConnection, cls).__new__(cls)
        return cls._instance

    def __init__(self):
        if self._client is None:
            config = Config()
            self._client = redis.Redis(
                host=config.REDIS_HOST,
                port=config.REDIS_PORT,
                db=config.REDIS_DB,
                decode_responses=False
            )

    @property
    def client(self):
        return self._client

    def ping(self) -> bool:
        try:
            return self._client.ping()
        except redis.ConnectionError:
            return False

    def close(self):
        if self._client:
            self._client.close()
