def format_cv_result(self, cv_id_bytes, cv_data_bytes_dict: Dict) -> Dict[str, Any]:
    """Format CV data for API response."""
    try:
        cv_id = cv_id_bytes.decode() if isinstance(cv_id_bytes, bytes) else cv_id_bytes

        # Helper function to safely get and decode data
        def safe_get_field(field_name, default=""):
            field_bytes = cv_data_bytes_dict.get(field_name.encode() if isinstance(field_name, str) else field_name)
            if field_bytes is None:
                return default

            try:
                if isinstance(field_bytes, bytes):
                    decoded = field_bytes.decode('utf-8')
                else:
                    decoded = str(field_bytes)

                # Try to parse as JSON if it looks like JSON
                if decoded.startswith('{') or decoded.startswith('['):
                    try:
                        return json.loads(decoded)
                    except json.JSONDecodeError:
                        return decoded
                return decoded
            except UnicodeDecodeError:
                return default

        # Get personal info
        personal_info_raw = safe_get_field('personal_info', {})
        if isinstance(personal_info_raw, str):
            try:
                personal_info = json.loads(personal_info_raw)
            except json.JSONDecodeError:
                personal_info = {}
        else:
            personal_info = personal_info_raw if isinstance(personal_info_raw, dict) else {}

        return {
            'id': cv_id,
            'name': personal_info.get('name', ''),
            'email': personal_info.get('email', ''),
            'phone': personal_info.get('phone', ''),
            'filename': safe_get_field('filename'),
            'upload_date': safe_get_field('upload_date')
        }

    except Exception as e:
        print(f"Error formatting CV result for {cv_id_bytes}: {e}")
        import traceback
        traceback.print_exc()
        return {
            'id': cv_id_bytes.decode() if isinstance(cv_id_bytes, bytes) else str(cv_id_bytes),
            'name': 'Error',
            'email': '',
            'phone': '',
            'filename': 'Error',
            'upload_date': ''
        }