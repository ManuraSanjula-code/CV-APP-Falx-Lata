from typing import Dict, List, Set, Any
import redis
from models.cv_processor import CVProcessor
from config import Config


class CVSearchEngine:
    """Handles CV searching functionality."""

    def __init__(self, redis_client: redis.Redis, cv_processor: CVProcessor):
        self.redis_client = redis_client
        self.cv_processor = cv_processor
        self.config = Config()

    def search_cvs(self, queries: List[str], logic: str = 'and',
                   page: int = 1, per_page: int = None) -> Dict[str, Any]:
        """Search CVs based on queries."""
        if per_page is None:
            per_page = self.config.DEFAULT_PAGE_SIZE

        per_page = min(per_page, self.config.MAX_PAGE_SIZE)

        if not queries:
            return self._get_all_cvs(page, per_page)

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
            final_cv_ids = set.union(*query_cv_id_sets)
        elif query_cv_id_sets:
            final_cv_ids = set.intersection(*query_cv_id_sets)
        else:
            final_cv_ids = set()

        return self._paginate_results(final_cv_ids, page, per_page, queries, logic)

    def _get_all_cvs(self, page: int, per_page: int) -> Dict[str, Any]:
        """Get all CVs with pagination."""
        all_cvs = self.redis_client.keys('cv:*')
        all_cvs_decoded = []

        for key in all_cvs:
            try:
                all_cvs_decoded.append(key.decode('utf-8') if isinstance(key, bytes) else key)
            except (UnicodeDecodeError, AttributeError):
                pass

        # Sort by key descending (newest first)
        sorted_cv_ids = sorted(all_cvs_decoded, reverse=True)
        final_cv_ids = set(sorted_cv_ids)

        return self._paginate_results(final_cv_ids, page, per_page, [], 'and')

    def _paginate_results(self, cv_ids: Set[str], page: int, per_page: int,
                          queries: List[str], logic: str) -> Dict[str, Any]:
        """Paginate search results."""
        total = len(cv_ids)
        start = (page - 1) * per_page
        end = start + per_page

        cv_id_list = list(cv_ids)
        paginated_cv_ids = cv_id_list[start:end]

        results = []
        for cv_id in paginated_cv_ids:
            try:
                cv_data = self.redis_client.hgetall(cv_id)
                if cv_data:
                    formatted_result = self.cv_processor.format_cv_result(cv_id, cv_data)
                    results.append(formatted_result)
                else:
                    print(f"Warning: CV ID {cv_id} found in index but data missing")
                    total -= 1
            except Exception as e:
                print(f"Error processing CV {cv_id} during search: {e}")
                continue

        total_pages = max(1, (total + per_page - 1) // per_page)

        return {
            'results': results,
            'queries': queries,
            'logic': logic,
            'page': page,
            'per_page': per_page,
            'total': total,
            'total_pages': total_pages
        }

    def get_statistics(self) -> Dict[str, Any]:
        """Get CV database statistics."""
        try:
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

            return {
                'total_cvs': total_cvs,
                'total_indexes': total_indexes,
                'top_skills': top_skills
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
