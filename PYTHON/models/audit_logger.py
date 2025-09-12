import json
import datetime
from typing import Dict, List, Any, Optional
import redis
from config import Config


class AuditLogger:
    """Handles audit logging."""

    def __init__(self, redis_client: redis.Redis):
        self.redis_client = redis_client
        self.config = Config()

    def log_action(self, user: str, action: str, cv_id: str = None,
                   details: Dict = None, ip_address: str = None):
        """Log an audit event with unique ID."""
        import hashlib

        # Generate a unique ID for the audit entry
        timestamp_str = datetime.datetime.utcnow().isoformat() + 'Z'
        unique_string = f"{user}_{action}_{timestamp_str}_{cv_id or 'none'}_{ip_address or 'none'}"
        hash_object = hashlib.md5(unique_string.encode())
        audit_id = f"audit_{hash_object.hexdigest()[:12]}"

        audit_entry = {
            'id': audit_id,  # Add unique ID
            'timestamp': timestamp_str,
            'user': user,
            'action': action,
            'cv_id': cv_id,
            'details': details or {},
            'ip_address': ip_address,
            'session_info': {
                'user_agent': None,  # You can pass this from the request if available
                'session_id': None  # You can pass this if you have session management
            }
        }

        try:
            # Store in Redis list for chronological order
            self.redis_client.lpush('audit_log', json.dumps(audit_entry))

            # Also store as hash for direct ID access (recommended for performance)
            redis_hash_data = {}
            for key, value in audit_entry.items():
                if isinstance(value, (dict, list)):
                    redis_hash_data[key] = json.dumps(value)
                else:
                    redis_hash_data[key] = str(value)

            self.redis_client.hset(f"audit_entry:{audit_id}", mapping=redis_hash_data)

            # Set expiration for individual entries (optional - 1 year)
            self.redis_client.expire(f"audit_entry:{audit_id}", 86400 * 365)

            return audit_id  # Return the generated ID

        except Exception as e:
            print(f"Error logging audit entry: {e}")
            return None

    def get_user_activity(self, username: str, days: int = 30) -> Dict[str, Any]:
        """Get user activity summary for the last N days."""
        try:
            cutoff_date = datetime.datetime.utcnow() - datetime.timedelta(days=days)
            cutoff_str = cutoff_date.isoformat() + 'Z'

            # Get recent logs (this is a simplified approach)
            raw_logs = self.redis_client.lrange('audit_log', 0, 1000)

            activity_summary = {
                'username': username,
                'period_days': days,
                'total_actions': 0,
                'actions_by_type': {},
                'recent_actions': []
            }

            for log_str in raw_logs:
                try:
                    log_data = json.loads(log_str)

                    if log_data.get('user') != username:
                        continue

                    if log_data.get('timestamp', '') < cutoff_str:
                        continue

                    activity_summary['total_actions'] += 1
                    action = log_data.get('action', 'UNKNOWN')
                    activity_summary['actions_by_type'][action] = activity_summary['actions_by_type'].get(action, 0) + 1

                    if len(activity_summary['recent_actions']) < 10:
                        activity_summary['recent_actions'].append(log_data)

                except json.JSONDecodeError:
                    continue

            return activity_summary

        except Exception as e:
            print(f"Error getting user activity for {username}: {e}")
            raise


    def get_log_by_id_optimized(self, log_id: str) -> Optional[Dict[str, Any]]:
        """Get a specific audit log entry by ID (optimized version using hash storage)."""
        try:
            # Try to get from hash storage first (if using the enhanced log_action method)
            hash_key = f"audit_entry:{log_id}"
            audit_data_bytes = self.redis_client.hgetall(hash_key)

            if audit_data_bytes:
                # Process the hash data
                audit_data = {}
                for key_bytes, value_bytes in audit_data_bytes.items():
                    try:
                        key_str = key_bytes.decode('utf-8') if isinstance(key_bytes, bytes) else str(key_bytes)
                        value_str = value_bytes.decode('utf-8') if isinstance(value_bytes, bytes) else str(value_bytes)

                        # Try to parse as JSON, fallback to string
                        try:
                            audit_data[key_str] = json.loads(value_str)
                        except json.JSONDecodeError:
                            audit_data[key_str] = value_str

                    except UnicodeDecodeError as e:
                        print(f"Unicode decode error for audit log {log_id}, key {key_bytes}: {e}")
                        continue

                return audit_data if audit_data else None

            # Fallback to searching in the list (for backward compatibility)
            return self.get_log_by_id(log_id)

        except Exception as e:
            print(f"Error fetching audit log by ID {log_id}: {e}")
            raise


    def get_log_by_id(self, log_id: str) -> Optional[Dict[str, Any]]:
        """Get a specific audit log entry by ID."""
        try:
            # Get all logs to search for the specific ID
            # In production, you might want to use Redis hash storage with IDs as keys
            raw_logs = self.redis_client.lrange('audit_log', 0, -1)  # Get all logs

            for log_str in raw_logs:
                try:
                    log_data = json.loads(log_str)

                    # Check if this log has the requested ID
                    # If logs don't have IDs, you might need to generate them consistently
                    if log_data.get('id') == log_id:
                        return log_data

                    # Alternative: if using hash-based ID generation for search results
                    generated_id = f"audit_{hash(log_str) % 1000000}"
                    if generated_id == log_id:
                        log_data['id'] = generated_id
                        return log_data

                except json.JSONDecodeError:
                    continue

            return None  # Log with specified ID not found

        except Exception as e:
            print(f"Error fetching audit log by ID {log_id}: {e}")
            raise

    def get_logs(self, page: int = 1, per_page: int = 20,
                 user_filter: str = None, action_filter: str = None,
                 start_date: str = None, end_date: str = None) -> Dict[str, Any]:
        """Get paginated audit logs with optional filtering including date range."""
        if per_page > self.config.MAX_PAGE_SIZE:
            per_page = self.config.MAX_PAGE_SIZE

        try:
            # Parse date filters if provided
            start_datetime = None
            end_datetime = None

            if start_date:
                try:
                    # Support multiple date formats
                    for date_format in ['%Y-%m-%d', '%Y-%m-%dT%H:%M:%S', '%Y-%m-%dT%H:%M:%SZ']:
                        try:
                            start_datetime = datetime.datetime.strptime(start_date, date_format)
                            if not start_date.endswith('Z') and 'T' not in start_date:
                                # If only date provided, set to start of day
                                start_datetime = start_datetime.replace(hour=0, minute=0, second=0, microsecond=0)
                            break
                        except ValueError:
                            continue
                    if start_datetime is None:
                        raise ValueError(f"Invalid start_date format: {start_date}")
                except ValueError as e:
                    return {
                        'logs': [],
                        'page': page,
                        'per_page': per_page,
                        'total': 0,
                        'total_pages': 1,
                        'error': f"Invalid start_date format: {start_date}. Use YYYY-MM-DD or ISO format."
                    }

            if end_date:
                try:
                    # Support multiple date formats
                    for date_format in ['%Y-%m-%d', '%Y-%m-%dT%H:%M:%S', '%Y-%m-%dT%H:%M:%SZ']:
                        try:
                            end_datetime = datetime.datetime.strptime(end_date, date_format)
                            if not end_date.endswith('Z') and 'T' not in end_date:
                                # If only date provided, set to end of day
                                end_datetime = end_datetime.replace(hour=23, minute=59, second=59, microsecond=999999)
                            break
                        except ValueError:
                            continue
                    if end_datetime is None:
                        raise ValueError(f"Invalid end_date format: {end_date}")
                except ValueError as e:
                    return {
                        'logs': [],
                        'page': page,
                        'per_page': per_page,
                        'total': 0,
                        'total_pages': 1,
                        'error': f"Invalid end_date format: {end_date}. Use YYYY-MM-DD or ISO format."
                    }

            # Validate date range
            if start_datetime and end_datetime and start_datetime > end_datetime:
                return {
                    'logs': [],
                    'page': page,
                    'per_page': per_page,
                    'total': 0,
                    'total_pages': 1,
                    'error': "Start date cannot be after end date."
                }

            total_logs = self.redis_client.llen('audit_log')

            if total_logs == 0:
                return {
                    'logs': [],
                    'page': page,
                    'per_page': per_page,
                    'total': 0,
                    'total_pages': 1,
                    'filters': {
                        'user': user_filter,
                        'action': action_filter,
                        'start_date': start_date,
                        'end_date': end_date
                    }
                }

            # Get more logs for filtering (adjust based on your needs)
            fetch_size = min(total_logs, per_page * 20)  # Fetch more for filtering
            raw_logs = self.redis_client.lrange('audit_log', 0, fetch_size - 1)

            parsed_logs = []
            for log_str in raw_logs:
                try:
                    log_data = json.loads(log_str)

                    # Apply user filter
                    if user_filter and log_data.get('user') != user_filter:
                        continue

                    # Apply action filter
                    if action_filter and log_data.get('action') != action_filter:
                        continue

                    # Apply date filters
                    if start_datetime or end_datetime:
                        log_timestamp_str = log_data.get('timestamp')
                        if log_timestamp_str:
                            try:
                                # Parse log timestamp
                                log_timestamp = datetime.datetime.fromisoformat(
                                    log_timestamp_str.replace('Z', '+00:00'))
                                # Convert to UTC if needed
                                if log_timestamp.tzinfo is not None:
                                    log_timestamp = log_timestamp.replace(tzinfo=None)

                                # Check date range
                                if start_datetime and log_timestamp < start_datetime:
                                    continue
                                if end_datetime and log_timestamp > end_datetime:
                                    continue
                            except (ValueError, AttributeError):
                                # Skip logs with invalid timestamps
                                continue

                    parsed_logs.append(log_data)

                except json.JSONDecodeError as e:
                    print(f"Error decoding audit log entry: {e}")
                    # Include malformed entries for debugging
                    parsed_logs.append({
                        'error': 'Malformed log entry',
                        'raw': log_str,
                        'timestamp': datetime.datetime.utcnow().isoformat() + 'Z'
                    })

            # Sort by timestamp (newest first)
            parsed_logs.sort(key=lambda x: x.get('timestamp', ''), reverse=True)

            # Paginate filtered results
            filtered_total = len(parsed_logs)
            start = (page - 1) * per_page
            end = start + per_page
            paginated_logs = parsed_logs[start:end]

            total_pages = max(1, (filtered_total + per_page - 1) // per_page)

            return {
                'logs': paginated_logs,
                'page': page,
                'per_page': per_page,
                'total': filtered_total,
                'total_pages': total_pages,
                'filters': {
                    'user': user_filter,
                    'action': action_filter,
                    'start_date': start_date,
                    'end_date': end_date
                }
            }

        except Exception as e:
            print(f"Error fetching audit logs: {e}")
            raise

    def get_logs_optimized_with_date_filter(self, page: int = 1, per_page: int = 20,
                                            user_filter: str = None, action_filter: str = None,
                                            start_date: str = None, end_date: str = None) -> Dict[str, Any]:
        return self.get_logs(page, per_page, user_filter, action_filter, start_date, end_date)

    def get_date_range_stats(self) -> Dict[str, str]:
        """Get the date range of available audit logs."""
        try:
            # Get first and last log timestamps
            total_logs = self.redis_client.llen('audit_log')
            if total_logs == 0:
                return {
                    'earliest_log': None,
                    'latest_log': None,
                    'total_logs': 0
                }

            # Get first (newest) and last (oldest) logs
            newest_log_str = self.redis_client.lindex('audit_log', 0)
            oldest_log_str = self.redis_client.lindex('audit_log', -1)

            newest_timestamp = None
            oldest_timestamp = None

            if newest_log_str:
                try:
                    newest_log = json.loads(newest_log_str)
                    newest_timestamp = newest_log.get('timestamp')
                except json.JSONDecodeError:
                    pass

            if oldest_log_str:
                try:
                    oldest_log = json.loads(oldest_log_str)
                    oldest_timestamp = oldest_log.get('timestamp')
                except json.JSONDecodeError:
                    pass

            return {
                'earliest_log': oldest_timestamp,
                'latest_log': newest_timestamp,
                'total_logs': total_logs
            }

        except Exception as e:
            print(f"Error getting date range stats: {e}")
            return {
                'earliest_log': None,
                'latest_log': None,
                'total_logs': 0
            }