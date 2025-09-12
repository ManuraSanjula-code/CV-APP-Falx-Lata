import spacy
from pdfminer.high_level import extract_text
from typing import Dict, Any, List, Optional
from collections import defaultdict
import docx

try:
    nlp = spacy.load("en_core_web_sm")
except OSError:
    print("Warning: spaCy 'en_core_web_sm' model not found. Name extraction might be less accurate.")
    nlp = None


def extract_cv_info(pdf_path: str) -> Dict[str, Any]:
    text = extract_text_from_file(pdf_path)
    doc = nlp(text) if nlp else None
    return {
        'raw_text': text,
        'personal_info': extract_personal_info(text, doc),
        'education': extract_education(text),
        'experience': extract_experience(text),
        'skills': extract_skills(text),
        'languages': extract_languages(text),
        'certifications': extract_certifications(text),
        'projects': extract_projects(text),
        'achievements': extract_achievements(text),
        'assets': extract_assets(text),
        'references': extract_references(text),
        'gender': None,  # Add this
        'type': None  # Add this
    }


def extract_section(text: str, section_headers_pattern: str) -> str:

    start_match = re.search(section_headers_pattern, text, re.IGNORECASE | re.MULTILINE)
    if not start_match:
        return ""

    header_end_pos = text.find('\n', start_match.end())
    if header_end_pos == -1:
        header_end_pos = start_match.end()
    else:
        header_end_pos += 1

    next_headers_pattern = (
        r"(?im)^\s*(Personal\s+Information|Contact\s+Details|Profile|"  # Before main sections
        r"Skills|Technical\s+Skills|Competencies|"  # Skills
        r"Education|Academic\s*(?:Background|Qualifications?)?|"  # Education
        r"(?:Work\s*)?Experience|(?:Employment|Professional)\s*History|"  # Experience
        r"Projects|Portfolio|Personal\s+Work|"  # Projects
        r"Languages?|"  # Languages
        r"Certifications?|Licenses?|Credentials?|"  # Certifications
        r"Achievements|Awards|Competitions|"  # Achievements
        r"Strengths|Assets|Attributes|"  # Assets
        r"References?|Referees?|"  # References
        r"Additional\s+Information"  # End-ish sections
        r")\s*$"
    )

    end_match = re.search(next_headers_pattern, text[header_end_pos:], re.MULTILINE)
    if end_match:
        section_content = text[header_end_pos: header_end_pos + end_match.start()]
    else:
        section_content = text[header_end_pos:]

    return section_content.strip()


def extract_text_from_pdf(pdf_path: str) -> str:
    try:
        text = extract_text(pdf_path)
        if len(text.strip()) > 100:
            return text
    except Exception as e:
        print(f"pdfminer extraction failed: {e}")
        pass
    return text

def extract_text_from_file(file_path: str) -> str:
    text = ""
    if file_path.lower().endswith('.pdf'):
        try:
            text = extract_text(file_path)
        except Exception as e:
            print(f"Error extracting text from PDF {file_path}: {e}")
    elif file_path.lower().endswith('.docx'):
        try:
            doc = docx.Document(file_path)
            text = '\n'.join([paragraph.text for paragraph in doc.paragraphs])
        except Exception as e:
            print(f"Error extracting text from DOCX {file_path}: {e}")
    else:
        print(f"Unsupported file type for text extraction: {file_path}")

    return text

def extract_personal_info(text: str, doc) -> Dict[str, str]:
    return {
        'name': extract_name(text, doc),
        'email': extract_email(text),
        'phone': extract_phone(text),
        'address': extract_address(text),
        'github': extract_github(text),
        'age': extract_age(text),
        'nationality': extract_nationality(text),
        'linkedin': extract_linkedin(text)
    }


def extract_name(text: str, doc) -> Optional[str]:
    lines = [line.strip() for line in text.split('\n') if line.strip()]

    if not lines:
        return None

    # --- Strategy 1: Handle the specific "spaced capital letter" name format ---
    # Check if the very first line is uppercase letters separated by spaces.
    first_line = lines[0]
    if re.match(r'^([A-Z]\s*)+$', first_line.strip()):
        # If the next line also looks like a plausible consolidated name, prefer it.
        # This handles the case where line 1 is "P A V..." and line 2 is "PAVILAKSHI VARATHARAJAN"
        if len(lines) > 1:
            potential_consolidated_name = lines[1]
            # Check if line 2 is a plausible name (2-6 words, letters/spaces, no obvious keywords)
            if (re.match(r'^[A-Za-z\s\'.]{4,100}$', potential_consolidated_name) and
                len(potential_consolidated_name.split()) >= 2 and len(potential_consolidated_name.split()) <= 6 and
                not any(char.isdigit() for char in potential_consolidated_name) and
                not re.search(r'(?i)\b(hr|generalist|manager|developer|engine|contact|summary|skills|education|experience|curriculum|vitae|resume|cv)\b|@', potential_consolidated_name)):
                return potential_consolidated_name.strip()
        # If no better consolidated name is found on line 2, return the spaced version from line 1
        # cleaned up (no trailing spaces).
        return " ".join(first_line.split()).strip()

    # --- Strategy 2: Standard name format on the first line ---
    # Check if the first line looks like a standard name (e.g., "KALINDU EDIRISINGHE")
    if (re.match(r'^[A-Za-z\s\'.]{3,100}$', first_line) and
        len(first_line.split()) >= 1 and len(first_line.split()) <= 5 and
        not any(char.isdigit() for char in first_line) and
        not re.search(r'(?i)\b(curriculum|vitae|resume|cv|email|phone|contact|address|linkedin|github|summary|skills|education|experience|profile|objective)\b|@', first_line)):
        return first_line.strip()

    # --- Strategy 3: Name on the second line after a header ---
    # Specific check for AEF31CD6...pdf pattern: Line 1 header, Line 2 name
    if len(lines) >= 2:
        line1 = lines[0]
        line2 = lines[1]
        if (re.search(r'(?i)(confidential|external|internal|private)', line1) and
            re.match(r'^[A-Za-z\s\'.]{5,150}$', line2) and
            len(line2.split()) >= 2 and len(line2.split()) <= 8 and
            not any(char.isdigit() for char in line2) and
            not re.search(r'@\w+|\.com|\.lk|www\.|github|linkedin', line2)):
            return line2.strip()

    # --- Strategy 4: Fallback to original spaCy NER logic (with better filtering) ---
    if doc:
        for ent in doc.ents:
            if ent.label_ == "PERSON":
                # Apply plausibility checks to filter poor spaCy matches
                if (len(ent.text.split()) >= 1 and len(ent.text.split()) <= 6 and
                    not any(char.isdigit() for char in ent.text) and
                    not re.search(r'(?i)\b(referee|reference|contact|email|phone|summary|skills|education|experience|profile|objective|linkedin|github)\b', ent.text) and
                    # Avoid names that are just locations (common misclassification)
                    not re.match(r'^[A-Z][a-z]+,\s*[A-Z][a-z]+$', ent.text.strip())):
                    return ent.text.strip()

    # --- Strategy 5: Fallback to original simple pattern (last resort) ---
    for line in lines[:8]:
        if not re.search(r'(@|\b(curriculum|vitae|resume|cv|skills|education|experience|summary|objective|profile|professional|contact|address)\b|\d{4}|^\d)', line, re.IGNORECASE):
            if re.match(r'^[A-Z][a-z]*(?:\s+[A-Z][a-z.\']*){0,5}$', line.strip()):
                return line.strip()

    # If all else fails
    return None


def extract_email(text: str) -> Optional[str]:
    email_pattern = r'\b[A-Za-z0-9.!#$%&\'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*\b'
    matches = re.findall(email_pattern, text)
    if matches:
        return matches[0]
    return None


def extract_phone(text: str) -> Optional[str]:
    phone_pattern = r'(?:\+\d{1,3}[-.\s]?)?\(?\d{1,4}\)?[-.\s]?\d{1,4}[-.\s]?\d{1,9}(?:\s?(?:ext|x|extension)\.?\s?\d+)?'
    matches = re.findall(phone_pattern, text)
    if matches:
        return max(matches, key=len)
    return None


def extract_address(text: str) -> Optional[str]:
    address_pattern = r'\d{1,5}\s+[A-Za-z0-9\s.]{3,}?\s*,\s*[A-Za-z\s.]{3,},?\s*(?:[A-Z]{2}|\w+),?\s*\d{5,6}(?:-\d{4})?'
    match = re.search(address_pattern, text)
    return match.group(0).strip() if match else None


def extract_github(text: str) -> Optional[str]:
    patterns = [
        r'(?:https?://)?(?:www\.)?github\.com/([a-zA-Z0-9\-_]+)',
        r'github\.com/([a-zA-Z0-9\-_]+)',
        r'@([a-zA-Z0-9\-_]+)',
    ]
    for pattern in patterns:
        match = re.search(pattern, text, re.IGNORECASE)
        if match:
            username = match.group(1).strip('/').strip()
            if len(username) <= 39:  # GitHub username limit
                return f"https://github.com/{username}"
    return None

def extract_age(text: str) -> Optional[str]:
    age_match = re.search(r'(?:age\s*:?\s*(\d{2})|\b(\d{2})\s*years?\s*old\b)', text, re.IGNORECASE)
    if age_match:
        return next((group for group in age_match.groups() if group), None)
    return None


def extract_nationality(text: str) -> Optional[str]:
    nation_match = re.search(r'(?:nationality|citizen(?:ship)?)\s*:?\s*([A-Z][a-z]+(?:\s+[A-Z][a-z]+)*)', text,
                             re.IGNORECASE)
    return nation_match.group(1).strip() if nation_match else None


import re
from typing import Optional

def extract_linkedin(text: str) -> Optional[str]:
    split_line_pattern = r'(?:https?://)?(?:www\.)?linkedin\.com/in/([a-zA-Z0-9\-_%]{2,100})[^\n]*\n\s*([a-zA-Z0-9\-_%]{2,100})'
    match = re.search(split_line_pattern, text, re.IGNORECASE)
    if match:
        part1 = match.group(1).rstrip('/.')
        part2 = match.group(2).rstrip('/.')
        # Combine the parts and standardize the URL
        full_username = f"{part1}{part2}"
        return f"https://www.linkedin.com/in/{full_username}"

    # --- Strategy 2: Standard pattern on a single line (or joined by normalization) ---
    # Normalize whitespace: Replace sequences of whitespace (including newlines) with a single space
    # This helps if the split was by a space character wrapping or other whitespace.
    normalized_text = re.sub(r'\s+', ' ', text)
    # Standard pattern to match LinkedIn URLs in the normalized text
    standard_pattern = r'(?:https?://)?(?:www\.)?linkedin\.com/(?:in/)?([a-zA-Z0-9\-_%]{2,100})/?'
    match = re.search(standard_pattern, normalized_text, re.IGNORECASE)
    if match:
        identifier_part = match.group(1).rstrip('/.')
        return f"https://www.linkedin.com/in/{identifier_part}"

    # --- Strategy 3: Basic pattern if normalization didn't work ---
    # Try the standard pattern directly on the original text as a last resort.
    basic_pattern = r'(?:https?://)?(?:www\.)?linkedin\.com/(?:in/)?([a-zA-Z0-9\-_%]{2,100})/?'
    match = re.search(basic_pattern, text, re.IGNORECASE)
    if match:
        identifier_part = match.group(1).rstrip('/.')
        return f"https://www.linkedin.com/in/{identifier_part}"

    # Return None if no LinkedIn URL is found
    return None

def extract_education(text: str) -> List[Dict[str, str]]:
    education = []
    edu_section_text = extract_section(text,
                                       r"(?im)^\s*(Education|Academic(?:\s+(?:Background|Qualifications?))?|University)\s*$")

    if edu_section_text:
        education.extend(parse_education_section(edu_section_text))
    else:
        education.extend(parse_education_section(text))

    return education


def parse_education_section(section_text: str) -> List[Dict[str, str]]:
    entries = []
    lines = [line.strip() for line in section_text.split('\n') if line.strip()]

    current_entry_lines = []
    for line in lines:
        if re.search(
                r'(?i)\b(B\.?A\.?|B\.?Sc\.?|B\.?S\.?|Bachelor|B\.?Tech\.?|M\.?A\.?|M\.?Sc\.?|M\.?S\.?|Master|Ph\.?D\.?|PhD|Doctorate|Diploma|Certificate|A-levels?|O-levels?|HND|Foundation|Associate)\b',
                line) or \
                re.search(r'\b(19|20)\d{2}\b', line) or \
                re.search(r'(University|College|Institute|School)', line, re.IGNORECASE):
            if current_entry_lines:
                parsed_entry = parse_single_education_entry("\n".join(current_entry_lines))
                if parsed_entry and (parsed_entry['degree'] or parsed_entry['institution']):
                    entries.append(parsed_entry)
                current_entry_lines = []
        current_entry_lines.append(line)

    if current_entry_lines:
        parsed_entry = parse_single_education_entry("\n".join(current_entry_lines))
        if parsed_entry and (parsed_entry['degree'] or parsed_entry['institution']):
            entries.append(parsed_entry)

    return entries


def parse_single_education_entry(entry_text: str) -> Dict[str, str]:
    entry_text = entry_text.strip()
    degree = ""
    institution = ""
    dates = ""
    description = entry_text

    date_pattern = r'((?:\d{1,2}[-/.])?\d{4})\s*(?:[-\u2013\u2014]|to)\s*((?:\d{1,2}[-/.])?(?:\d{4}|Present|Now))'
    date_match = re.search(date_pattern, entry_text)
    if date_match:
        dates = f"{date_match.group(1)} - {date_match.group(2)}"
        entry_without_dates = re.sub(date_pattern, '', entry_text, count=1).strip()
    else:
        year_matches = re.findall(r'\b((?:19|20)\d{2})\b', entry_text)
        if len(year_matches) >= 1:
            start_year = year_matches[0]
            end_year = year_matches[-1] if year_matches[-1] != start_year else ""
            dates = f"{start_year}" + (f" - {end_year}" if end_year else "")
            entry_without_dates = re.sub(r'\b((?:19|20)\d{2})\b', '', entry_text, count=len(year_matches)).strip()
        else:
            entry_without_dates = entry_text

    separator_match = re.search(r'\s*\b(at|in|from|,)\s*', entry_without_dates, re.IGNORECASE)
    if separator_match:
        before_sep = entry_without_dates[:separator_match.start()].strip()
        after_sep = entry_without_dates[separator_match.end():].strip()
        degree = before_sep
        institution = after_sep
    else:
        parts = [p.strip() for p in entry_without_dates.split(',') if p.strip()]
        if parts:
            degree = parts[0]
            institution = ", ".join(parts[1:]) if len(parts) > 1 else ""

    if degree and not institution:
        if re.search(r'(University|College|Institute|School)', degree, re.IGNORECASE):
            institution = degree
            degree = ""  # Reset degree if it was mistakenly assigned
    if institution and not degree:
        if re.search(
                r'(?i)\b(B\.?A\.?|B\.?Sc\.?|B\.?S\.?|Bachelor|M\.?A\.?|M\.?Sc\.?|M\.?S\.?|Master|Ph\.?D\.?|PhD|Doctorate|Diploma|Certificate|HND)\b',
                institution):
            inst_parts = institution.split()
            if len(inst_parts) > 1:
                potential_degree_word = inst_parts[0]
                if re.match(
                        r'(?i)\b(B\.?A\.?|B\.?Sc\.?|B\.?S\.?|Bachelor|M\.?A\.?|M\.?Sc\.?|M\.?S\.?|Master|Ph\.?D\.?|PhD|Doctorate|Diploma|Certificate|HND)\b',
                        potential_degree_word):
                    degree = potential_degree_word + " " + " ".join(inst_parts[1:])  # Reconstruct
                    institution = ""
                else:
                    degree = institution
                    institution = ""

    return {
        'degree': degree.strip(),
        'institution': institution.strip(),
        'dates': dates.strip(),
        'description': entry_text
    }


def extract_experience(text: str) -> List[Dict[str, str]]:
    experience = []
    exp_section_headers = r"(?im)^\s*((?:Work\s*)?Experience|(?:Employment|Professional)\s*History|Career\s*History|Work\s*History)\s*$"
    exp_section_text = extract_section(text, exp_section_headers)

    if exp_section_text:
        experience.extend(parse_experience_section(exp_section_text))
    else:
        experience.extend(parse_experience_section(text))

    return experience


def parse_experience_section(section_text: str) -> List[Dict[str, str]]:
    entries = []
    lines = [line.strip() for line in section_text.split('\n') if line.strip()]

    current_entry_lines = []
    for line in lines:
        if re.search(r'(?i)\b(at|in|for|with)\b', line) and \
                (re.search(r'\b(19|20)\d{2}\b', line) or
                 re.search(r'[A-Z][a-z]+(?:\s+[A-Z][a-z.]+)*', line)):
            if current_entry_lines:
                parsed_entry = parse_single_experience_entry("\n".join(current_entry_lines))
                if parsed_entry and (parsed_entry['position'] or parsed_entry['company']):
                    entries.append(parsed_entry)
                current_entry_lines = []
        current_entry_lines.append(line)

    # Process the last accumulated entry
    if current_entry_lines:
        parsed_entry = parse_single_experience_entry("\n".join(current_entry_lines))
        if parsed_entry and (parsed_entry['position'] or parsed_entry['company']):
            entries.append(parsed_entry)

    return entries


def parse_single_experience_entry(entry_text: str) -> Dict[str, str]:
    entry_text = entry_text.strip()
    position = ""
    company = ""
    dates = ""
    description = entry_text

    date_pattern = r'((?:\d{1,2}[-/.])?\d{4})\s*(?:[-\u2013\u2014]|to)\s*((?:\d{1,2}[-/.])?(?:\d{4}|Present|Now|Current))'
    date_match = re.search(date_pattern, entry_text)
    if date_match:
        dates = f"{date_match.group(1)} - {date_match.group(2)}"
        entry_without_dates = re.sub(date_pattern, '', entry_text, count=1).strip()
    else:
        year_matches = re.findall(r'\b((?:19|20)\d{2})\b', entry_text)
        if len(year_matches) >= 1:
            start_year = year_matches[0]
            end_year = year_matches[-1] if year_matches[-1] != start_year else ""
            dates = f"{start_year}" + (f" - {end_year}" if end_year else "")
            entry_without_dates = re.sub(r'\b((?:19|20)\d{2})\b', '', entry_text, count=len(year_matches)).strip()
        else:
            entry_without_dates = entry_text

    # Try to identify Position and Company
    separator_match = re.search(r'\s*\b(at|in|for|with)\b\s*', entry_without_dates, re.IGNORECASE)
    if separator_match:
        before_sep = entry_without_dates[:separator_match.start()].strip()
        after_sep = entry_without_dates[separator_match.end():].strip()
        position = before_sep
        company = after_sep
    else:
        parts = [p.strip() for p in entry_without_dates.split(',') if p.strip()]
        if parts:
            position = parts[0]
            company = ", ".join(parts[1:]) if len(parts) > 1 else ""

    return {
        'position': position.strip(),
        'company': company.strip(),
        'dates': dates.strip(),
        'description': entry_text
    }


# --- Skills ---
def extract_skills(text: str) -> Dict[str, List[str]]:
    """Enhanced skills extraction with better categorization"""
    skills = defaultdict(list)

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

    if not skills_section:
        skills_section = lines

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

    for category in skills:
        seen = set()
        skills[category] = [x for x in skills[category] if not (x in seen or seen.add(x))]

    return dict(skills)


def extract_languages(text: str) -> List[str]:
    languages = []
    lang_section_text = extract_section(text, r"(?im)^\s*(Languages?|Language\s+Proficiency)\s*$")
    if lang_section_text:
        lines = [line.strip() for line in lang_section_text.split('\n') if line.strip()]
        for line in lines:
            lang_matches = re.findall(r'\b([A-Z][a-z]{2,})\b', line)
            common_languages = {
                'english', 'spanish', 'french', 'german', 'chinese',
                'hindi', 'arabic', 'portuguese', 'russian', 'japanese',
                'dutch', 'italian', 'korean', 'flemish', 'sinhala', 'tamil',
                'telugu', 'punjabi', 'bengali', 'urdu', 'persian', 'turkish',
                'swedish', 'norwegian', 'danish', 'finnish', 'polish'
            }
            for match in lang_matches:
                lang_lower = match.lower()
                if lang_lower in common_languages:
                    languages.append(match)
    else:
        text_lower = text.lower()
        common_languages = {
            'english', 'spanish', 'french', 'german', 'chinese',
            'hindi', 'arabic', 'portuguese', 'russian', 'japanese',
            'dutch', 'italian', 'korean', 'flemish', 'sinhala', 'tamil',
            'telugu', 'punjabi', 'bengali', 'urdu', 'persian', 'turkish',
            'swedish', 'norwegian', 'danish', 'finnish', 'polish'
        }
        capitalized_words = re.findall(r'\b[A-Z][a-z]{2,}\b', text)
        for word in capitalized_words:
            if word.lower() in common_languages:
                languages.append(word)

    return list(dict.fromkeys(languages))


def extract_certifications(text: str) -> List[Dict[str, str]]:
    certifications = []
    cert_section_text = extract_section(text, r"(?im)^\s*(Certifications?|Licenses?|Credentials?)\s*$")
    text_to_parse = cert_section_text if cert_section_text else text

    lines = [line.strip() for line in text_to_parse.split('\n') if line.strip()]
    current_cert_lines = []
    for line in lines:
        if re.match(r'^(\s*[A-Z0-9•\-*]|\d+\.)', line):
            if current_cert_lines:
                parsed_cert = parse_single_certification("\n".join(current_cert_lines))
                if parsed_cert and parsed_cert['name']:
                    certifications.append(parsed_cert)
                current_cert_lines = []
        current_cert_lines.append(line)

    if current_cert_lines:
        parsed_cert = parse_single_certification("\n".join(current_cert_lines))
        if parsed_cert and parsed_cert['name']:
            certifications.append(parsed_cert)

    return certifications


def parse_single_certification(entry_text: str) -> Dict[str, str]:
    name = ""
    issuer = ""
    date = ""
    description = entry_text.strip()

    date_pattern = r'((?:\d{1,2}[-/.])?\d{4})'
    date_matches = re.findall(date_pattern, entry_text)
    if date_matches:
        date = date_matches[-1]

    lines = entry_text.split('\n')
    first_line = lines[0] if lines else entry_text
    name_match = re.search(r'^([^,(]+?)(?:\s*(?:\(|from|by|at|,)\s*(.+))?$', first_line)
    if name_match:
        name = name_match.group(1).strip()
        if name_match.group(2):
            issuer = name_match.group(2).strip().rstrip(')')

    if not name:
        acronym_match = re.search(r'\b([A-Z]{2,8})\b', first_line)
        if acronym_match:
            name = acronym_match.group(1)
        else:
            words = first_line.split()
            name = " ".join(words[:5])

    return {
        'name': name,
        'issuer': issuer,
        'date': date,
        'description': description
    }


def extract_projects(text: str) -> List[Dict[str, str]]:
    projects = []
    project_section_text = extract_section(text, r"(?im)^\s*(Projects|Portfolio|Personal\s+Work|Academic\s+Work)\s*$")
    text_to_parse = project_section_text if project_section_text else text

    lines = [line.strip() for line in text_to_parse.split('\n') if line.strip()]
    current_proj_lines = []
    for line in lines:
        if re.match(r'^(\s*[A-Z0-9•\-*]|\d+\.)', line):
            if current_proj_lines:
                parsed_proj = parse_single_project("\n".join(current_proj_lines))
                if parsed_proj and parsed_proj['name']:
                    projects.append(parsed_proj)
                current_proj_lines = []
        current_proj_lines.append(line)

    if current_proj_lines:
        parsed_proj = parse_single_project("\n".join(current_proj_lines))
        if parsed_proj and parsed_proj['name']:
            projects.append(parsed_proj)

    return projects


def parse_single_project(entry_text: str) -> Dict[str, str]:
    name = ""
    description = entry_text.strip()
    technologies = []

    lines = entry_text.split('\n')
    first_line = lines[0] if lines else entry_text

    if ':' in first_line:
        parts = first_line.split(':', 1)
        name = parts[0].strip()
        description = parts[1].strip() + "\n" + "\n".join(lines[1:])
    elif '-' in first_line and not first_line.startswith('-'):
        parts = first_line.split('-', 1)
        name = parts[0].strip()
        description = parts[1].strip() + "\n" + "\n".join(lines[1:])
    else:
        name = first_line.split(',')[0].strip()

    tech_pattern = r'\b(Java|Python|JavaScript|TypeScript|C\+\+|C#|Ruby|PHP|Swift|Kotlin|Go|Rust|Scala|R|Dart|Perl|HTML|CSS|Sass|Less|Bootstrap|Tailwind|jQuery|React|Angular|Vue|Ember|Svelte|Next\.js|Nuxt\.js|Django|Flask|Node\.?js|Express|Spring|Docker|Kubernetes|AWS|Azure|GCP|MySQL|PostgreSQL|MongoDB|Redis|TensorFlow|PyTorch)\b'
    tech_matches = re.findall(tech_pattern, description, re.IGNORECASE)
    seen = set()
    technologies = [re.sub(r'\\', '', match).replace('-', ' ').title() for match in tech_matches if
                    not (match.lower() in seen or seen.add(match.lower()))]

    return {
        'name': name,
        'description': description.strip(),
        'technologies': technologies
    }


def extract_achievements(text: str) -> List[Dict[str, str]]:
    achievements = []
    achieve_section_text = extract_section(text, r"(?im)^\s*(Achievements|Awards|Competitions|Honors?)\s*$")
    text_to_parse = achieve_section_text if achieve_section_text else text

    lines = [line.strip() for line in text_to_parse.split('\n') if line.strip()]
    current_achieve_lines = []
    for line in lines:
        if re.match(r'^(\s*[A-Z0-9•\-*]|\d+\.)', line):
            if current_achieve_lines:
                parsed_achieve = parse_single_achievement("\n".join(current_achieve_lines))
                if parsed_achieve and parsed_achieve['title']:
                    achievements.append(parsed_achieve)
                current_achieve_lines = []
        current_achieve_lines.append(line)

    if current_achieve_lines:
        parsed_achieve = parse_single_achievement("\n".join(current_achieve_lines))
        if parsed_achieve and parsed_achieve['title']:
            achievements.append(parsed_achieve)

    return achievements


def parse_single_achievement(entry_text: str) -> Dict[str, str]:
    lines = entry_text.split('\n')
    first_line = lines[0] if lines else entry_text
    title = first_line.strip()
    description = entry_text.strip()
    if ':' in first_line:
        parts = first_line.split(':', 1)
        title = parts[0].strip()
        description = parts[1].strip() + ("\n" + "\n".join(lines[1:]) if len(lines) > 1 else "")
    return {
        'title': title,
        'description': description
    }


def extract_assets(text: str) -> List[Dict[str, str]]:
    assets = []
    assets_section_text = extract_section(text, r"(?im)^\s*(Assets|Strengths|Attributes|Key\s+Strengths?)\s*$")
    text_to_parse = assets_section_text if assets_section_text else text

    lines = [line.strip() for line in text_to_parse.split('\n') if line.strip()]
    for line in lines:
        if ':' in line:
            parts = line.split(':', 1)
            assets.append({
                'type': parts[0].strip(),
                'description': parts[1].strip()
            })
        elif line and not re.match(r'^\s*(Assets|Strengths|Attributes)\s*$', line, re.IGNORECASE):
            assets.append({
                'type': 'General',
                'description': line
            })
    return assets


def extract_references(text: str) -> List[Dict[str, str]]:
    references = []
    ref_section_text = extract_section(text, r"(?im)^\s*(References?|Referees?)\s*$")
    text_to_parse = ref_section_text if ref_section_text else text

    lines = [line.strip() for line in text_to_parse.split('\n') if line.strip()]
    current_ref_lines = []
    for line in lines:
        if re.match(r'^[A-Z][a-z]+(?:\s+[A-Z][a-z.\']*)+', line):
            if current_ref_lines:
                parsed_ref = parse_single_reference("\n".join(current_ref_lines))
                if parsed_ref and parsed_ref['name']:
                    references.append(parsed_ref)
                current_ref_lines = []
        current_ref_lines.append(line)

    if current_ref_lines:
        parsed_ref = parse_single_reference("\n".join(current_ref_lines))
        if parsed_ref and parsed_ref['name']:
            references.append(parsed_ref)

    if not references:
        if re.search(r'(?i)available\s+upon\s+request', text_to_parse):
            references.append({'name': 'Available upon request', 'details': ''})

    return references


def parse_single_reference(entry_text: str) -> Dict[str, str]:
    lines = entry_text.strip().split('\n')
    name = ""
    details = entry_text.strip()
    if lines:
        name = lines[0].strip()
        details = "\n".join(lines[1:]).strip()
    return {
        'name': name,
        'details': details
    }