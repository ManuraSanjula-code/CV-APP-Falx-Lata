import re
import spacy
from PyPDF2 import PdfReader
from pdfminer.high_level import extract_text
from typing import Dict, Any, List, Optional
from collections import defaultdict

# Load English language model for spaCy
nlp = spacy.load("en_core_web_sm")


def extract_cv_info(pdf_path: str) -> Dict[str, Any]:
    """Extract all CV information with enhanced parsing"""
    text = extract_text_from_pdf(pdf_path)
    doc = nlp(text)

    return {
        'personal_info': extract_personal_info(text, doc),
        'education': extract_education(text),
        'experience': extract_experience(text),
        'skills': extract_skills(text),
        'languages': extract_languages(text),
        'certifications': extract_certifications(text),
        'projects': extract_projects(text),
        'achievements': extract_achievements(text),
        'assets': extract_assets(text),
        'references': extract_references(text)
    }


def extract_languages(text: str) -> List[str]:
    """
    Extract languages mentioned in the CV with improved detection
    Handles both dedicated language sections and inline mentions
    """
    languages = []

    # Common languages to look for (expanded list)
    common_languages = [
        'english', 'spanish', 'french', 'german', 'chinese',
        'hindi', 'arabic', 'portuguese', 'russian', 'japanese',
        'dutch', 'italian', 'korean', 'flemish', 'sinhala', 'tamil'
    ]

    # First try to find a dedicated languages section
    lang_section = extract_section(text, r'(?i)(languages?|language proficiency)')

    if lang_section:
        # Extract from dedicated section
        lines = [line.strip() for line in lang_section.split('\n') if line.strip()]
        for line in lines:
            # Look for language proficiency levels (e.g., "English (Fluent)")
            lang_match = re.search(r'([A-Za-z]+)\s*(?:\(.*?\))?', line, re.IGNORECASE)
            if lang_match:
                lang = lang_match.group(1).lower()
                if lang in common_languages:
                    languages.append(lang.capitalize())
    else:
        # Fallback: scan entire text for language mentions
        text_lower = text.lower()
        for lang in common_languages:
            if re.search(rf'\b{lang}\b', text_lower):
                languages.append(lang.capitalize())

    # Remove duplicates while preserving order
    seen = set()
    return [lang for lang in languages if not (lang in seen or seen.add(lang))]


def extract_certifications(text: str) -> List[Dict[str, str]]:
    """
    Extract certifications with improved pattern matching
    Returns list of dictionaries with certification details
    """
    certifications = []

    # Try to find certifications section first
    cert_section = extract_section(text, r'(?i)(certifications?|licenses?|credentials?)')

    if cert_section:
        # Split into potential certification entries
        entries = re.split(r'\n(?=\s*(?:[A-Z]|•|\-|\d))', cert_section)

        for entry in entries:
            if not entry.strip():
                continue

            # Extract certification name and issuer
            cert_match = re.search(
                r'^(.*?)\s*(?:\(|from|at|,)\s*([A-Z][a-zA-Z\s&.,-]+?(?:Institute|University|College|Academy|Foundation|Association|Company|Inc\.?|Ltd\.?)|[A-Z][a-zA-Z\s&.,-]+)',
                entry
            )

            # Extract date if present
            date_match = re.search(
                r'(?:20\d{2}\s*[-–to]+\s*20\d{2}|20\d{2}\s*[-–]+\s*(?:Present|Now)|20\d{2}|since\s+20\d{2})',
                entry
            )

            if cert_match or date_match:
                cert_entry = {
                    'name': cert_match.group(1).strip() if cert_match else entry.split(',')[0].strip(),
                    'issuer': cert_match.group(2).strip() if cert_match and cert_match.group(2) else '',
                    'date': date_match.group(0) if date_match else '',
                    'description': entry.strip()
                }
                certifications.append(cert_entry)
    else:
        # Fallback: scan entire text for certification patterns
        lines = [line.strip() for line in text.split('\n') if line.strip()]
        cert_keywords = [
            'certified', 'certification', 'certificate',
            'license', 'credential', 'qualified as',
            r'\b[A-Z]{3,}\b'  # Matches all-caps acronyms (like AWS, PMP)
        ]

        for line in lines:
            if any(re.search(keyword, line, re.IGNORECASE) for keyword in cert_keywords):
                certifications.append({
                    'name': line.split(',')[0].strip(),
                    'issuer': '',
                    'date': '',
                    'description': line.strip()
                })

    return certifications


def extract_projects(text: str) -> List[Dict[str, str]]:
    """
    Extract projects with detailed parsing
    Handles both dedicated project sections and inline mentions
    """
    projects = []

    # Try to find projects section first
    project_section = extract_section(text, r'(?i)(projects|portfolio|personal work|academic work)')

    if project_section:
        # Split into potential project entries
        entries = re.split(r'\n(?=\s*(?:[A-Z]|•|\-|\d))', project_section)

        for entry in entries:
            if not entry.strip():
                continue

            # Extract project name and description
            if ':' in entry:
                # Format: "Project Name: Description"
                parts = entry.split(':', 1)
                project_name = parts[0].strip()
                description = parts[1].strip()
            elif '-' in entry:
                # Format: "Project Name - Description"
                parts = entry.split('-', 1)
                project_name = parts[0].strip()
                description = parts[1].strip()
            else:
                # No clear separator, use entire entry
                project_name = entry.split(',')[0].strip()
                description = entry.strip()

            # Extract technologies if mentioned
            tech_matches = re.findall(
                r'\b(Java|Python|JavaScript|React|Spring|Django|Flask|Node\.?js|AWS|Azure)\b',
                description,
                re.IGNORECASE
            )

            projects.append({
                'name': project_name,
                'description': description,
                'technologies': list(set(tech_matches))  # Remove duplicates
            })
    else:
        # Fallback: scan for project-like entries
        lines = [line.strip() for line in text.split('\n') if line.strip()]
        project_keywords = [
            'developed', 'created', 'built', 'designed',
            'project', 'system', 'application', 'website'
        ]

        current_project = {}
        for line in lines:
            if any(keyword in line.lower() for keyword in project_keywords):
                if current_project:
                    projects.append(current_project)
                    current_project = {}

                if ':' in line:
                    parts = line.split(':', 1)
                    current_project = {
                        'name': parts[0].strip(),
                        'description': parts[1].strip(),
                        'technologies': []
                    }
                else:
                    current_project = {
                        'name': line.split(',')[0].strip(),
                        'description': line.strip(),
                        'technologies': []
                    }
            elif current_project:
                current_project['description'] += ' ' + line

        if current_project:
            projects.append(current_project)

    return projects


def extract_section(text: str, pattern: str) -> str:
    """
    Helper function to extract a specific section from text
    """
    # First try with double line breaks
    match = re.search(
        pattern + r'(.*?)(?:\n\s*\n|\Z)',
        text,
        re.DOTALL | re.IGNORECASE
    )

    if not match:
        # Fallback to single line break if needed
        match = re.search(
            pattern + r'(.*?)(?:\n|\Z)',
            text,
            re.DOTALL | re.IGNORECASE
        )

    return match.group(1).strip() if match else ''


def extract_text_from_pdf(pdf_path: str) -> str:
    """Robust text extraction from PDF"""
    try:
        text = extract_text(pdf_path)
        if len(text.strip()) > 100:
            return text
    except:
        pass

    # Fallback to PyPDF2
    reader = PdfReader(pdf_path)
    text = ""
    for page in reader.pages:
        text += page.extract_text() or ""
    return text


def extract_personal_info(text: str, doc) -> Dict[str, str]:
    """Extract all personal information"""
    email = extract_email(text)
    phone = extract_phone(text)

    return {
        'name': extract_name(text, doc),
        'email': email,
        'phone': phone,
        'address': extract_address(text),
        'github': extract_github(text),
        'age': extract_age(text),
        'nationality': extract_nationality(text),
        'linkedin': extract_linkedin(text)
    }


def extract_name(text: str, doc) -> Optional[str]:
    """Improved name extraction"""
    # Try to find name in first few lines
    lines = [line.strip() for line in text.split('\n') if line.strip()]
    for line in lines[:5]:
        # Look for title case name pattern
        if re.match(r'^[A-Z][a-z]+(?:\s+[A-Z][a-z]+)+$', line):
            return line

    # Fallback to NLP
    for ent in doc.ents:
        if ent.label_ == "PERSON":
            return ent.text
    return None


def extract_email(text: str) -> Optional[str]:
    """Extract email address"""
    email_pattern = r'\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b'
    match = re.search(email_pattern, text)
    return match.group(0) if match else None


def extract_phone(text: str) -> Optional[str]:
    """Extract phone number"""
    phone_pattern = r'(\+?\d{1,3}[-.\s]?)?\(?\d{3}\)?[-.\s]?\d{3}[-.\s]?\d{4}\b'
    match = re.search(phone_pattern, text)
    return match.group(0) if match else None


def extract_address(text: str) -> Optional[str]:
    """Extract address"""
    address_pattern = r'\d{1,5}\s[\w\s]{3,},\s[\w\s]{3,},\s[A-Z]{2}\s\d{5}'
    match = re.search(address_pattern, text)
    return match.group(0) if match else None


def extract_github(text: str) -> Optional[str]:
    """Extract GitHub URL"""
    patterns = [r'github\.com/[a-zA-Z0-9-]+']
    for pattern in patterns:
        match = re.search(pattern, text)
        if match:
            return f"https://{match.group(0)}"
    return None


def extract_age(text: str) -> Optional[str]:
    """Extract age"""
    age_match = re.search(r'(\d{2})\s*years?\s*old', text, re.IGNORECASE)
    return age_match.group(1) if age_match else None


def extract_nationality(text: str) -> Optional[str]:
    """Extract nationality"""
    nation_match = re.search(r'(?:nationality|citizen):?\s*([A-Z][a-z]+)', text, re.IGNORECASE)
    return nation_match.group(1) if nation_match else None


def extract_linkedin(text: str) -> Optional[str]:
    """Extract LinkedIn URL"""
    patterns = [r'linkedin\.com/in/[a-zA-Z0-9-]+']
    for pattern in patterns:
        match = re.search(pattern, text)
        if match:
            return f"https://{match.group(0)}"
    return None


def extract_education(text: str) -> List[Dict[str, str]]:
    """Reliable education extraction"""
    education = []
    lines = [line.strip() for line in text.split('\n') if line.strip()]

    # Find education section
    edu_section = []
    in_section = False

    for line in lines:
        if re.search(r'(?i)(education|academic|qualifications)', line):
            in_section = True
            continue
        if in_section:
            if re.search(r'(?i)(experience|skills|projects)', line):
                break
            edu_section.append(line)

    # If no section found, scan entire text
    if not edu_section:
        edu_section = lines

    # Parse education entries
    current_entry = ""
    for line in edu_section:
        if re.search(r'(?i)\b(B\.?S\.?|B\.?A\.?|Bachelor|M\.?S\.?|M\.?A\.?|Master|PhD|Diploma|Certificate|HND)\b',
                     line):
            if current_entry:
                education.append(parse_education_entry(current_entry))
                current_entry = ""
            current_entry = line
        elif current_entry:
            current_entry += " " + line

    if current_entry:
        education.append(parse_education_entry(current_entry))

    return education


def parse_education_entry(entry: str) -> Dict[str, str]:
    """Parse individual education entry"""
    # Extract year
    year_match = re.search(r'(20\d{2}\s*[-–to]+\s*20\d{2}|20\d{2}\s*[-–]+\s*(?:Present|Now)|20\d{2})', entry)
    year = year_match.group(0) if year_match else ""

    # Extract degree and institution
    parts = re.split(r'[,;]|\bat\b|\bfrom\b|\b-\b', entry)
    parts = [p.strip() for p in parts if p.strip()]

    return {
        'degree': parts[0] if parts else "",
        'institution': parts[1] if len(parts) > 1 else "",
        'year': year,
        'description': entry
    }


def extract_skills(text: str) -> Dict[str, List[str]]:
    """Enhanced skills extraction with better categorization"""
    skills = defaultdict(list)

    # Find skills section
    skills_section = []
    in_section = False

    lines = [line.strip() for line in text.split('\n') if line.strip()]
    for line in lines:
        if re.search(r'(?i)(skills|technical skills|competencies)', line):
            in_section = True
            continue
        if in_section:
            if re.search(r'(?i)(experience|education|projects)', line):
                break
            skills_section.append(line)

    # If no section found, scan entire text
    if not skills_section:
        skills_section = lines

    # Define skill categories and patterns
    skill_categories = {
        'programming': [
            'java', 'python', 'javascript', 'typescript', 'c\\+\\+', 'c#', 'ruby', 'php',
            'swift', 'kotlin', 'go', 'rust', 'scala', 'r\b', 'dart', 'perl'
        ],
        'web': [
            'html', 'css', 'sass', 'less', 'bootstrap', 'tailwind', 'jquery',
            'react', 'angular', 'vue', 'ember', 'svelte', 'next\\.js', 'nuxt\\.js'
        ],
        'mobile': [
            'android', 'ios', 'flutter', 'react native', 'xamarin', 'swiftui'
        ],
        'databases': [
            'mysql', 'postgresql', 'mongodb', 'sql\\s*server', 'oracle',
            'sqlite', 'neo4j', 'cassandra', 'redis', 'dynamodb', 'firebase'
        ],
        'devops': [
            'docker', 'kubernetes', 'aws', 'azure', 'gcp', 'jenkins',
            'ansible', 'terraform', 'github\\s*actions', 'gitlab\\s*ci',
            'circleci', 'prometheus', 'grafana'
        ],
        'data_science': [
            'pandas', 'numpy', 'tensorflow', 'pytorch', 'scikit\\-learn',
            'keras', 'spark', 'hadoop', 'tableau', 'power\\s*bi'
        ],
        'soft': [
            'communication', 'teamwork', 'leadership', 'problem\\s*solving',
            'time\\s*management', 'adaptability', 'creativity', 'critical\\s*thinking',
            'negotiation', 'presentation', 'project\\s*management'
        ]
    }

    skill_text = " ".join(skills_section).lower()

    for category, patterns in skill_categories.items():
        for pattern in patterns:
            if re.search(rf'\b{pattern}\b', skill_text, re.IGNORECASE):
                skills[category].append(pattern.replace('\\', '').replace('-', ' ').title())

    # Remove duplicates while preserving order
    for category in skills:
        seen = set()
        skills[category] = [x for x in skills[category] if not (x in seen or seen.add(x))]

    return dict(skills)


def extract_experience(text: str) -> List[Dict[str, str]]:
    """Extract projects with technologies used"""

    experience = []
    lines = [line.strip() for line in text.split('\n') if line.strip()]

    # Find experience section
    exp_section = []
    in_section = False

    for line in lines:
        if re.search(r'(?i)(experience|work history|employment|history|employment history)', line):
            in_section = True
            continue
        if in_section:
            if re.search(r'(?i)(education|skills|projects)', line):
                break
            exp_section.append(line)

    # If no section found, scan entire text
    if not exp_section:
        exp_section = lines

    # Parse experience entries
    current_entry = ""
    for line in exp_section:
        if re.search(r'(?i)\b(at|in)\b', line) and re.search(r'20\d{2}', line):
            if current_entry:
                experience.append(parse_experience_entry(current_entry))
                current_entry = ""
            current_entry = line
        elif current_entry:
            current_entry += " " + line

    if current_entry:
        experience.append(parse_experience_entry(current_entry))

    """ ======================== """
    # Try dedicated projects section first
    project_section = extract_section(text, r'(?i)(projects|portfolio|personal work)')
    if project_section:
        entries = re.split(r'\n(?=\s*(?:[A-Z]|•|\-|\d))', project_section)

        for entry in entries:
            if not entry.strip():
                continue

            # Split project name and description
            if ':' in entry:
                parts = entry.split(':', 1)
                project_name = parts[0].strip()
                description = parts[1].strip()
            elif '-' in entry:
                parts = entry.split('-', 1)
                project_name = parts[0].strip()
                description = parts[1].strip()
            else:
                project_name = entry.split(',')[0].strip()
                description = entry.strip()

            # Extract technologies
            tech_matches = re.findall(
                r'\b(Java|Python|JavaScript|TypeScript|C\+\+|C#|Ruby|PHP|'
                r'Swift|Kotlin|Go|Rust|Scala|R|Dart|Perl|HTML|CSS|Sass|'
                r'React|Angular|Vue|Django|Flask|Node\.?js|Express|Spring)\b',
                description,
                re.IGNORECASE
            )

            experience.append({
                'name': project_name,
                'description': description,
                'technologies': list(set(tech_matches))  # Remove duplicates
            })

    # Fallback to scanning entire text
    if not experience:
        lines = [line.strip() for line in text.split('\n') if line.strip()]
        project_keywords = [
            'developed', 'created', 'built', 'designed',
            'project', 'system', 'application', 'website',
            'app', 'software', 'platform'
        ]

        current_project = {}
        for line in lines:
            if any(keyword in line.lower() for keyword in project_keywords):
                if current_project:
                    experience.append(current_project)
                    current_project = {}

                if ':' in line:
                    parts = line.split(':', 1)
                    current_project = {
                        'name': parts[0].strip(),
                        'description': parts[1].strip(),
                        'technologies': []
                    }
                else:
                    current_project = {
                        'name': line.split(',')[0].strip(),
                        'description': line.strip(),
                        'technologies': []
                    }
            elif current_project:
                current_project['description'] += ' ' + line

        if current_project:
            experience.append(current_project)

    return experience


def parse_experience_entry(entry: str) -> Dict[str, str]:
    """Parse individual experience entry"""
    # Extract duration
    duration_match = re.search(r'(20\d{2}\s*[-–to]+\s*20\d{2}|20\d{2}\s*[-–]+\s*(?:Present|Now))', entry)
    duration = duration_match.group(0) if duration_match else ""

    # Extract position and company
    position_company = re.sub(r'\(.*?\)', '', entry)  # Remove duration
    parts = re.split(r'\bat\b|\bin\b', position_company, maxsplit=1)
    parts = [p.strip() for p in parts if p.strip()]

    return {
        'position': parts[0] if parts else "",
        'company': parts[1] if len(parts) > 1 else "",
        'duration': duration,
        'description': entry
    }


def extract_achievements(text: str) -> List[Dict[str, str]]:
    """Extract achievements/competitions"""
    achievements = []
    lines = [line.strip() for line in text.split('\n') if line.strip()]

    # Find achievements section
    achieve_section = []
    in_section = False

    for line in lines:
        if re.search(r'(?i)(achievements|competitions|awards)', line):
            in_section = True
            continue
        if in_section:
            if re.search(r'(?i)(education|experience|skills)', line):
                break
            achieve_section.append(line)

    # Parse achievement entries
    current_entry = ""
    for line in achieve_section:
        if re.search(r'\b\d+(?:st|nd|rd|th)\s+place\b', line, re.IGNORECASE):
            if current_entry:
                achievements.append(parse_achievement_entry(current_entry))
                current_entry = ""
            current_entry = line
        elif current_entry:
            current_entry += " " + line

    if current_entry:
        achievements.append(parse_achievement_entry(current_entry))

    return achievements


def parse_achievement_entry(entry: str) -> Dict[str, str]:
    """Parse individual achievement entry"""
    # Split into title and description
    parts = re.split(r'[:,]', entry, maxsplit=1)
    parts = [p.strip() for p in parts if p.strip()]

    return {
        'title': parts[0] if parts else "",
        'description': parts[1] if len(parts) > 1 else entry
    }


def extract_assets(text: str) -> List[Dict[str, str]]:
    """Extract assets/strengths"""
    assets = []
    lines = [line.strip() for line in text.split('\n') if line.strip()]

    # Find assets section
    assets_section = []
    in_section = False

    for line in lines:
        if re.search(r'(?i)(assets|strengths|attributes)', line):
            in_section = True
            continue
        if in_section:
            if re.search(r'(?i)(achievements|experience)', line):
                break
            assets_section.append(line)

    # Parse asset entries
    for line in assets_section:
        if ':' in line:
            parts = line.split(':', 1)
            assets.append({
                'type': parts[0].strip(),
                'description': parts[1].strip()
            })
        elif line:
            assets.append({
                'type': 'General',
                'description': line
            })

    return assets


def extract_references(text: str) -> List[Dict[str, str]]:
    """Extract references"""
    references = []
    lines = [line.strip() for line in text.split('\n') if line.strip()]

    # Find references section
    ref_section = []
    in_section = False

    for line in lines:
        if re.search(r'(?i)(references?|referees?)', line):
            in_section = True
            continue
        if in_section:
            ref_section.append(line)

    # Parse reference entries
    current_ref = ""
    for line in ref_section:
        if re.match(r'^[A-Z][a-z]+\s+[A-Z][a-z]+', line):
            if current_ref:
                references.append(parse_reference_entry(current_ref))
                current_ref = ""
            current_ref = line
        elif current_ref:
            current_ref += " " + line

    if current_ref:
        references.append(parse_reference_entry(current_ref))

    return references


def parse_reference_entry(entry: str) -> Dict[str, str]:
    """Parse individual reference entry"""
    # Extract name
    name_match = re.match(r'^([A-Z][a-z]+\s+[A-Z][a-z]+)', entry)
    name = name_match.group(1) if name_match else ""

    return {
        'name': name,
        'details': entry
    }