from flask import Flask, request, jsonify, send_file
import os
from werkzeug.utils import secure_filename
from utils.pdf_parser import extract_cv_info
import redis
from datetime import datetime
import json
import hashlib # Added for hashing
from dateutil.parser import parse
import getpass

app = Flask(__name__)

# --- Configuration ---
USER_PDF_STORAGE_BASE = os.path.join(os.path.expanduser("~"), "python_cv_uploads")
os.makedirs(USER_PDF_STORAGE_BASE, exist_ok=True)

app.config.update(
    UPLOAD_FOLDER='uploads/',
    ALLOWED_EXTENSIONS={'pdf'},
    REDIS_HOST='localhost',
    REDIS_PORT=6379,
    REDIS_DB=0,
    MAX_CONTENT_LENGTH=100 * 1024 * 1024,
    MAX_FILES_PER_UPLOAD=100
)

# --- Redis Connection ---
redis_client = redis.Redis(
    host=app.config['REDIS_HOST'],
    port=app.config['REDIS_PORT'],
    db=app.config['REDIS_DB'],
    decode_responses=False # Keep as False for binary safety, decode when needed
)

os.makedirs(app.config['UPLOAD_FOLDER'], exist_ok=True)

def allowed_file(filename):
    return '.' in filename and \
           filename.rsplit('.', 1)[1].lower() in app.config['ALLOWED_EXTENSIONS']

# --- New Helper Function for Content Hashing ---
def generate_cv_content_hash(cv_data):
    """
    Generates a hash representing the core content of the CV.
    This helps identify if the *content* has changed, even if the file is different.
    Focuses on key fields likely to change meaningfully.
    """
    # Select key fields that represent the core content
    key_fields = {
        'name': cv_data.get('personal_info', {}).get('name', ''),
        'email': cv_data.get('personal_info', {}).get('email', ''),
        'phone': cv_data.get('personal_info', {}).get('phone', ''),
        # Consider adding a condensed version of experience/education if needed,
        # but names, contact, skills are often sufficient for a quick check.
        'skills_flat': sorted([skill.lower() for sublist in cv_data.get('skills', {}).values() for skill in sublist]),
        'experience_titles': sorted([exp.get('position', '').lower() for exp in cv_data.get('experience', []) if exp.get('position')]),
        'education_degrees': sorted([edu.get('degree', '').lower() for edu in cv_data.get('education', []) if edu.get('degree')])
    }
    # Serialize the selected data. Sorting lists ensures order doesn't affect the hash.
    content_str = json.dumps(key_fields, sort_keys=True, separators=(',', ':')) # Compact JSON
    # Generate a hash (e.g., SHA256) of this string
    content_hash = hashlib.sha256(content_str.encode('utf-8')).hexdigest()
    return content_hash
# --- End of New Helper Function ---

def index_cv_data(cv_id, cv_data, pipe=None):
    redis_pipe = pipe or redis_client
    try:
        # Index by name
        if 'name' in cv_data.get('personal_info', {}):
            name = cv_data['personal_info']['name'].lower()
            redis_pipe.sadd(f"index:name:{name}", cv_id)
        # Index by email
        if 'email' in cv_data.get('personal_info', {}):
            email = cv_data['personal_info']['email'].lower()
            redis_pipe.sadd(f"index:email:{email}", cv_id)
        # Index by skills
        for skill_type, skills in cv_data.get('skills', {}).items():
            for skill in skills:
                skill_normalized = skill.lower().strip()
                redis_pipe.sadd(f"index:skill:{skill_normalized}", cv_id)
                redis_pipe.sadd(f"index:skill_type:{skill_type.lower()}", cv_id)
        # Index by education keywords
        for edu in cv_data.get('education', []):
            if 'degree' in edu:
                for word in edu['degree'].lower().split():
                    redis_pipe.sadd(f"index:education:{word}", cv_id)
            if 'institution' in edu:
                for word in edu['institution'].lower().split():
                    redis_pipe.sadd(f"index:institution:{word}", cv_id)
        # Index by experience years (simplified)
        years_exp = estimate_years_experience(cv_data.get('experience', []))
        if years_exp is not None: # Check for None specifically
             redis_pipe.sadd(f"index:experience_years:{years_exp}", cv_id)
        # Index by job titles/positions
        for exp in cv_data.get('experience', []):
            if 'position' in exp:
                for word in exp['position'].lower().split():
                    redis_pipe.sadd(f"index:position:{word}", cv_id)
        # Index by filename (partial)
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

def estimate_years_experience(experience_list):
    if not experience_list:
        return None
    total_days = 0
    for exp in experience_list:
        if 'duration' in exp:
            duration = exp['duration']
            try:
                if '-' in duration:
                    start, end = duration.split('-')
                    start_date = parse(start.strip())
                    end_date = parse(end.strip()) if end.strip().lower() != 'present' else datetime.now()
                    total_days += (end_date - start_date).days
                elif 'year' in duration.lower():
                     import re
                     year_match = re.search(r'(\d+)', duration)
                     if year_match:
                         years = int(year_match.group(1))
                         total_days += years * 365
            except Exception as e:
                print(f"Error parsing duration '{duration}': {e}")
                continue
    years = round(total_days / 365) if total_days > 0 else 0
    return years if years > 0 else None # Return None if 0 or negative

def format_cv_result(cv_id_bytes, cv_data_bytes_dict):
    try:
        cv_id = cv_id_bytes.decode() if isinstance(cv_id_bytes, bytes) else cv_id_bytes
        def safe_loads(key_bytes, default):
            data_bytes = cv_data_bytes_dict.get(key_bytes, None)
            if data_bytes is None:
                return default
            try:
                return json.loads(data_bytes.decode())
            except (json.JSONDecodeError, UnicodeDecodeError) as e:
                print(f"Error decoding/parsing {key_bytes}: {e}")
                return default
        personal_info = safe_loads(b'personal_info', {})
        skills = safe_loads(b'skills', {})
        education = safe_loads(b'education', [])
        experience = safe_loads(b'experience', [])
        def safe_decode(key_bytes, default=""):
            data_bytes = cv_data_bytes_dict.get(key_bytes, None)
            if data_bytes is None:
                return default
            try:
                return data_bytes.decode()
            except UnicodeDecodeError:
                return default
        return {
            'id': cv_id,
            'name': personal_info.get('name', ''),
            'email': personal_info.get('email', ''),
            'phone': personal_info.get('phone', ''),
            'filename': safe_decode(b'filename'),
            'upload_date': safe_decode(b'upload_date')
        }
    except Exception as e:
        print(f"Error formatting CV result for {cv_id_bytes}: {e}")
        return {
            'id': cv_id_bytes.decode() if isinstance(cv_id_bytes, bytes) else str(cv_id_bytes),
            'name': 'Error',
            'email': '',
            'phone': '',
            'filename': 'Error',
            'upload_date': ''
        }

@app.route('/')
def index():
    return jsonify({"message": "CV Parser API is running. Use /upload and /search endpoints."}), 200

@app.route('/upload', methods=['POST'])
def upload_files():
    if 'files[]' not in request.files:
        return jsonify({'error': 'No files uploaded'}), 400
    files = request.files.getlist('files[]')
    if len(files) > app.config['MAX_FILES_PER_UPLOAD']:
        return jsonify({
            'error': f'Too many files. Maximum {app.config["MAX_FILES_PER_UPLOAD"]} allowed',
            'max_files': app.config['MAX_FILES_PER_UPLOAD']
        }), 400
    results = []
    errors = []
    try:
        current_user = getpass.getuser()
    except Exception as e:
        print(f"Could not determine user, using 'unknown': {e}")
        current_user = "unknown"
    user_upload_dir = os.path.join(USER_PDF_STORAGE_BASE, current_user)
    os.makedirs(user_upload_dir, exist_ok=True) # Create directory if it doesn't exist

    for file in files:
        try:
            if not file or file.filename == '':
                errors.append({'filename': 'empty', 'error': 'No file selected'})
                continue
            if not allowed_file(file.filename):
                errors.append({
                    'filename': file.filename,
                    'error': 'Invalid file type. Only PDFs allowed'
                })
                continue
            filename = secure_filename(file.filename)
            temp_filepath = os.path.join(app.config['UPLOAD_FOLDER'], filename)
            file.save(temp_filepath)
            user_pdf_filepath = os.path.join(user_upload_dir, filename)
            import shutil
            shutil.copy2(temp_filepath, user_pdf_filepath)
            cv_data = extract_cv_info(temp_filepath) # Process the temp file

            # --- NEW LOGIC: Check for Duplicates/Updates (Keep Old, Add New) ---
            user_email = cv_data.get('personal_info', {}).get('email', '').lower().strip()
            action = "new"  # Default action
            existing_cv_id = None # Not used for updating, but kept for reporting

            if user_email:
                # 1. Find existing CV IDs associated with this email
                email_index_key = f"index:email:{user_email}"
                potential_existing_ids = redis_client.smembers(email_index_key)
                potential_existing_ids = [eid.decode() for eid in potential_existing_ids if isinstance(eid, bytes)]

                if potential_existing_ids:
                    # 2. Generate content hash for the new CV
                    new_cv_content_hash = generate_cv_content_hash(cv_data)

                    # 3. Compare with existing CVs for this email
                    for eid in potential_existing_ids:
                        existing_content_hash_bytes = redis_client.hget(eid, 'content_hash')
                        if existing_content_hash_bytes:
                            try:
                                existing_content_hash = existing_content_hash_bytes.decode('utf-8')
                                if new_cv_content_hash == existing_content_hash:
                                    # Found an exact content match - Duplicate
                                    action = "duplicate"
                                    print(f"Duplicate CV detected for email {user_email}. Skipping {filename}. Existing ID: {eid}")
                                    errors.append({
                                        'filename': filename,
                                        'error': f'Duplicate CV detected (based on content). Existing entry ID: {eid}. File skipped.'
                                    })
                                    break # Stop checking other IDs for this email
                                else:
                                    # Content hash differs - Potential Update (Keep Old, Add New)
                                    action = "update" # Indicate it's an update, but we'll store as new
                                    existing_cv_id = eid # Store for reporting
                                    print(f"Updated CV detected for email {user_email}. Storing {filename} as a new entry. Existing ID: {eid}")
                                    # Do NOT break here, continue processing as 'new' but log the update detection
                                    # The loop will finish, and the 'new' cv_id generation below will run.
                            except (UnicodeDecodeError, Exception) as e:
                                print(f"Error comparing content hash for {eid}: {e}")
                                # Continue checking other potential IDs if comparison fails

            # If it's a duplicate, skip further processing for this file
            if action == "duplicate":
                if os.path.exists(temp_filepath):
                    os.remove(temp_filepath)
                continue # Move to the next file

            # --- END NEW LOGIC ---

            # If not a duplicate, proceed with processing (treat 'update' as 'new' for storage)
            if not cv_data or not cv_data.get('personal_info', {}).get('name'):
                errors.append({
                    'filename': filename,
                    'error': 'No valid CV data extracted - possibly not a CV'
                })
                if os.path.exists(temp_filepath):
                   os.remove(temp_filepath)
                continue

            cv_data['filename'] = filename
            cv_data['upload_date'] = datetime.now().isoformat()
            cv_data['saved_pdf_path'] = user_pdf_filepath
            cv_data['content_hash'] = generate_cv_content_hash(cv_data)

            import hashlib
            timestamp = datetime.now().timestamp()
            hash_input = f"{filename}{timestamp}".encode()
            cv_id = f"cv:{timestamp}-{hashlib.md5(hash_input).hexdigest()[:8]}"

            redis_data = {}
            for key, value in cv_data.items():
                if isinstance(value, (dict, list)):
                    redis_data[key] = json.dumps(value)
                else:
                    redis_data[key] = str(value)

            with redis_client.pipeline() as pipe:
                pipe.hset(cv_id, mapping=redis_data)
                index_cv_data(cv_id, cv_data, pipe=pipe)
                pipe.execute()

            results.append({
                'id': cv_id,
                'filename': filename,
                'name': cv_data.get('personal_info', {}).get('name', ''),
                'action': action, # "new", "duplicate", or "update" (stored as new)
                'existing_id': existing_cv_id if action == "update" else None # Report ID if it was an update
            })
            if os.path.exists(temp_filepath):
                os.remove(temp_filepath)

        except Exception as e:
            errors.append({
                'filename': file.filename,
                'error': f"Upload/Processing Error: {str(e)}"
            })
            try:
                if hasattr(file, 'filename') and file.filename:
                    temp_path = os.path.join(app.config['UPLOAD_FOLDER'], secure_filename(file.filename))
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
        'errors': errors[:10] # Limit errors in response
    })

@app.route('/search')
def search():
    query = request.args.get('q', '').strip().lower()
    page = int(request.args.get('page', 1))
    per_page = 10
    results = []
    total = 0
    if query:
        search_keys = redis_client.keys(f"index:*{query}*")
        cv_ids = set()
        for key in search_keys:
            try:
                members = redis_client.smembers(key)
                for member in members:
                    try:
                        cv_ids.add(member.decode())
                    except (UnicodeDecodeError, AttributeError):
                        pass
            except (UnicodeDecodeError, AttributeError):
                 pass # Skip problematic keys
        total = len(cv_ids)
        start = (page - 1) * per_page
        end = start + per_page
        cv_id_list = list(cv_ids)
        paginated_cv_ids = cv_id_list[start:end]
        for cv_id in paginated_cv_ids:
            try:
                cv_data = redis_client.hgetall(cv_id)
                if cv_data:
                    formatted_result = format_cv_result(cv_id, cv_data)
                    results.append(formatted_result)
                else:
                    print(f"Warning: CV ID {cv_id} found in index but data missing in Redis.")
                    total -= 1
            except Exception as e:
                print(f"Error processing CV {cv_id} during search: {e}")
                continue
    else:
        all_cvs = redis_client.keys('cv:*')
        all_cvs_decoded = []
        for key in all_cvs:
             try:
                 all_cvs_decoded.append(key.decode())
             except (UnicodeDecodeError, AttributeError):
                 pass
        total = len(all_cvs_decoded)
        start = (page - 1) * per_page
        end = start + per_page
        sorted_cv_ids = sorted(all_cvs_decoded, reverse=True) # Sort by key descending
        paginated_cv_ids = sorted_cv_ids[start:end]
        for cv_id in paginated_cv_ids:
            try:
                cv_data = redis_client.hgetall(cv_id)
                if cv_data:
                    formatted_result = format_cv_result(cv_id, cv_data)
                    results.append(formatted_result)
                else:
                     print(f"Warning: Recent CV ID {cv_id} data missing in Redis.")
                     total -= 1
            except Exception as e:
                 print(f"Error processing recent CV {cv_id}: {e}")
                 continue
    total_pages = (total + per_page - 1) // per_page if per_page > 0 else 1
    if total_pages <= 0:
         total_pages = 1
    return jsonify({
        'results': results,
        'query': query,
        'page': page,
        'per_page': per_page,
        'total': total,
        'total_pages': total_pages
    })

@app.route('/api/view/<cv_id>')
def api_view_cv(cv_id):
    try:
        cv_data_bytes = redis_client.hgetall(cv_id)
        if not cv_data_bytes:
            return jsonify({'error': 'CV not found'}), 404
        processed_data = {}
        for key_bytes, value_bytes in cv_data_bytes.items():
            try:
                key_str = key_bytes.decode()
                try:
                    processed_data[key_str] = json.loads(value_bytes.decode())
                except json.JSONDecodeError:
                    processed_data[key_str] = value_bytes.decode()
            except UnicodeDecodeError:
                 processed_data[key_bytes] = value_bytes # Keep as bytes if decode fails
            except Exception as e:
                 print(f"Error processing key {key_bytes} for CV {cv_id}: {e}")
                 processed_data[key_bytes.decode() if isinstance(key_bytes, bytes) else str(key_bytes)] = "[Error Processing Field]"
        return jsonify(processed_data), 200
    except Exception as e:
        print(f"Unexpected error fetching CV {cv_id}: {e}")
        return jsonify({'error': 'Internal server error fetching CV details'}), 500

@app.route('/api/stats')
def api_stats():
    try:
        total_cvs = len(redis_client.keys(b'cv:*'))
        total_indexes = len(redis_client.keys(b'index:*'))
        all_skills = {}
        skill_keys = redis_client.keys(b'index:skill:*')
        for key_bytes in skill_keys:
            try:
                skill_name = key_bytes.decode().split(':')[-1]
                count = redis_client.scard(key_bytes)
                all_skills[skill_name] = count
            except Exception as e:
                 print(f"Error processing skill index key {key_bytes}: {e}")
                 pass
        top_skills = sorted(all_skills.items(), key=lambda x: x[1], reverse=True)[:10]
        return jsonify({
            'total_cvs': total_cvs,
            'total_indexes': total_indexes,
            'top_skills': top_skills
        }), 200
    except Exception as e:
         print(f"Error fetching stats: {e}")
         return jsonify({'error': 'Internal server error fetching statistics'}), 500

@app.route('/api/download_pdf/<cv_id>')
def download_pdf(cv_id):
    try:
        saved_pdf_path_bytes = redis_client.hget(cv_id, 'saved_pdf_path')
        if not saved_pdf_path_bytes:
            return jsonify({'error': 'PDF path not found for this CV ID'}), 404
        saved_pdf_path = saved_pdf_path_bytes.decode('utf-8')
        if not os.path.exists(saved_pdf_path):
            return jsonify({'error': 'PDF file not found on server'}), 404
        return send_file(saved_pdf_path, as_attachment=False)
    except Exception as e:
        print(f"Error serving PDF for {cv_id}: {e}")
        return jsonify({'error': 'Internal server error retrieving PDF'}), 500

@app.route('/api/indexes')
def get_indexes():
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
        all_index_keys = redis_client.keys(b'index:*')
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
        for category in categorized_indexes:
             categorized_indexes[category].sort()
        return jsonify(categorized_indexes), 200
    except Exception as e:
        print(f"Error fetching indexes: {e}")
        import traceback
        traceback.print_exc()
        return jsonify({'error': 'Internal server error fetching indexes'}), 500

@app.route('/api/view/<cv_id>', methods=['PUT'])  # Use PUT for updates
def api_update_cv(cv_id):
    try:
        if not request.is_json:
            return jsonify({'error': 'Content-Type must be application/json'}), 400
        updated_data = request.get_json()
        if not updated_data:
            return jsonify({'error': 'No JSON data provided in request body'}), 400
        if not redis_client.exists(cv_id):
            return jsonify({'error': 'CV not found'}), 404

        # --- UPDATE: Ensure content_hash is updated if core data changes ---
        # Fetch existing data to merge with updates for hash calculation
        existing_data_bytes = redis_client.hgetall(cv_id)
        existing_data = {}
        for k_bytes, v_bytes in existing_data_bytes.items():
            try:
                k_str = k_bytes.decode()
                try:
                    existing_data[k_str] = json.loads(v_bytes.decode())
                except json.JSONDecodeError:
                    existing_data[k_str] = v_bytes.decode()
            except UnicodeDecodeError:
                pass # Skip binary fields if any

        # Merge existing with updated data (shallow merge for top-level keys)
        merged_data = {**existing_data, **updated_data}

        # Recalculate content hash if relevant fields might have changed
        if any(key in updated_data for key in ['personal_info', 'skills', 'experience', 'education']):
            updated_content_hash = generate_cv_content_hash(merged_data)
            updated_data['content_hash'] = updated_content_hash
            print(f"Updated content hash for {cv_id}")
        # --- END UPDATE ---

        redis_update_data = {}
        for key, value in updated_data.items():
            key_str = str(key)
            if isinstance(value, (dict, list)):
                redis_update_data[key_str] = json.dumps(value)
            else:
                redis_update_data[key_str] = str(value)

        redis_client.hset(cv_id, mapping=redis_update_data)

        # Re-index the updated CV
        try:
            # Remove old indexes for this cv_id
            index_keys = redis_client.keys("index:*")
            pipeline = redis_client.pipeline()
            for key in index_keys:
                pipeline.srem(key, cv_id)
            pipeline.execute()
            # Index the merged/updated data
            index_cv_data(cv_id, merged_data)
        except Exception as e:
            print(f"Error re-indexing CV {cv_id} after update: {e}")

        return jsonify({'message': 'CV data updated successfully'}), 200
    except Exception as e:
        print(f"Unexpected error updating CV {cv_id}: {e}")
        import traceback
        traceback.print_exc()
        return jsonify({'error': 'Internal server error updating CV'}), 500

if __name__ == '__main__':
    app.run(debug=True, host='0.0.0.0')