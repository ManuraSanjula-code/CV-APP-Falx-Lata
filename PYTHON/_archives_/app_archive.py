from flask import Flask, render_template, request, jsonify, redirect, url_for
import os
from werkzeug.utils import secure_filename
from pdf_parser_archive import extract_cv_info
import redis
from datetime import datetime
import json
from dateutil.parser import parse

app = Flask(__name__)
app.config.update(
    UPLOAD_FOLDER='uploads/',
    ALLOWED_EXTENSIONS={'pdf'},
    REDIS_HOST='localhost',
    REDIS_PORT=6379,
    REDIS_DB=0,
    MAX_CONTENT_LENGTH=100 * 1024 * 1024,  # 100MB max upload size
    MAX_FILES_PER_UPLOAD=100  # Limit number of files per upload
)

# Connect to Redis
redis_client = redis.Redis(
    host=app.config['REDIS_HOST'],
    port=app.config['REDIS_PORT'],
    db=app.config['REDIS_DB']
)

# Ensure upload folder exists
os.makedirs(app.config['UPLOAD_FOLDER'], exist_ok=True)

def allowed_file(filename):
    return '.' in filename and \
           filename.rsplit('.', 1)[1].lower() in app.config['ALLOWED_EXTENSIONS']

@app.route('/')
def index():
    return render_template('upload.html')

@app.route('/upload', methods=['POST'])
def upload_files():
    if 'files[]' not in request.files:
        return jsonify({'error': 'No files uploaded'}), 400
    
    files = request.files.getlist('files[]')
    
    # Validate number of files
    if len(files) > app.config['MAX_FILES_PER_UPLOAD']:
        return jsonify({
            'error': f'Too many files. Maximum {app.config["MAX_FILES_PER_UPLOAD"]} allowed',
            'max_files': app.config['MAX_FILES_PER_UPLOAD']
        }), 400
    
    results = []
    errors = []
    
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
            filepath = os.path.join(app.config['UPLOAD_FOLDER'], filename)
            
            # Save file temporarily
            file.save(filepath)
            
            # Process the PDF
            cv_data = extract_cv_info(filepath)
            if not cv_data or not cv_data.get('personal_info', {}).get('name'):
                errors.append({
                    'filename': filename,
                    'error': 'No valid CV data extracted - possibly not a CV'
                })
                os.remove(filepath)
                continue
                
            cv_data['filename'] = filename
            cv_data['upload_date'] = datetime.now().isoformat()
            
            # Generate unique ID
            cv_id = f"cv:{datetime.now().timestamp()}-{hash(filename)}"
            
            # Prepare Redis data
            redis_data = {}
            for key, value in cv_data.items():
                if isinstance(value, (dict, list)):
                    redis_data[key] = json.dumps(value)
                else:
                    redis_data[key] = str(value)
            
            # Use pipeline for atomic operations
            with redis_client.pipeline() as pipe:
                # Store CV data
                pipe.hset(cv_id, mapping=redis_data)
                # Index for search
                index_cv_data(cv_id, cv_data, pipe=pipe)
                # Execute all commands
                pipe.execute()
            
            results.append({
                'id': cv_id,
                'filename': filename,
                'name': cv_data.get('personal_info', {}).get('name', '')
            })
            
            # Clean up temporary file
            os.remove(filepath)
            
        except Exception as e:
            errors.append({
                'filename': file.filename,
                'error': str(e)
            })
            continue
    
    return jsonify({
        'success': True,
        'processed': results,
        'total_files': len(files),
        'success_count': len(results),
        'error_count': len(errors),
        'errors': errors[:10]  # Return first 10 errors if any
    })

def index_cv_data(cv_id, cv_data, pipe=None):
    """Enhanced indexing with Redis pipeline support"""
    redis_pipe = pipe or redis_client
    
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
    
    # Index by education
    for edu in cv_data.get('education', []):
        if 'degree' in edu:
            for word in edu['degree'].lower().split():
                redis_pipe.sadd(f"index:education:{word}", cv_id)
        if 'institution' in edu:
            for word in edu['institution'].lower().split():
                redis_pipe.sadd(f"index:institution:{word}", cv_id)
    
    # Index by experience years
    years_exp = estimate_years_experience(cv_data.get('experience', []))
    if years_exp:
        redis_pipe.sadd(f"index:experience_years:{years_exp}", cv_id)
    
    # Index by job titles
    for exp in cv_data.get('experience', []):
        if 'position' in exp:
            for word in exp['position'].lower().split():
                redis_pipe.sadd(f"index:position:{word}", cv_id)
    
    if not pipe:
        redis_pipe.execute()

def estimate_years_experience(experience_list):
    """Estimate total years of experience"""
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
            except:
                continue
    
    return round(total_days / 365) if total_days > 0 else None

@app.route('/search')
def search():
    query = request.args.get('q', '').strip().lower()
    page = int(request.args.get('page', 1))
    per_page = 10
    
    results = []
    total = 0
    
    if query:
        # Search across all indexes
        search_keys = redis_client.keys(f"index:*{query}*")
        cv_ids = set()
        
        for key in search_keys:
            cv_ids.update(redis_client.smembers(key))
        
        total = len(cv_ids)
        start = (page - 1) * per_page
        end = start + per_page
        
        # Get paginated results
        for cv_id in list(cv_ids)[start:end]:
            try:
                cv_data = redis_client.hgetall(cv_id)
                results.append(format_cv_result(cv_id, cv_data))
            except:
                continue
    else:
        # Show recent CVs if no query
        all_cvs = redis_client.keys('cv:*')
        total = len(all_cvs)
        start = (page - 1) * per_page
        end = start + per_page
        
        for cv_id in sorted(all_cvs, reverse=True)[start:end]:
            try:
                cv_data = redis_client.hgetall(cv_id)
                results.append(format_cv_result(cv_id, cv_data))
            except:
                continue
    
    total_pages = (total + per_page - 1) // per_page
    
    return render_template('search.html',
                         results=results,
                         query=query,
                         page=page,
                         per_page=per_page,
                         total=total,
                         total_pages=total_pages)

def format_cv_result(cv_id, cv_data):
    """Format CV data for display in search results"""
    personal_info = json.loads(cv_data.get(b'personal_info', b'{}'))
    skills = json.loads(cv_data.get(b'skills', b'{}'))
    education = json.loads(cv_data.get(b'education', b'[]'))
    experience = json.loads(cv_data.get(b'experience', b'[]'))
    
    return {
        'id': cv_id.decode(),
        'name': personal_info.get('name', ''),
        'email': personal_info.get('email', ''),
        'phone': personal_info.get('phone', ''),
        'skills': skills,
        'education': education,
        'experience': experience,
        'filename': cv_data.get(b'filename', b'').decode(),
        'upload_date': cv_data.get(b'upload_date', b'').decode()
    }

@app.route('/view/<cv_id>')
def view_cv(cv_id):
    cv_data = redis_client.hgetall(cv_id)
    if not cv_data:
        return "CV not found", 404
    
    # Convert bytes to strings and parse JSON fields
    processed_data = {}
    for key, value in cv_data.items():
        key_str = key.decode()
        try:
            processed_data[key_str] = json.loads(value)
        except json.JSONDecodeError:
            processed_data[key_str] = value.decode()
    
    return render_template('view.html', cv_data=processed_data)

@app.route('/stats')
def stats():
    """Get statistics about stored CVs"""
    total_cvs = len(redis_client.keys('cv:*'))
    total_indexes = len(redis_client.keys('index:*'))
    
    # Get skill frequency
    all_skills = {}
    skill_keys = redis_client.keys('index:skill:*')
    for key in skill_keys:
        skill_name = key.decode().split(':')[-1]
        count = redis_client.scard(key)
        all_skills[skill_name] = count
    
    top_skills = sorted(all_skills.items(), key=lambda x: x[1], reverse=True)[:10]
    
    return jsonify({
        'total_cvs': total_cvs,
        'total_indexes': total_indexes,
        'top_skills': top_skills
    })

if __name__ == '__main__':
    app.run(debug=True)