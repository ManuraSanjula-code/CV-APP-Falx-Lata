from flask import Blueprint, request, jsonify
from models.auth_manager import AuthManager
from utils.redis_client import RedisConnection

auth_bp = Blueprint('auth', __name__)


def get_auth_manager():
    """Get AuthManager instance."""
    redis_conn = RedisConnection()
    return AuthManager(redis_conn.client)


@auth_bp.route('/register', methods=['POST'])
def register_user():
    """Register a new user."""
    try:
        data = request.get_json()
        if not data:
            return jsonify({'message': 'No JSON data provided'}), 400

        auth_manager = get_auth_manager()
        result = auth_manager.register_user(
            username=data.get('username'),
            password=data.get('password'),
            name=data.get('name', ''),
            role=data.get('role', 'user'),
            priority=data.get('priority', 5)
        )

        status_code = 201 if result['success'] else 400
        return jsonify({'message': result['message']}), status_code

    except Exception as e:
        print(f"Error in register endpoint: {e}")
        return jsonify({'message': 'Internal server error'}), 500


@auth_bp.route('/login', methods=['POST'])
def login():
    """Authenticate user and return JWT token."""
    try:
        data = request.get_json()
        if not data:
            return jsonify({'message': 'No JSON data provided'}), 400

        auth_manager = get_auth_manager()
        result = auth_manager.authenticate_user(
            username=data.get('username'),
            password=data.get('password')
        )

        if result['success']:
            return jsonify({'token': result['token']}), 200
        else:
            return jsonify({'message': result['message']}), 401

    except Exception as e:
        print(f"Error in login endpoint: {e}")
        return jsonify({'message': 'Internal server error'}), 500


@auth_bp.route('/profile', methods=['GET'])
def get_profile():
    """Get current user profile (requires authentication)."""
    from routes.cv_routes import token_required

    @token_required
    def _get_profile(current_user_username):
        try:
            auth_manager = get_auth_manager()
            result = auth_manager.get_user_info(current_user_username)

            if result['success']:
                return jsonify(result['user']), 200
            else:
                return jsonify({'message': result['message']}), 404

        except Exception as e:
            print(f"Error getting profile for {current_user_username}: {e}")
            return jsonify({'message': 'Internal server error'}), 500

    return _get_profile()