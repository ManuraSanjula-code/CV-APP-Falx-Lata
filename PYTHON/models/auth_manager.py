import jwt
import bcrypt
import datetime
from typing import Dict, Any
import redis
from config import Config


class AuthManager:
    """Handles user authentication and authorization."""

    def __init__(self, redis_client: redis.Redis):
        self.redis_client = redis_client
        self.config = Config()

    def register_user(self, username: str, password: str, name: str = "",
                      role: str = "user", priority: int = 5) -> Dict[str, Any]:
        """Register a new user."""
        if not username or not password:
            return {'success': False, 'message': 'Username and password are required'}

        if self.redis_client.sismember('users', username):
            return {'success': False, 'message': 'Username already exists'}

        try:
            hashed_pw_bytes = bcrypt.hashpw(password.encode('utf-8'), bcrypt.gensalt())

            user_key = f"user:{username}"
            pipe = self.redis_client.pipeline()
            pipe.hset(user_key, mapping={
                'password_hash': hashed_pw_bytes,
                'name': name,
                'role': role,
                'priority': str(priority),
                'created_at': datetime.datetime.utcnow().isoformat()
            })
            pipe.sadd('users', username)
            pipe.execute()

            return {'success': True, 'message': 'User created successfully'}

        except Exception as e:
            print(f"Error creating user {username}: {e}")
            return {'success': False, 'message': f'Error creating user: {str(e)}'}

    def authenticate_user(self, username: str, password: str) -> Dict[str, Any]:
        """Authenticate user and return JWT token."""
        if not username or not password:
            return {'success': False, 'message': 'Username and password are required'}

        user_key = f"user:{username}"
        stored_hash = self.redis_client.hget(user_key, 'password_hash')

        if not stored_hash:
            return {'success': False, 'message': 'Invalid credentials'}

        try:
            # Ensure stored_hash is bytes - handle both string and bytes from Redis
            if isinstance(stored_hash, str):
                stored_hash_bytes = stored_hash.encode('utf-8')
            else:
                stored_hash_bytes = stored_hash

            if bcrypt.checkpw(password.encode('utf-8'), stored_hash_bytes):
                token = jwt.encode({
                    'username': username,
                    'exp': datetime.datetime.utcnow() + datetime.timedelta(hours=self.config.JWT_EXPIRATION_HOURS)
                }, self.config.SECRET_KEY, algorithm="HS256")

                # Handle token encoding for different PyJWT versions
                token_str = token.decode('utf-8') if isinstance(token, bytes) else token
                return {'success': True, 'token': token_str}
            else:
                return {'success': False, 'message': 'Invalid credentials'}

        except ValueError as ve:
            print(f"BCRYPT Error during login for {username}: {ve}")
            return {'success': False, 'message': 'Internal server error during authentication'}
        except Exception as e:
            print(f"Error during login for {username}: {e}")
            return {'success': False, 'message': 'Internal server error'}

    def verify_token(self, token: str) -> Dict[str, Any]:
        """Verify JWT token and return user info."""
        try:
            data = jwt.decode(token, self.config.SECRET_KEY, algorithms=["HS256"])
            return {'success': True, 'username': data['username']}
        except jwt.ExpiredSignatureError:
            return {'success': False, 'message': 'Token has expired'}
        except jwt.InvalidTokenError:
            return {'success': False, 'message': 'Token is invalid'}

    def get_user_info(self, username: str) -> Dict[str, Any]:
        """Get user information."""
        try:
            user_key = f"user:{username}"
            user_data = self.redis_client.hgetall(user_key)

            if not user_data:
                return {'success': False, 'message': 'User not found'}

            # Decode bytes data
            decoded_data = {}
            for key, value in user_data.items():
                try:
                    decoded_key = key.decode('utf-8') if isinstance(key, bytes) else key
                    decoded_value = value.decode('utf-8') if isinstance(value, bytes) else value
                    decoded_data[decoded_key] = decoded_value
                except UnicodeDecodeError:
                    pass

            # Remove sensitive data
            decoded_data.pop('password_hash', None)

            return {'success': True, 'user': decoded_data}

        except Exception as e:
            print(f"Error getting user info for {username}: {e}")
            return {'success': False, 'message': 'Error retrieving user information'}