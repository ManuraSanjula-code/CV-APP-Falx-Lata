import os
import json
import hashlib
import datetime
from typing import Dict, List, Optional, Set, Any
import re

from dateutil.parser import parse
import itertools
from typing import ClassVar

class CVUtils:
    """Utility functions for CV processing."""
    _id_counter: ClassVar[itertools.count] = itertools.count(start=1)

    @staticmethod
    def allowed_file(filename: str, allowed_extensions: Set[str]) -> bool:
        """Check if file extension is allowed."""
        return ('.' in filename and
                filename.rsplit('.', 1)[1].lower() in allowed_extensions)

    @staticmethod
    def generate_cv_content_hash(cv_data: Dict[str, Any]) -> str:
        """Generate a hash based on key CV content fields."""
        key_fields = {
            'name': cv_data.get('personal_info', {}).get('name', ''),
            'email': cv_data.get('personal_info', {}).get('email', ''),
            'phone': cv_data.get('personal_info', {}).get('phone', ''),
            'skills_flat': sorted([
                skill.lower()
                for sublist in cv_data.get('skills', {}).values()
                for skill in sublist
            ]),
            'experience_titles': sorted([
                exp.get('position', '').lower()
                for exp in cv_data.get('experience', [])
                if exp.get('position')
            ]),
            'education_degrees': sorted([
                edu.get('degree', '').lower()
                for edu in cv_data.get('education', [])
                if edu.get('degree')
            ])
        }
        content_str = json.dumps(key_fields, sort_keys=True, separators=(',', ':'))
        return hashlib.sha256(content_str.encode('utf-8')).hexdigest()

    @staticmethod
    def estimate_years_experience(experience_list: List[Dict[str, Any]]) -> Optional[int]:
        """Estimate total years of experience from experience list."""
        if not experience_list:
            return None

        total_days = 0
        for exp in experience_list:
            duration = exp.get('duration', '')
            if not duration:
                continue

            try:
                if '-' in duration:
                    start, end = duration.split('-')
                    start_date = parse(start.strip())
                    end_date = (parse(end.strip()) if end.strip().lower() != 'present'
                                else datetime.datetime.now())
                    total_days += (end_date - start_date).days
                elif 'year' in duration.lower():
                    year_match = re.search(r'(\d+)', duration)
                    if year_match:
                        years = int(year_match.group(1))
                        total_days += years * 365
            except Exception as e:
                print(f"Error parsing duration '{duration}': {e}")
                continue

        years = round(total_days / 365) if total_days > 0 else 0
        return years if years > 0 else None

    @staticmethod
    def generate_cv_id(filename: str) -> str:
        """Generate unique CV ID."""
        timestamp = datetime.datetime.now().timestamp()
        hash_input = f"{filename}{timestamp}".encode()
        return f"falx-lata:{next(CVUtils._id_counter)}"

    @staticmethod
    def generate_id(filename: str = "") -> str:
        return f"falx-lata:{next(CVUtils._id_counter)}"

    @staticmethod
    def safe_decode_redis_data(data_bytes, default=""):
        """Safely decode Redis bytes data."""
        if data_bytes is None:
            return default
        try:
            return data_bytes.decode('utf-8')
        except UnicodeDecodeError:
            return default

    @staticmethod
    def safe_loads_json(data_bytes, default):
        """Safely load JSON from Redis bytes data."""
        if data_bytes is None:
            return default
        try:
            return json.loads(data_bytes.decode('utf-8'))
        except (json.JSONDecodeError, UnicodeDecodeError) as e:
            print(f"Error decoding/parsing data: {e}")
            return default