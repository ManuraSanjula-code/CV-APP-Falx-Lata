import json
from typing import Dict, List, Optional, Set, Any, Tuple
import redis
from utils.cv_utils import CVUtils


class CVProcessor:
    """Handles CV data processing and indexing."""

    def __init__(self, redis_client: redis.Redis):
        self.redis_client = redis_client

    def index_cv_data(self, cv_id: str, cv_data: Dict[str, Any], pipe=None) -> None:
        """Index CV data for searching."""
        redis_pipe = pipe or self.redis_client

        try:
            # Index by name
            name = cv_data.get('personal_info', {}).get('name', '').lower()
            if name:
                redis_pipe.sadd(f"index:name:{name}", cv_id)

            gender = cv_data.get('gender')
            if gender:
                redis_pipe.sadd(f"index:gender:{gender.lower()}", cv_id)

            emp_type = cv_data.get('type')
            if emp_type:
                redis_pipe.sadd(f"index:type:{emp_type.lower().replace(' ', '_')}", cv_id)

            # Index by email
            email = cv_data.get('personal_info', {}).get('email', '').lower()
            if email:
                redis_pipe.sadd(f"index:email:{email}", cv_id)

            # Index by skills
            for skill_type, skills in cv_data.get('skills', {}).items():
                for skill in skills:
                    skill_normalized = skill.lower().strip()
                    redis_pipe.sadd(f"index:skill:{skill_normalized}", cv_id)
                    redis_pipe.sadd(f"index:skill_type:{skill_type.lower()}", cv_id)

            # Index by education
            for edu in cv_data.get('education', []):
                if 'degree' in edu:
                    for word in edu['degree'].lower().split():
                        redis_pipe.sadd(f"index:education:{word}", cv_id)
                if 'institution' in edu:
                    for word in edu['institution'].lower().split():
                        redis_pipe.sadd(f"index:institution:{word}", cv_id)

            # Index by experience years
            years_exp = CVUtils.estimate_years_experience(cv_data.get('experience', []))
            if years_exp is not None:
                redis_pipe.sadd(f"index:experience_years:{years_exp}", cv_id)

            # Index by job positions
            for exp in cv_data.get('experience', []):
                position = exp.get('position', '')
                if position:
                    for word in position.lower().split():
                        redis_pipe.sadd(f"index:position:{word}", cv_id)

            # Index by filename
            filename = cv_data.get('filename', '')
            if filename:
                for word in filename.lower().split('.'):
                    if word:
                        redis_pipe.sadd(f"index:filename:{word}", cv_id)

            if not pipe:
                redis_pipe.execute()

        except Exception as e:
            print(f"Error indexing CV {cv_id}: {e}")
            if not pipe:
                raise

    def format_cv_result(self, cv_id: str, cv_data: Dict) -> Dict[str, Any]:
        """Enhanced CV result formatter with proper field extraction"""
        def safe_get(data, key, default=''):
            value = data.get(key, default)
            if isinstance(value, bytes):
                return value.decode('utf-8', errors='ignore')
            return str(value) if value is not None else default

        # Handle personal_info whether it's str or dict
        personal_info = cv_data.get('personal_info', {})
        if isinstance(personal_info, (bytes, str)):
            try:
                personal_info = json.loads(personal_info.decode() if isinstance(personal_info, bytes) else personal_info)
            except (json.JSONDecodeError, AttributeError):
                personal_info = {}

        return {
            'id': cv_id,
            'name': safe_get(personal_info, 'name'),
            'email': safe_get(personal_info, 'email'),
            'phone': safe_get(personal_info, 'phone'),
            'filename': safe_get(cv_data, 'filename'),
            'upload_date': safe_get(cv_data, 'upload_date'),
            'gender': safe_get(cv_data, 'gender'),
            'type': safe_get(cv_data, 'type')  # Add this
        }

    def check_duplicate_cv(self, cv_data: Dict[str, Any]) -> Tuple[str, Optional[str]]:
        """Check if CV is duplicate or update. Returns (action, existing_id)."""
        user_email = cv_data.get('personal_info', {}).get('email', '').lower().strip()

        if not user_email:
            return "new", None

        # Find existing CVs with same email
        email_index_key = f"index:email:{user_email}"
        potential_existing_ids = self.redis_client.smembers(email_index_key)
        potential_existing_ids = [
            eid.decode() for eid in potential_existing_ids
            if isinstance(eid, bytes)
        ]

        if not potential_existing_ids:
            return "new", None

        # Generate content hash for comparison
        new_cv_content_hash = CVUtils.generate_cv_content_hash(cv_data)

        # Compare with existing CVs
        for eid in potential_existing_ids:
            existing_hash_bytes = self.redis_client.hget(eid, 'content_hash')
            if existing_hash_bytes:
                try:
                    existing_hash = existing_hash_bytes.decode('utf-8')
                    if new_cv_content_hash == existing_hash:
                        return "duplicate", eid
                    else:
                        return "update", eid
                except (UnicodeDecodeError, Exception) as e:
                    print(f"Error comparing content hash for {eid}: {e}")
                    continue

        return "new", None

    def remove_cv_from_indexes(self, cv_id: str) -> None:
        """Remove CV ID from all indexes."""
        try:
            all_index_keys = self.redis_client.keys("index:*")
            pipeline = self.redis_client.pipeline()

            for key in all_index_keys:
                try:
                    decoded_key = key.decode('utf-8') if isinstance(key, bytes) else key
                    pipeline.srem(decoded_key, cv_id)
                except (UnicodeDecodeError, Exception) as e:
                    print(f"Warning: Could not process index key {key} for removal: {e}")
                    continue

            pipeline.execute()
        except Exception as e:
            print(f"Error removing CV {cv_id} from indexes: {e}")
            raise
