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
    """Search CVs based on query parameters with date filtering."""
    try:
        raw_query = request.args.get('q', '').strip()
        logic = request.args.get('logic', 'and').lower()
        page = int(request.args.get('page', 1))
        per_page = int(request.args.get('per_page', 10))

        # Date filtering parameters
        date_from = request.args.get('date_from', '').strip()
        date_to = request.args.get('date_to', '').strip()

        # Sorting parameters
        sort_by = request.args.get('sort_by', 'upload_date').strip()
        sort_order = request.args.get('sort_order', 'desc').lower()

        # Validate sort parameters
        valid_sort_fields = ['upload_date', 'name', 'filename']
        if sort_by not in valid_sort_fields:
            sort_by = 'upload_date'

        if sort_order not in ['asc', 'desc']:
            sort_order = 'desc'

        queries = [q.strip().lower() for q in raw_query.split(',') if q.strip()] if raw_query else []

        _, _, search_engine, _ = get_components()
        result = search_engine.search_cvs(
            queries=queries,
            logic=logic,
            page=page,
            per_page=per_page,
            date_from=date_from if date_from else None,
            date_to=date_to if date_to else None,
            sort_by=sort_by,
            sort_order=sort_order
        )

        return jsonify(result)

    except ValueError as ve:
        return jsonify({'error': f'Invalid parameter values: {str(ve)}'}), 400
    except Exception as e:
        print(f"Error in search endpoint: {e}")
        return jsonify({'error': 'Internal server error during search'}), 500


@api_bp.route('/stats')
def api_stats():
    """Get CV database statistics including date information."""
    try:
        _, _, search_engine, _ = get_components()
        stats = search_engine.get_statistics()
        return jsonify(stats), 200

    except Exception as e:
        print(f"Error fetching stats: {e}")
        return jsonify({'error': 'Internal server error fetching statistics'}), 500


@api_bp.route('/date_stats')
def get_date_stats():
    """Get comprehensive date-related statistics."""
    try:
        _, _, search_engine, _ = get_components()
        date_stats = search_engine.get_date_range_stats()
        return jsonify(date_stats), 200

    except Exception as e:
        print(f"Error fetching date stats: {e}")
        return jsonify({'error': 'Internal server error fetching date statistics'}), 500


@api_bp.route('/recent_uploads')
def get_recent_uploads():
    """Get recently uploaded CVs."""
    try:
        days = int(request.args.get('days', 7))  # Default to last 7 days
        page = int(request.args.get('page', 1))
        per_page = int(request.args.get('per_page', 20))

        # Validate days parameter
        if days < 1 or days > 365:
            days = 7

        _, _, search_engine, _ = get_components()
        result = search_engine.get_cvs_by_date_range(days_back=days)

        # Apply pagination if needed
        if page != 1 or per_page != 20:
            # Re-search with pagination
            end_date = datetime.datetime.now()
            start_date = end_date - datetime.timedelta(days=days)

            result = search_engine.search_cvs(
                queries=[],
                page=page,
                per_page=per_page,
                date_from=start_date.isoformat(),
                date_to=end_date.isoformat(),
                sort_by='upload_date',
                sort_order='desc'
            )

        return jsonify(result), 200

    except ValueError as ve:
        return jsonify({'error': f'Invalid parameter values: {str(ve)}'}), 400
    except Exception as e:
        print(f"Error fetching recent uploads: {e}")
        return jsonify({'error': 'Internal server error fetching recent uploads'}), 500


@api_bp.route('/filter_options')
def get_filter_options():
    """Get available filter options including date ranges."""
    try:
        _, _, search_engine, _ = get_components()

        # Get all indexes
        indexes = search_engine.get_all_indexes()

        # Get date statistics
        date_stats = search_engine.get_date_range_stats()

        # Define common date range presets
        now = datetime.datetime.now()
        date_presets = {
            'today': {
                'label': 'Today',
                'date_from': now.strftime('%Y-%m-%d'),
                'date_to': now.strftime('%Y-%m-%d')
            },
            'yesterday': {
                'label': 'Yesterday',
                'date_from': (now - datetime.timedelta(days=1)).strftime('%Y-%m-%d'),
                'date_to': (now - datetime.timedelta(days=1)).strftime('%Y-%m-%d')
            },
            'last_7_days': {
                'label': 'Last 7 days',
                'date_from': (now - datetime.timedelta(days=7)).strftime('%Y-%m-%d'),
                'date_to': now.strftime('%Y-%m-%d')
            },
            'last_30_days': {
                'label': 'Last 30 days',
                'date_from': (now - datetime.timedelta(days=30)).strftime('%Y-%m-%d'),
                'date_to': now.strftime('%Y-%m-%d')
            },
            'last_3_months': {
                'label': 'Last 3 months',
                'date_from': (now - datetime.timedelta(days=90)).strftime('%Y-%m-%d'),
                'date_to': now.strftime('%Y-%m-%d')
            },
            'last_6_months': {
                'label': 'Last 6 months',
                'date_from': (now - datetime.timedelta(days=180)).strftime('%Y-%m-%d'),
                'date_to': now.strftime('%Y-%m-%d')
            },
            'last_year': {
                'label': 'Last year',
                'date_from': (now - datetime.timedelta(days=365)).strftime('%Y-%m-%d'),
                'date_to': now.strftime('%Y-%m-%d')
            }
        }

        return jsonify({
            'search_indexes': indexes,
            'date_statistics': date_stats,
            'date_presets': date_presets,
            'sort_options': {
                'fields': [
                    {'value': 'upload_date', 'label': 'Upload Date'},
                    {'value': 'name', 'label': 'Name'},
                    {'value': 'filename', 'label': 'Filename'}
                ],
                'orders': [
                    {'value': 'desc', 'label': 'Descending'},
                    {'value': 'asc', 'label': 'Ascending'}
                ]
            }
        }), 200

    except Exception as e:
        print(f"Error fetching filter options: {e}")
        return jsonify({'error': 'Internal server error fetching filter options'}), 500


@api_bp.route('/advanced_search', methods=['POST'])
def advanced_search():
    """Advanced search with multiple filter combinations."""
    try:
        if not request.is_json:
            return jsonify({'error': 'Content-Type must be application/json'}), 400

        search_data = request.get_json()
        if not search_data:
            return jsonify({'error': 'No search data provided'}), 400

        # Extract search parameters
        queries = search_data.get('queries', [])
        logic = search_data.get('logic', 'and').lower()
        page = int(search_data.get('page', 1))
        per_page = int(search_data.get('per_page', 20))

        # Date filters
        date_from = search_data.get('date_from')
        date_to = search_data.get('date_to')

        # Sorting
        sort_by = search_data.get('sort_by', 'upload_date')
        sort_order = search_data.get('sort_order', 'desc')

        # Additional filters (for future enhancement)
        skill_filters = search_data.get('skills', [])
        experience_filters = search_data.get('experience_years', [])

        # Validate inputs
        if logic not in ['and', 'or']:
            logic = 'and'

        if sort_by not in ['upload_date', 'name', 'filename']:
            sort_by = 'upload_date'

        if sort_order not in ['asc', 'desc']:
            sort_order = 'desc'

        # Combine skill and experience filters with queries if provided
        combined_queries = list(queries)
        combined_queries.extend(skill_filters)
        combined_queries.extend([f"experience_years:{years}" for years in experience_filters])

        _, _, search_engine, _ = get_components()
        result = search_engine.search_cvs(
            queries=combined_queries,
            logic=logic,
            page=page,
            per_page=per_page,
            date_from=date_from,
            date_to=date_to,
            sort_by=sort_by,
            sort_order=sort_order
        )

        return jsonify(result), 200

    except ValueError as ve:
        return jsonify({'error': f'Invalid parameter values: {str(ve)}'}), 400
    except Exception as e:
        print(f"Error in advanced search: {e}")
        import traceback
        traceback.print_exc()
        return jsonify({'error': 'Internal server error during advanced search'}), 500


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


@api_bp.route('/export_search', methods=['POST'])
def export_search_results():
    """Export search results to CSV or JSON."""
    try:
        if not request.is_json:
            return jsonify({'error': 'Content-Type must be application/json'}), 400

        export_data = request.get_json()
        if not export_data:
            return jsonify({'error': 'No export data provided'}), 400

        # Extract search parameters (same as advanced_search)
        queries = export_data.get('queries', [])
        logic = export_data.get('logic', 'and').lower()
        date_from = export_data.get('date_from')
        date_to = export_data.get('date_to')
        sort_by = export_data.get('sort_by', 'upload_date')
        sort_order = export_data.get('sort_order', 'desc')
        export_format = export_data.get('format', 'json').lower()

        # Get all results (no pagination for export)
        _, _, search_engine, _ = get_components()
        result = search_engine.search_cvs(
            queries=queries,
            logic=logic,
            page=1,
            per_page=10000,  # Large number to get all results
            date_from=date_from,
            date_to=date_to,
            sort_by=sort_by,
            sort_order=sort_order
        )

        if export_format == 'csv':
            import csv
            import io

            output = io.StringIO()
            writer = csv.writer(output)

            # Write header
            writer.writerow(['ID', 'Name', 'Email', 'Phone', 'Filename', 'Upload Date'])

            # Write data
            for cv in result['results']:
                writer.writerow([
                    cv.get('id', ''),
                    cv.get('name', ''),
                    cv.get('email', ''),
                    cv.get('phone', ''),
                    cv.get('filename', ''),
                    cv.get('upload_date', '')
                ])

            csv_content = output.getvalue()
            output.close()

            return jsonify({
                'format': 'csv',
                'content': csv_content,
                'filename': f"cv_export_{datetime.datetime.now().strftime('%Y%m%d_%H%M%S')}.csv",
                'total_records': len(result['results'])
            }), 200

        else:  # JSON format
            return jsonify({
                'format': 'json',
                'content': result,
                'filename': f"cv_export_{datetime.datetime.now().strftime('%Y%m%d_%H%M%S')}.json",
                'total_records': len(result['results'])
            }), 200

    except Exception as e:
        print(f"Error in export search results: {e}")
        import traceback
        traceback.print_exc()
        return jsonify({'error': 'Internal server error during export'}), 500