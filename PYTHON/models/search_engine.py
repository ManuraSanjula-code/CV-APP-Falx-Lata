from typing import Dict, List, Set, Any, Optional, Tuple
import redis
from models.cv_processor import CVProcessor
from config import Config
from datetime import datetime, timedelta


class CVSearchEngine:
    def __init__(self, redis_client: redis.Redis, cv_processor: CVProcessor):
        self.redis_client = redis_client
        self.cv_processor = cv_processor
        self.config = Config()

    def search_cvs(self, queries: List[str], logic: str = 'and',
                   page: int = 1, per_page: int = None,
                   date_from: Optional[str] = None, date_to: Optional[str] = None,
                   sort_by: str = 'upload_date', sort_order: str = 'desc') -> Dict[str, Any]:
        if per_page is None:
            per_page = self.config.DEFAULT_PAGE_SIZE

        per_page = min(per_page, self.config.MAX_PAGE_SIZE)

        # Get initial CV set based on search queries
        if not queries:
            cv_ids = self._get_all_cv_ids()
        else:
            cv_ids = self._search_by_queries(queries, logic)

        # Apply date filtering
        if date_from or date_to:
            cv_ids = self._filter_by_date(cv_ids, date_from, date_to)

        # Sort results
        sorted_cv_data = self._sort_cvs(cv_ids, sort_by, sort_order)

        return self._paginate_results(
            sorted_cv_data, page, per_page, queries, logic,
            date_from, date_to, sort_by, sort_order
        )

    def _get_all_cv_ids(self) -> Set[str]:
        """Get all CV IDs."""
        all_cvs = self.redis_client.keys('falx-lata:*')
        return {key.decode('utf-8') if isinstance(key, bytes) else key for key in all_cvs}

    def _search_by_queries(self, queries: List[str], logic: str) -> Set[str]:
        """Search CVs by queries and combine with specified logic."""
        query_cv_id_sets = []

        for query in queries:
            search_keys = self.redis_client.keys(f"index:*{query}*")
            cv_ids_for_query = set()

            for key in search_keys:
                try:
                    decoded_key = key.decode('utf-8') if isinstance(key, bytes) else key
                    members = self.redis_client.smembers(decoded_key)
                    for member in members:
                        try:
                            cv_ids_for_query.add(
                                member.decode('utf-8') if isinstance(member, bytes) else member
                            )
                        except (UnicodeDecodeError, AttributeError):
                            pass
                except (UnicodeDecodeError, AttributeError):
                    pass

            query_cv_id_sets.append(cv_ids_for_query)

        # Combine results based on logic
        if logic == 'or' and query_cv_id_sets:
            return set.union(*query_cv_id_sets)
        elif query_cv_id_sets:
            return set.intersection(*query_cv_id_sets)
        else:
            return set()

    def _filter_by_date(self, cv_ids: Set[str], date_from: Optional[str],
                        date_to: Optional[str]) -> Set[str]:
        """Filter CV IDs by upload date range."""
        if not (date_from or date_to):
            return cv_ids

        try:
            # Parse date filters
            from_date = self._parse_date(date_from) if date_from else None
            to_date = self._parse_date(date_to) if date_to else None

            # If to_date is provided without time, set it to end of day
            if to_date and not self._has_time_component(date_to):
                to_date = to_date.replace(hour=23, minute=59, second=59, microsecond=999999)

            filtered_ids = set()

            for cv_id in cv_ids:
                try:
                    upload_date_bytes = self.redis_client.hget(cv_id, 'upload_date')
                    if not upload_date_bytes:
                        continue

                    upload_date_str = upload_date_bytes.decode('utf-8')
                    upload_date = self._parse_date(upload_date_str)

                    if upload_date:
                        # Check if date falls within range
                        if from_date and upload_date < from_date:
                            continue
                        if to_date and upload_date > to_date:
                            continue
                        filtered_ids.add(cv_id)

                except Exception as e:
                    print(f"Error filtering CV {cv_id} by date: {e}")
                    continue

            return filtered_ids

        except Exception as e:
            print(f"Error in date filtering: {e}")
            return cv_ids

    def _parse_date(self, date_str: str) -> Optional[datetime]:
        """Parse date string in various formats."""
        if not date_str:
            return None

        date_formats = [
            '%Y-%m-%d',
            '%Y-%m-%dT%H:%M:%S',
            '%Y-%m-%dT%H:%M:%S.%f',
            '%Y-%m-%d %H:%M:%S',
            '%d/%m/%Y',
            '%d-%m-%Y',
            '%Y/%m/%d',
        ]

        for fmt in date_formats:
            try:
                return datetime.strptime(date_str, fmt)
            except ValueError:
                continue

        # Try parsing ISO format with timezone
        try:
            if date_str.endswith('Z'):
                date_str = date_str[:-1]
            return datetime.fromisoformat(date_str.replace('Z', '+00:00').replace('T', ' '))
        except ValueError:
            pass

        print(f"Could not parse date: {date_str}")
        return None

    def _has_time_component(self, date_str: str) -> bool:
        """Check if date string includes time component."""
        return 'T' in date_str or ':' in date_str

    def _sort_cvs(self, cv_ids: Set[str], sort_by: str, sort_order: str) -> List[Tuple[str, Dict]]:
        """Sort CVs by specified field."""
        cv_data_list = []

        for cv_id in cv_ids:
            try:
                cv_data = self.redis_client.hgetall(cv_id)
                if cv_data:
                    # Decode data for sorting
                    decoded_data = {}
                    for key_bytes, value_bytes in cv_data.items():
                        try:
                            key_str = key_bytes.decode('utf-8') if isinstance(key_bytes, bytes) else str(key_bytes)
                            value_str = value_bytes.decode('utf-8') if isinstance(value_bytes, bytes) else str(
                                value_bytes)
                            decoded_data[key_str] = value_str
                        except (UnicodeDecodeError, AttributeError):
                            continue

                    cv_data_list.append((cv_id, decoded_data))

            except Exception as e:
                print(f"Error loading CV data for sorting {cv_id}: {e}")
                continue

        # Sort the data
        def get_sort_key(item):
            cv_id, data = item

            if sort_by == 'upload_date':
                date_str = data.get('upload_date', '')
                parsed_date = self._parse_date(date_str)
                return parsed_date if parsed_date else datetime.min
            elif sort_by == 'name':
                personal_info = data.get('personal_info', '{}')
                try:
                    import json
                    personal_data = json.loads(personal_info)
                    return personal_data.get('name', '').lower()
                except:
                    return ''
            elif sort_by == 'filename':
                return data.get('filename', '').lower()
            else:
                return data.get(sort_by, '').lower()

        reverse_order = sort_order.lower() == 'desc'
        return sorted(cv_data_list, key=get_sort_key, reverse=reverse_order)

    def _paginate_results(self, sorted_cv_data: List[Tuple[str, Dict]], page: int, per_page: int,
                          queries: List[str], logic: str, date_from: Optional[str],
                          date_to: Optional[str], sort_by: str, sort_order: str) -> Dict[str, Any]:
        """Paginate sorted results."""
        total = len(sorted_cv_data)
        start = (page - 1) * per_page
        end = start + per_page

        paginated_data = sorted_cv_data[start:end]
        results = []

        for cv_id, cv_data in paginated_data:
            try:
                formatted_result = self.cv_processor.format_cv_result(cv_id, cv_data)
                results.append(formatted_result)
            except Exception as e:
                print(f"Error formatting CV result for {cv_id}: {e}")
                continue

        total_pages = max(1, (total + per_page - 1) // per_page)

        return {
            'results': results,
            'queries': queries,
            'logic': logic,
            'page': page,
            'per_page': per_page,
            'total': total,
            'total_pages': total_pages,
            'date_from': date_from,
            'date_to': date_to,
            'sort_by': sort_by,
            'sort_order': sort_order,
            'filters_applied': {
                'date_filter': bool(date_from or date_to),
                'search_filter': bool(queries)
            }
        }

    def get_date_range_stats(self) -> Dict[str, Any]:
        """Get date range statistics for all CVs."""
        try:
            all_cvs = self.redis_client.keys('cv:*')
            dates = []

            for cv_key in all_cvs:
                try:
                    upload_date_bytes = self.redis_client.hget(cv_key, 'upload_date')
                    if upload_date_bytes:
                        upload_date_str = upload_date_bytes.decode('utf-8')
                        parsed_date = self._parse_date(upload_date_str)
                        if parsed_date:
                            dates.append(parsed_date)
                except Exception as e:
                    print(f"Error processing date for {cv_key}: {e}")
                    continue

            if not dates:
                return {
                    'earliest_date': None,
                    'latest_date': None,
                    'total_cvs': 0,
                    'date_distribution': {}
                }

            earliest = min(dates)
            latest = max(dates)

            # Create date distribution by month
            date_distribution = {}
            for date in dates:
                month_key = date.strftime('%Y-%m')
                date_distribution[month_key] = date_distribution.get(month_key, 0) + 1

            return {
                'earliest_date': earliest.isoformat(),
                'latest_date': latest.isoformat(),
                'total_cvs': len(dates),
                'date_distribution': date_distribution
            }

        except Exception as e:
            print(f"Error getting date range stats: {e}")
            return {
                'earliest_date': None,
                'latest_date': None,
                'total_cvs': 0,
                'date_distribution': {}
            }

    def get_statistics(self) -> Dict[str, Any]:
        """Get CV database statistics including date information."""
        try:
            # Get existing statistics
            total_cvs = len(self.redis_client.keys(b'cv:*'))
            total_indexes = len(self.redis_client.keys(b'index:*'))

            # Get top skills
            all_skills = {}
            skill_keys = self.redis_client.keys(b'index:skill:*')

            for key_bytes in skill_keys:
                try:
                    skill_name = key_bytes.decode().split(':')[-1]
                    count = self.redis_client.scard(key_bytes)
                    all_skills[skill_name] = count
                except Exception as e:
                    print(f"Error processing skill index key {key_bytes}: {e}")
                    pass

            top_skills = sorted(all_skills.items(), key=lambda x: x[1], reverse=True)[:10]

            # Add date range stats
            date_stats = self.get_date_range_stats()

            return {
                'total_cvs': total_cvs,
                'total_indexes': total_indexes,
                'top_skills': top_skills,
                'date_range': {
                    'earliest': date_stats.get('earliest_date'),
                    'latest': date_stats.get('latest_date'),
                    'distribution': date_stats.get('date_distribution', {})
                }
            }

        except Exception as e:
            print(f"Error fetching statistics: {e}")
            raise

    def get_all_indexes(self) -> Dict[str, List[str]]:
        """Get all available search indexes categorized."""
        try:
            categorized_indexes = {
                'skills': [],
                'institutions': [],
                'positions': [],
                'skill_types': [],
                'education_keywords': [],
                'experience_years': [],
                'filenames': []
            }

            all_index_keys = self.redis_client.keys(b'index:*')

            for key_bytes in all_index_keys:
                try:
                    key_str = key_bytes.decode('utf-8')
                    parts = key_str.split(':', 2)

                    if len(parts) >= 3 and parts[0] == 'index':
                        category = parts[1]
                        value = parts[2]

                        if category == 'skill':
                            categorized_indexes['skills'].append(value)
                        elif category == 'institution':
                            categorized_indexes['institutions'].append(value)
                        elif category == 'position':
                            categorized_indexes['positions'].append(value)
                        elif category == 'skill_type':
                            categorized_indexes['skill_types'].append(value)
                        elif category == 'education':
                            categorized_indexes['education_keywords'].append(value)
                        elif category == 'experience_years':
                            categorized_indexes['experience_years'].append(value)
                        elif category == 'filename':
                            categorized_indexes['filenames'].append(value)

                except (UnicodeDecodeError, IndexError, Exception) as e:
                    print(f"Error processing index key {key_bytes}: {e}")
                    pass

            # Sort all categories
            for category in categorized_indexes:
                categorized_indexes[category].sort()

            return categorized_indexes

        except Exception as e:
            print(f"Error fetching indexes: {e}")
            raise

    def get_cvs_by_date_range(self, days_back: int = 30) -> Dict[str, Any]:
        """Get CVs uploaded in the last N days."""
        try:
            end_date = datetime.now()
            start_date = end_date - timedelta(days=days_back)

            return self.search_cvs(
                queries=[],
                date_from=start_date.isoformat(),
                date_to=end_date.isoformat(),
                sort_by='upload_date',
                sort_order='desc'
            )

        except Exception as e:
            print(f"Error getting CVs by date range: {e}")
            raise