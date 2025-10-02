import os
import json
import shutil
import getpass
import datetime
import asyncio
import concurrent.futures
from functools import wraps
from flask import Blueprint, request, jsonify, send_file
from werkzeug.utils import secure_filename
from threading import Lock, Semaphore
import time

from config import Config
from utils.redis_client import RedisConnection
from utils.cv_utils import CVUtils
from models.cv_processor import CVProcessor
from models.auth_manager import AuthManager
from models.audit_logger import AuditLogger
from utils.pdf_parser import extract_cv_info

cv_bp = Blueprint('cv', __name__)

# Thread-safe processing control
processing_lock = Lock()
processing_semaphore = Semaphore(10)  # Allow max 10 concurrent CV extractions
upload_status_cache = {}  # Store upload progress


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


def process_single_cv(file_info, config, user_upload_dir, redis_conn, cv_processor, current_user_username, request_ip):
    """Process a single CV file with error handling and resource management."""
    filename, temp_filepath = file_info

    with processing_semaphore:  # Limit concurrent processing
        try:
            start_time = time.time()

            # Copy to user directory
            user_pdf_filepath = os.path.join(user_upload_dir, filename)
            shutil.copy2(temp_filepath, user_pdf_filepath)

            # Extract CV data with timeout handling
            cv_data = extract_cv_info(temp_filepath)

            if not cv_data or not cv_data.get('personal_info', {}).get('name'):
                if os.path.exists(temp_filepath):
                    os.remove(temp_filepath)
                return {
                    'filename': filename,
                    'status': 'error',
                    'error': 'No valid CV data extracted - possibly not a CV'
                }

            # Check for duplicates
            action, existing_cv_id = cv_processor.check_duplicate_cv(cv_data)

            if action == "duplicate":
                if os.path.exists(temp_filepath):
                    os.remove(temp_filepath)
                return {
                    'filename': filename,
                    'status': 'skipped',
                    'reason': f'Duplicate CV detected. Existing entry ID: {existing_cv_id}'
                }

            # Prepare CV data for storage
            cv_data.update({
                'filename': filename,
                'upload_date': datetime.datetime.now().isoformat(),
                'saved_pdf_path': user_pdf_filepath,
                'content_hash': CVUtils.generate_cv_content_hash(cv_data),
                'gender': None,
                'type': None,
                'processing_time': time.time() - start_time
            })

            cv_id = CVUtils.generate_cv_id(filename)

            # Store in Redis with pipeline for efficiency
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

            # Clean up temp file
            if os.path.exists(temp_filepath):
                os.remove(temp_filepath)

            return {
                'id': cv_id,
                'filename': filename,
                'name': cv_data.get('personal_info', {}).get('name', ''),
                'status': 'success',
                'action': action,
                'existing_id': existing_cv_id if action == "update" else None,
                'processing_time': cv_data['processing_time']
            }

        except Exception as e:
            # Clean up on error
            try:
                if os.path.exists(temp_filepath):
                    os.remove(temp_filepath)
            except:
                pass

            return {
                'filename': filename,
                'status': 'error',
                'error': f"Processing Error: {str(e)}"
            }


def process_cvs_batch(file_list, config, user_upload_dir, components, current_user_username, request_ip, batch_id):
    """Process a batch of CVs using ThreadPoolExecutor for parallel processing."""
    redis_conn, cv_processor, _, audit_logger = components

    results = []
    errors = []

    # Use ThreadPoolExecutor for parallel processing
    max_workers = min(len(file_list), 8)  # Limit concurrent threads

    with concurrent.futures.ThreadPoolExecutor(max_workers=max_workers) as executor:
        # Submit all tasks
        future_to_file = {
            executor.submit(
                process_single_cv,
                file_info,
                config,
                user_upload_dir,
                redis_conn,
                cv_processor,
                current_user_username,
                request_ip
            ): file_info[0] for file_info in file_list
        }

        # Process completed tasks as they finish
        for future in concurrent.futures.as_completed(future_to_file):
            filename = future_to_file[future]
            try:
                result = future.result(timeout=300)  # 5-minute timeout per CV

                if result['status'] == 'success':
                    results.append(result)
                    # Log successful uploads (batch logging for efficiency)
                    if len(results) % 10 == 0:  # Log every 10 successful uploads
                        audit_logger.log_action(
                            user=current_user_username,
                            action='BULK_UPLOAD_BATCH',
                            details={'batch_size': 10, 'batch_id': batch_id},
                            ip_address=request_ip
                        )
                elif result['status'] == 'skipped':
                    results.append(result)
                else:
                    errors.append(result)

                # Update progress cache
                with processing_lock:
                    if batch_id in upload_status_cache:
                        upload_status_cache[batch_id]['processed'] += 1

            except concurrent.futures.TimeoutError:
                errors.append({
                    'filename': filename,
                    'status': 'error',
                    'error': 'Processing timeout (5 minutes exceeded)'
                })
            except Exception as e:
                errors.append({
                    'filename': filename,
                    'status': 'error',
                    'error': f"Unexpected error: {str(e)}"
                })

    return results, errors


@cv_bp.route('/upload', methods=['POST'])
@token_required
def upload_files(current_user_username):
    """Enhanced upload handler for bulk CV processing."""
    config = Config()

    if 'files[]' not in request.files:
        return jsonify({'error': 'No files uploaded'}), 400

    files = request.files.getlist('files[]')
    total_files = len(files)

    # Check file limits
    if total_files > config.MAX_FILES_PER_UPLOAD:
        return jsonify({
            'error': f'Too many files. Maximum {config.MAX_FILES_PER_UPLOAD} allowed',
            'max_files': config.MAX_FILES_PER_UPLOAD
        }), 400

    # Generate batch ID for tracking
    batch_id = f"batch_{int(time.time())}_{current_user_username}"

    # ✅ CAPTURE REQUEST DATA BEFORE THREADING
    request_ip = request.remote_addr

    # Initialize progress tracking
    with processing_lock:
        upload_status_cache[batch_id] = {
            'total_files': total_files,
            'processed': 0,
            'started_at': datetime.datetime.now().isoformat(),
            'status': 'processing'
        }

    redis_conn, cv_processor, _, audit_logger = get_components()

    # Get current user for file storage
    try:
        current_user = getpass.getuser()
    except Exception as e:
        print(f"Could not determine user, using 'unknown': {e}")
        current_user = "unknown"

    user_upload_dir = os.path.join(config.USER_PDF_STORAGE_BASE, current_user)
    os.makedirs(user_upload_dir, exist_ok=True)

    # Pre-validate and save all files first
    file_list = []
    validation_errors = []

    for file in files:
        try:
            if not file or file.filename == '':
                validation_errors.append({'filename': 'empty', 'error': 'No file selected'})
                continue

            if not CVUtils.allowed_file(file.filename, config.ALLOWED_EXTENSIONS):
                validation_errors.append({
                    'filename': file.filename,
                    'error': 'Invalid file type. Only PDFs and DOCX allowed'
                })
                continue

            filename = secure_filename(file.filename)
            temp_filepath = os.path.join(config.UPLOAD_FOLDER, f"{batch_id}_{filename}")
            file.save(temp_filepath)

            file_list.append((filename, temp_filepath))

        except Exception as e:
            validation_errors.append({
                'filename': getattr(file, 'filename', 'unknown'),
                'error': f"File validation error: {str(e)}"
            })

    if validation_errors:
        # Clean up any saved files if validation failed
        for filename, temp_filepath in file_list:
            if os.path.exists(temp_filepath):
                os.remove(temp_filepath)

        return jsonify({
            'error': 'File validation failed',
            'validation_errors': validation_errors
        }), 400

    # For small batches, process synchronously
    if total_files <= 10:
        results, errors = process_cvs_batch(
            file_list,
            config,
            user_upload_dir,
            (redis_conn, cv_processor, _, audit_logger),
            current_user_username,
            request_ip,  # ✅ Use captured value
            batch_id
        )

        # Clean up progress tracking
        with processing_lock:
            if batch_id in upload_status_cache:
                del upload_status_cache[batch_id]

        return jsonify({
            'success': True,
            'batch_id': batch_id,
            'processed': results,
            'total_files': total_files,
            'success_count': len([r for r in results if r['status'] == 'success']),
            'skipped_count': len([r for r in results if r['status'] == 'skipped']),
            'error_count': len(errors),
            'errors': errors[:20],
            'processing_mode': 'synchronous'
        })

    # For large batches, process asynchronously
    else:
        # Start background processing
        def background_process():
            try:
                results, errors = process_cvs_batch(
                    file_list,
                    config,
                    user_upload_dir,
                    (redis_conn, cv_processor, _, audit_logger),
                    current_user_username,
                    request_ip,  # ✅ Use captured value from closure
                    batch_id
                )

                # Store final results in Redis for later retrieval
                final_result = {
                    'batch_id': batch_id,
                    'processed': results,
                    'total_files': total_files,
                    'success_count': len([r for r in results if r['status'] == 'success']),
                    'skipped_count': len([r for r in results if r['status'] == 'skipped']),
                    'error_count': len(errors),
                    'errors': errors,
                    'completed_at': datetime.datetime.now().isoformat(),
                    'processing_mode': 'asynchronous'
                }

                redis_conn.client.setex(
                    f"batch_result:{batch_id}",
                    3600,  # Store for 1 hour
                    json.dumps(final_result)
                )

                # Update progress cache
                with processing_lock:
                    if batch_id in upload_status_cache:
                        upload_status_cache[batch_id]['status'] = 'completed'

                # Final audit log
                audit_logger.log_action(
                    user=current_user_username,
                    action='BULK_UPLOAD_COMPLETED',
                    details={
                        'batch_id': batch_id,
                        'total_files': total_files,
                        'success_count': final_result['success_count'],
                        'error_count': final_result['error_count']
                    },
                    ip_address=request_ip  # ✅ Use captured value
                )

            except Exception as e:
                print(f"Background processing error for batch {batch_id}: {e}")
                with processing_lock:
                    if batch_id in upload_status_cache:
                        upload_status_cache[batch_id]['status'] = 'failed'
                        upload_status_cache[batch_id]['error'] = str(e)

        # Start background thread
        import threading
        thread = threading.Thread(target=background_process)
        thread.daemon = True
        thread.start()

        return jsonify({
            'success': True,
            'batch_id': batch_id,
            'message': 'Bulk upload started. Use /upload/status endpoint to check progress.',
            'total_files': total_files,
            'processing_mode': 'asynchronous',
            'status_endpoint': f'/upload/status/{batch_id}'
        }), 202

@cv_bp.route('/upload/status/<batch_id>', methods=['GET'])
@token_required
def get_upload_status(current_user_username, batch_id):
    """Get upload batch status and progress."""
    redis_conn, _, _, _ = get_components()

    # Check if batch is completed (result stored in Redis)
    result_data = redis_conn.client.get(f"batch_result:{batch_id}")
    if result_data:
        try:
            result = json.loads(result_data.decode('utf-8'))
            return jsonify(result), 200
        except (json.JSONDecodeError, UnicodeDecodeError):
            pass

    # Check in-progress status
    with processing_lock:
        if batch_id in upload_status_cache:
            status = upload_status_cache[batch_id].copy()
            status['progress_percentage'] = (status['processed'] / status['total_files']) * 100
            return jsonify(status), 200

    return jsonify({'error': 'Batch ID not found'}), 404


@cv_bp.route('/upload/cancel/<batch_id>', methods=['POST'])
@token_required
def cancel_upload_batch(current_user_username, batch_id):
    """Cancel an ongoing upload batch (best effort)."""
    with processing_lock:
        if batch_id in upload_status_cache:
            upload_status_cache[batch_id]['status'] = 'cancelled'
            return jsonify({'message': 'Batch cancellation requested'}), 200

    return jsonify({'error': 'Batch ID not found or already completed'}), 404


# Keep existing endpoints unchanged...
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
                if isinstance(key_bytes, bytes):
                    key_str = key_bytes.decode('utf-8')
                else:
                    key_str = str(key_bytes)

                if isinstance(value_bytes, bytes):
                    value_str = value_bytes.decode('utf-8')
                else:
                    value_str = str(value_bytes)

                try:
                    processed_data[key_str] = json.loads(value_str)
                except json.JSONDecodeError:
                    processed_data[key_str] = value_str

            except UnicodeDecodeError as e:
                key_str = str(key_bytes) if not isinstance(key_bytes, str) else key_bytes
                processed_data[key_str] = f"[Encoding Error: {str(e)}]"
                print(f"Unicode decode error for key {key_bytes}: {e}")

            except Exception as e:
                key_str = str(key_bytes) if not isinstance(key_bytes, str) else key_bytes
                processed_data[key_str] = f"[Processing Error: {str(e)}]"
                print(f"Error processing key {key_bytes} for CV {cv_id}: {e}")

        return jsonify(processed_data), 200

    except Exception as e:
        print(f"Unexpected error fetching CV {cv_id}: {e}")
        import traceback
        traceback.print_exc()
        return jsonify({'error': 'Internal server error fetching CV details'}), 500