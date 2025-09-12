import os
import json
import shutil
import getpass
import datetime
from functools import wraps
from flask import Blueprint, request, jsonify, send_file
from werkzeug.utils import secure_filename

from config import Config
from utils.redis_client import RedisConnection
from utils.cv_utils import CVUtils
from models.cv_processor import CVProcessor
from models.auth_manager import AuthManager
from models.audit_logger import AuditLogger
from utils.pdf_parser import extract_cv_info

cv_bp = Blueprint('cv', __name__)


def get_components():
    """Get all required components."""
    redis_conn = RedisConnection()
    cv_processor = CVProcessor(redis_conn.client)
    auth_manager = AuthManager(redis_conn.client)
    audit_logger = AuditLogger(redis_conn.client)
    return redis_conn, cv_processor, auth_manager, audit_logger


def token_required(f):
    """Decorator to require JWT token authentication."""

    @wraps(f)
    def decorated(*args, **kwargs):
        token = None

        if 'Authorization' in request.headers:
            auth_header = request.headers['Authorization']
            try:
                token = auth_header.split(" ")[1]
            except IndexError:
                return jsonify({'message': 'Token format invalid'}), 401

        if not token:
            return jsonify({'message': 'Token is missing'}), 401

        _, _, auth_manager, _ = get_components()
        result = auth_manager.verify_token(token)

        if not result['success']:
            return jsonify({'message': result['message']}), 401

        return f(result['username'], *args, **kwargs)

    return decorated


@cv_bp.route('/upload', methods=['POST'])
@token_required
def upload_files(current_user_username):
    """Upload and process CV files."""
    config = Config()

    if 'files[]' not in request.files:
        return jsonify({'error': 'No files uploaded'}), 400

    files = request.files.getlist('files[]')
    if len(files) > config.MAX_FILES_PER_UPLOAD:
        return jsonify({
            'error': f'Too many files. Maximum {config.MAX_FILES_PER_UPLOAD} allowed',
            'max_files': config.MAX_FILES_PER_UPLOAD
        }), 400

    redis_conn, cv_processor, _, audit_logger = get_components()
    results = []
    errors = []

    # Get current user for file storage
    try:
        current_user = getpass.getuser()
    except Exception as e:
        print(f"Could not determine user, using 'unknown': {e}")
        current_user = "unknown"

    user_upload_dir = os.path.join(config.USER_PDF_STORAGE_BASE, current_user)
    os.makedirs(user_upload_dir, exist_ok=True)

    for file in files:
        try:
            if not file or file.filename == '':
                errors.append({'filename': 'empty', 'error': 'No file selected'})
                continue

            if not CVUtils.allowed_file(file.filename, config.ALLOWED_EXTENSIONS):
                errors.append({
                    'filename': file.filename,
                    'error': 'Invalid file type. Only PDFs and DOCX allowed'
                })
                continue

            filename = secure_filename(file.filename)
            temp_filepath = os.path.join(config.UPLOAD_FOLDER, filename)
            file.save(temp_filepath)

            # Copy to user directory
            user_pdf_filepath = os.path.join(user_upload_dir, filename)
            shutil.copy2(temp_filepath, user_pdf_filepath)

            # Extract CV data
            cv_data = extract_cv_info(temp_filepath)

            # Check for duplicates
            action, existing_cv_id = cv_processor.check_duplicate_cv(cv_data)

            if action == "duplicate":
                errors.append({
                    'filename': filename,
                    'error': f'Duplicate CV detected. Existing entry ID: {existing_cv_id}. File skipped.'
                })
                if os.path.exists(temp_filepath):
                    os.remove(temp_filepath)
                continue

            if not cv_data or not cv_data.get('personal_info', {}).get('name'):
                errors.append({
                    'filename': filename,
                    'error': 'No valid CV data extracted - possibly not a CV'
                })
                if os.path.exists(temp_filepath):
                    os.remove(temp_filepath)
                continue

            # Prepare CV data for storage
            cv_data.update({
                'filename': filename,
                'upload_date': datetime.datetime.now().isoformat(),
                'saved_pdf_path': user_pdf_filepath,
                'content_hash': CVUtils.generate_cv_content_hash(cv_data),
                'gender': None,  # Add this
                'type': None  # Add this
            })

            cv_id = CVUtils.generate_cv_id(filename)

            # Store in Redis
            redis_data = {}
            for key, value in cv_data.items():
                if isinstance(value, (dict, list)):
                    redis_data[key] = json.dumps(value)
                else:
                    redis_data[key] = str(value)

            with redis_conn.client.pipeline() as pipe:
                pipe.hset(cv_id, mapping=redis_data)
                cv_processor.index_cv_data(cv_id, cv_data, pipe=pipe)
                pipe.execute()

            results.append({
                'id': cv_id,
                'filename': filename,
                'name': cv_data.get('personal_info', {}).get('name', ''),
                'action': action,
                'existing_id': existing_cv_id if action == "update" else None
            })

            # Clean up temp file
            if os.path.exists(temp_filepath):
                os.remove(temp_filepath)

            # Log action
            audit_logger.log_action(
                user=current_user_username,
                action='UPLOAD',
                cv_id=cv_id,
                ip_address=request.remote_addr
            )

        except Exception as e:
            errors.append({
                'filename': file.filename,
                'error': f"Upload/Processing Error: {str(e)}"
            })
            # Clean up on error
            try:
                if hasattr(file, 'filename') and file.filename:
                    temp_path = os.path.join(config.UPLOAD_FOLDER, secure_filename(file.filename))
                    if os.path.exists(temp_path):
                        os.remove(temp_path)
            except:
                pass
            continue

    return jsonify({
        'success': True,
        'processed': results,
        'total_files': len(files),
        'success_count': len(results),
        'error_count': len(errors),
        'errors': errors[:10]
    })


@cv_bp.route('/api/view/<cv_id>', methods=['GET'])
def api_view_cv(cv_id):
    """Get detailed CV information."""
    try:
        redis_conn, cv_processor, _, _ = get_components()
        cv_data_bytes = redis_conn.client.hgetall(cv_id)

        if not cv_data_bytes:
            return jsonify({'error': 'CV not found'}), 404

        processed_data = {}
        for key_bytes, value_bytes in cv_data_bytes.items():
            try:
                # Safely decode the key
                if isinstance(key_bytes, bytes):
                    key_str = key_bytes.decode('utf-8')
                else:
                    key_str = str(key_bytes)

                # Safely decode the value
                if isinstance(value_bytes, bytes):
                    value_str = value_bytes.decode('utf-8')
                else:
                    value_str = str(value_bytes)

                # Try to parse as JSON, fallback to string
                try:
                    processed_data[key_str] = json.loads(value_str)
                except json.JSONDecodeError:
                    processed_data[key_str] = value_str

            except UnicodeDecodeError as e:
                # Handle encoding issues gracefully
                key_str = str(key_bytes) if not isinstance(key_bytes, str) else key_bytes
                processed_data[key_str] = f"[Encoding Error: {str(e)}]"
                print(f"Unicode decode error for key {key_bytes}: {e}")

            except Exception as e:
                # Handle any other errors
                key_str = str(key_bytes) if not isinstance(key_bytes, str) else key_bytes
                processed_data[key_str] = f"[Processing Error: {str(e)}]"
                print(f"Error processing key {key_bytes} for CV {cv_id}: {e}")

        return jsonify(processed_data), 200

    except Exception as e:
        print(f"Unexpected error fetching CV {cv_id}: {e}")
        import traceback
        traceback.print_exc()
        return jsonify({'error': 'Internal server error fetching CV details'}), 500



@cv_bp.route('/api/view/<cv_id>', methods=['PUT'])
@token_required
def api_update_cv(current_user_username, cv_id):
    """Update CV information."""
    try:
        if not request.is_json:
            return jsonify({'error': 'Content-Type must be application/json'}), 400

        updated_data = request.get_json()
        if not updated_data:
            return jsonify({'error': 'No JSON data provided in request body'}), 400

        redis_conn, cv_processor, _, audit_logger = get_components()

        if not redis_conn.client.exists(cv_id):
            return jsonify({'error': 'CV not found'}), 404

        # Fetch existing data for merging
        existing_data_bytes = redis_conn.client.hgetall(cv_id)
        existing_data = {}

        for k_bytes, v_bytes in existing_data_bytes.items():
            try:
                # Safely decode the key
                if isinstance(k_bytes, bytes):
                    k_str = k_bytes.decode('utf-8')
                else:
                    k_str = str(k_bytes)

                # Safely decode the value
                if isinstance(v_bytes, bytes):
                    v_str = v_bytes.decode('utf-8')
                else:
                    v_str = str(v_bytes)

                # Try to parse as JSON, fallback to string
                try:
                    existing_data[k_str] = json.loads(v_str)
                except json.JSONDecodeError:
                    existing_data[k_str] = v_str

            except UnicodeDecodeError as e:
                print(f"Unicode decode error in update for key {k_bytes}: {e}")
                continue
            except Exception as e:
                print(f"Error processing existing data for key {k_bytes}: {e}")
                continue

        # Merge existing with updated data
        merged_data = {**existing_data, **updated_data}

        # Recalculate content hash if relevant fields changed
        if any(key in updated_data for key in ['personal_info', 'skills', 'experience', 'education']):
            updated_data['content_hash'] = CVUtils.generate_cv_content_hash(merged_data)
            print(f"Updated content hash for {cv_id}")

        # Prepare data for Redis storage
        redis_update_data = {}
        for key, value in updated_data.items():
            if isinstance(value, (dict, list)):
                redis_update_data[str(key)] = json.dumps(value)
            else:
                redis_update_data[str(key)] = str(value)

        redis_conn.client.hset(cv_id, mapping=redis_update_data)

        # Re-index the updated CV
        try:
            cv_processor.remove_cv_from_indexes(cv_id)
            cv_processor.index_cv_data(cv_id, merged_data)
        except Exception as e:
            print(f"Error re-indexing CV {cv_id} after update: {e}")

        # Log action
        audit_logger.log_action(
            user=current_user_username,
            action='UPDATE',
            cv_id=cv_id,
            ip_address=request.remote_addr
        )

        return jsonify({'message': 'CV data updated successfully'}), 200

    except Exception as e:
        print(f"Unexpected error updating CV {cv_id}: {e}")
        import traceback
        traceback.print_exc()
        return jsonify({'error': 'Internal server error updating CV'}), 500


@cv_bp.route('/api/cv/<cv_id>', methods=['DELETE'])
@token_required
def api_delete_cv(current_user_username, cv_id):
    """Delete a CV."""
    try:
        redis_conn, cv_processor, _, audit_logger = get_components()

        if not redis_conn.client.exists(cv_id):
            return jsonify({'error': 'CV not found'}), 404

        # Get CV data for audit log
        cv_data_bytes = redis_conn.client.hgetall(cv_id)
        cv_data = {}

        if cv_data_bytes:
            for key_bytes, value_bytes in cv_data_bytes.items():
                try:
                    # Safely decode the key
                    if isinstance(key_bytes, bytes):
                        key_str = key_bytes.decode('utf-8')
                    else:
                        key_str = str(key_bytes)

                    # Safely decode the value
                    if isinstance(value_bytes, bytes):
                        value_str = value_bytes.decode('utf-8')
                    else:
                        value_str = str(value_bytes)

                    # Try to parse as JSON, fallback to string
                    try:
                        cv_data[key_str] = json.loads(value_str)
                    except json.JSONDecodeError:
                        cv_data[key_str] = value_str

                except UnicodeDecodeError as e:
                    print(f"Unicode decode error in delete for key {key_bytes}: {e}")
                    continue
                except Exception as e:
                    print(f"Error processing CV data for deletion, key {key_bytes}: {e}")
                    continue

        # Delete CV data and remove from indexes
        pipe = redis_conn.client.pipeline()
        pipe.delete(cv_id)
        pipe.execute()

        # Remove from all indexes
        cv_processor.remove_cv_from_indexes(cv_id)

        # Log action
        audit_logger.log_action(
            user=current_user_username,
            action='DELETE',
            cv_id=cv_id,
            details=cv_data,
            ip_address=request.remote_addr
        )

        return jsonify({'message': f'CV with ID {cv_id} deleted successfully'}), 200

    except Exception as e:
        print(f"Unexpected error deleting CV {cv_id}: {e}")
        import traceback
        traceback.print_exc()
        return jsonify({'error': 'Internal server error deleting CV'}), 500

@cv_bp.route('/api/download_pdf/<cv_id>')
def download_pdf(cv_id):
    """Download CV PDF file."""
    try:
        redis_conn, _, _, _ = get_components()
        saved_pdf_path_bytes = redis_conn.client.hget(cv_id, 'saved_pdf_path')

        if not saved_pdf_path_bytes:
            return jsonify({'error': 'PDF path not found for this CV ID'}), 404

        saved_pdf_path = saved_pdf_path_bytes.decode('utf-8')
        if not os.path.exists(saved_pdf_path):
            return jsonify({'error': 'PDF file not found on server'}), 404

        return send_file(saved_pdf_path, as_attachment=False)

    except Exception as e:
        print(f"Error serving PDF for {cv_id}: {e}")
        return jsonify({'error': 'Internal server error retrieving PDF'}), 500