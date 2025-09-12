import datetime
import json

from flask import Blueprint, request, jsonify
from utils.redis_client import RedisConnection
from models.cv_processor import CVProcessor
from models.search_engine import CVSearchEngine
from models.audit_logger import AuditLogger

api_bp = Blueprint('api', __name__)


def get_components():
    """Get all required components."""
    redis_conn = RedisConnection()
    cv_processor = CVProcessor(redis_conn.client)
    search_engine = CVSearchEngine(redis_conn.client, cv_processor)
    audit_logger = AuditLogger(redis_conn.client)
    return redis_conn, cv_processor, search_engine, audit_logger


@api_bp.route('/search')
def search():
    """Search CVs based on query parameters."""
    try:
        raw_query = request.args.get('q', '').strip()
        logic = request.args.get('logic', 'and').lower()
        page = int(request.args.get('page', 1))
        per_page = int(request.args.get('per_page', 10))

        queries = [q.strip().lower() for q in raw_query.split(',') if q.strip()] if raw_query else []

        _, _, search_engine, _ = get_components()
        result = search_engine.search_cvs(queries, logic, page, per_page)

        return jsonify(result)

    except ValueError as ve:
        return jsonify({'error': f'Invalid parameter values: {str(ve)}'}), 400
    except Exception as e:
        print(f"Error in search endpoint: {e}")
        return jsonify({'error': 'Internal server error during search'}), 500


@api_bp.route('/stats')
def api_stats():
    """Get CV database statistics."""
    try:
        _, _, search_engine, _ = get_components()
        stats = search_engine.get_statistics()
        return jsonify(stats), 200

    except Exception as e:
        print(f"Error fetching stats: {e}")
        return jsonify({'error': 'Internal server error fetching statistics'}), 500


@api_bp.route('/indexes')
def get_indexes():
    """Get all available search indexes."""
    try:
        _, _, search_engine, _ = get_components()
        indexes = search_engine.get_all_indexes()
        return jsonify(indexes), 200

    except Exception as e:
        print(f"Error fetching indexes: {e}")
        import traceback
        traceback.print_exc()
        return jsonify({'error': 'Internal server error fetching indexes'}), 500


@api_bp.route('/audit_logs', methods=['GET'])
def get_audit_logs():
    """Get paginated audit logs with optional filtering including date range."""
    try:
        page = int(request.args.get('page', 1))
        per_page = int(request.args.get('per_page', 20))
        user_filter = request.args.get('user')
        action_filter = request.args.get('action')
        start_date = request.args.get('start_date')  # Format: YYYY-MM-DD or ISO
        end_date = request.args.get('end_date')  # Format: YYYY-MM-DD or ISO

        _, _, _, audit_logger = get_components()
        result = audit_logger.get_logs(page, per_page, user_filter, action_filter, start_date, end_date)

        # Check if there was an error in date parsing
        if 'error' in result:
            return jsonify({'error': result['error']}), 400

        return jsonify(result), 200

    except ValueError as ve:
        return jsonify({'error': f'Invalid pagination parameters: {str(ve)}'}), 400
    except Exception as e:
        print(f"Unexpected error fetching audit logs: {e}")
        import traceback
        traceback.print_exc()
        return jsonify({'error': 'Internal server error fetching audit logs'}), 500


@api_bp.route('/audit_logs/date_range', methods=['GET'])
def get_audit_date_range():
    """Get the date range of available audit logs."""
    try:
        _, _, _, audit_logger = get_components()

        if hasattr(audit_logger, 'get_date_range_stats'):
            stats = audit_logger.get_date_range_stats()
        else:
            # Fallback if method doesn't exist
            stats = {
                'earliest_log': None,
                'latest_log': None,
                'total_logs': 0
            }

        return jsonify(stats), 200

    except Exception as e:
        print(f"Error fetching audit date range: {e}")
        import traceback
        traceback.print_exc()
        return jsonify({'error': 'Internal server error fetching date range'}), 500


@api_bp.route('/audit_logs/actions', methods=['GET'])
def get_available_actions():
    """Get list of all available actions for filtering."""
    try:
        _, _, _, audit_logger = get_components()

        # Get a sample of logs to extract unique actions
        raw_logs = audit_logger.redis_client.lrange('audit_log', 0, 1000)  # Sample first 1000
        actions = set()
        users = set()

        for log_str in raw_logs:
            try:
                log_data = json.loads(log_str)
                if log_data.get('action'):
                    actions.add(log_data['action'])
                if log_data.get('user'):
                    users.add(log_data['user'])
            except json.JSONDecodeError:
                continue

        return jsonify({
            'actions': sorted(list(actions)),
            'users': sorted(list(users))
        }), 200

    except Exception as e:
        print(f"Error fetching available actions: {e}")
        return jsonify({'error': 'Internal server error fetching actions'}), 500


@api_bp.route('/audit_logs/<log_id>', methods=['GET'])
def get_audit_log_by_id(log_id):
    """Get detailed audit log by ID."""
    try:
        if not log_id or not log_id.strip():
            return jsonify({'error': 'Log ID is required'}), 400

        _, _, _, audit_logger = get_components()

        # Use the optimized method if available, fallback to regular method
        if hasattr(audit_logger, 'get_log_by_id_optimized'):
            audit_log = audit_logger.get_log_by_id_optimized(log_id.strip())
        else:
            audit_log = audit_logger.get_log_by_id(log_id.strip())

        if not audit_log:
            return jsonify({
                'error': 'Audit log not found',
                'log_id': log_id
            }), 404

        # Add additional metadata for the response
        response_data = {
            'audit_log': audit_log,
            'metadata': {
                'retrieved_at': datetime.datetime.utcnow().isoformat() + 'Z',
                'log_id': log_id,
                'fields_present': list(audit_log.keys())
            }
        }

        return jsonify(response_data), 200

    except ValueError as ve:
        return jsonify({'error': f'Invalid log ID format: {str(ve)}'}), 400
    except Exception as e:
        print(f"Unexpected error fetching audit log {log_id}: {e}")
        import traceback
        traceback.print_exc()
        return jsonify({
            'error': 'Internal server error fetching audit log',
            'log_id': log_id
        }), 500

@api_bp.route('/user_activity/<username>')
def get_user_activity(username):
    """Get user activity summary."""
    try:
        days = int(request.args.get('days', 30))

        _, _, _, audit_logger = get_components()
        activity = audit_logger.get_user_activity(username, days)
        return jsonify(activity), 200

    except ValueError as ve:
        return jsonify({'error': f'Invalid days parameter: {str(ve)}'}), 400
    except Exception as e:
        print(f"Error fetching user activity for {username}: {e}")
        return jsonify({'error': 'Internal server error fetching user activity'}), 500

