# import re
# import spacy
# import logging
# import os
# from PyPDF2 import PdfReader
# from pdfminer.high_level import extract_text
# from typing import Dict, Any, List, Optional, Tuple
# from collections import defaultdict

# # Configure logging
# logging.basicConfig(level=logging.INFO)
# logger = logging.getLogger(__name__)

# # Load English language model for spaCy
# try:
#     nlp = spacy.load("en_core_web_sm")
# except OSError:
#     logger.error("spaCy English model not found. Please install it first.")
#     nlp = None

# def is_valid_cv(text: str) -> bool:
#     """
#     Determine if the extracted text resembles a CV/resume by checking for:
#     - Minimum length
#     - Presence of common CV sections
#     - Personal information patterns
#     """
#     if not text or len(text.strip()) < 150:  # Increased minimum length
#         logger.warning("Text too short to be a CV")
#         return False
    
#     # Score based on CV indicators
#     score = 0
    
#     # Check for common section headers
#     section_headers = [
#         r'(?i)\b(experience|work history|employment)\b',
#         r'(?i)\b(education|qualifications|academic)\b',
#         r'(?i)\b(skills|competencies|technical skills)\b',
#         r'(?i)\b(projects|portfolio)\b',
#         r'(?i)\b(certifications|licenses)\b'
#     ]
#     score += sum(1 for pattern in section_headers if re.search(pattern, text))
    
#     # Check for personal information patterns
#     personal_info_patterns = [
#         r'\b[A-Z][a-z]+\s+[A-Z][a-z]+\b',  # Name pattern
#         r'\b[\w\.-]+@[\w\.-]+\.\w+\b',     # Email
#         r'\+?\d[\d -]{8,}\d',              # Phone number
#         r'\d{1,5}\s+\w+\s+(?:street|st|avenue|ave|road|rd)\b'  # Address
#     ]
#     score += sum(1 for pattern in personal_info_patterns if re.search(pattern, text))
    
#     # Consider it a CV if we found at least 4 indicators
#     is_valid = score >= 4
#     if not is_valid:
#         logger.warning(f"Document doesn't appear to be a CV (score: {score}/7)")
#     return is_valid

# def extract_cv_info(pdf_path: str) -> Dict[str, Any]:
#     """
#     Main CV parsing function with comprehensive error handling.
#     Returns None if the file doesn't appear to be a valid CV.
#     """
#     try:
#         # Extract text with multiple fallbacks
#         text = extract_text_from_pdf(pdf_path)
#         if not text:
#             logger.error("Failed to extract text from PDF")
#             return None
            
#         # Validate it's a CV before processing
#         if not is_valid_cv(text):
#             return None
        
#         if not nlp:
#             logger.error("NLP model not available")
#             return None
            
#         doc = nlp(text)
        
#         # Extract all sections with enhanced parsers
#         result = {
#             'personal_info': extract_personal_info(text, doc),
#             'education': extract_education(text),
#             'experience': extract_experience_enhanced(text),
#             'skills': extract_skills_enhanced(text),
#             'languages': extract_languages(text),
#             'certifications': extract_certifications(text),
#             'projects': extract_projects_enhanced(text),
#             'achievements': extract_achievements_enhanced(text),
#             'assets': extract_assets(text),
#             'references': extract_references(text)
#         }
        
#         # Verify we extracted meaningful data
#         if not any(result.values()):
#             logger.warning("No meaningful data extracted")
#             return None
            
#         return result
        
#     except Exception as e:
#         logger.error(f"Error processing CV: {str(e)}", exc_info=True)
#         return None

# def extract_text_from_pdf(pdf_path: str) -> str:
#     """Robust text extraction from PDF with multiple fallbacks"""
#     text = ""
#     try:
#         # Try pdfminer first
#         text = extract_text(pdf_path)
#         if len(text.strip()) > 150:
#             return text
            
#         # Fallback to PyPDF2
#         reader = PdfReader(pdf_path)
#         text = "\n".join(page.extract_text() or "" for page in reader.pages)
            
#     except Exception as e:
#         logger.error(f"PDF text extraction failed: {str(e)}")
        
#     return text

# def extract_personal_info(text: str, doc) -> Dict[str, str]:
#     """Extract personal information with enhanced validation"""
#     info = {
#         'name': extract_name_enhanced(text, doc),
#         'email': extract_email(text),
#         'phone': extract_phone(text),
#         'address': extract_address(text),
#         'github': extract_github(text),
#         'linkedin': extract_linkedin(text),
#         'age': extract_age(text),
#         'nationality': extract_nationality(text)
#     }
#     return {k: v for k, v in info.items() if v}

# def extract_name_enhanced(text: str, doc) -> Optional[str]:
#     """Enhanced name extraction with multiple fallbacks"""
#     # Try to find name in first few lines
#     lines = [line.strip() for line in text.split('\n') if line.strip()]
    
#     # Score each line based on name-like characteristics
#     best_score = 0
#     best_name = None
    
#     for line in lines[:10]:  # Check more lines
#         # Skip lines that are clearly not names
#         if (len(line.split()) > 4 or 
#             re.search(r'@|http|\.com|\.org|skill|experience|resume|cv', line, re.IGNORECASE)):
#             continue
            
#         score = 0
        
#         # Title case with 2-4 words
#         if re.match(r'^[A-Z][a-z]+(?:\s+[A-Z][a-z]+){1,3}$', line):
#             score += 2
        
#         # Contains common name prefixes
#         if re.search(r'\b(Dr\.?|Mr\.?|Ms\.?|Mrs\.?|Prof\.?)\s+[A-Z][a-z]+', line):
#             score += 2
        
#         # Is a PERSON entity according to spaCy
#         if nlp:
#             for ent in doc.ents:
#                 if ent.text == line and ent.label_ == "PERSON":
#                     score += 1
#                     break
        
#         # Line position weighting (earlier lines more likely to be name)
#         position_weight = max(0, (10 - lines.index(line)) / 10)
#         score += position_weight
        
#         if score > best_score:
#             best_score = score
#             best_name = line
    
#     if best_name:
#         # Clean up name (remove titles, extra spaces)
#         clean_name = re.sub(r'\b(Dr\.?|Mr\.?|Ms\.?|Mrs\.?|Prof\.?)\s*', '', best_name).strip()
#         return clean_name if clean_name else None
    
#     return None

# def extract_email(text: str) -> Optional[str]:
#     """Extract email address with validation"""
#     email_pattern = r'\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,7}\b'
#     match = re.search(email_pattern, text)
#     return match.group(0) if match else None

# def extract_phone(text: str) -> Optional[str]:
#     """Extract phone number with international support"""
#     phone_patterns = [
#         r'\+?\d[\d -]{8,12}\d',  # International numbers
#         r'\(?\d{3}\)?[-.\s]?\d{3}[-.\s]?\d{4}',  # US numbers
#         r'\b\d{4}[-.\s]?\d{3}[-.\s]?\d{3}\b'  # Other formats
#     ]
#     for pattern in phone_patterns:
#         match = re.search(pattern, text)
#         if match:
#             # Clean up the phone number
#             phone = re.sub(r'[^\d+]', '', match.group(0))
#             return phone if len(phone) >= 8 else None
#     return None

# def extract_address(text: str) -> Optional[str]:
#     """Extract physical address"""
#     address_patterns = [
#         r'\d{1,5}\s[\w\s]{3,},\s[\w\s]{3,},\s[A-Z]{2}\s\d{5}',  # US format
#         r'\d{1,5}\s[\w\s]{3,}\s(?:street|st|avenue|ave|road|rd)[,\s]+[\w\s]+',  # International
#         r'[A-Z][a-z]+,\s*[A-Z][a-z]+,\s*\d{5}'  # City, State, ZIP
#     ]
#     for pattern in address_patterns:
#         match = re.search(pattern, text)
#         if match:
#             return match.group(0)
#     return None

# def extract_github(text: str) -> Optional[str]:
#     """Extract GitHub profile URL"""
#     patterns = [
#         r'github\.com/[a-zA-Z0-9-]{3,}',
#         r'github\.io/[a-zA-Z0-9-]{3,}'
#     ]
#     for pattern in patterns:
#         match = re.search(pattern, text)
#         if match:
#             return f"https://{match.group(0)}"
#     return None

# def extract_linkedin(text: str) -> Optional[str]:
#     """Extract LinkedIn profile URL"""
#     patterns = [
#         r'linkedin\.com/in/[a-zA-Z0-9-]{3,}',
#         r'linkedin\.com/company/[a-zA-Z0-9-]{3,}'
#     ]
#     for pattern in patterns:
#         match = re.search(pattern, text)
#         if match:
#             return f"https://{match.group(0)}"
#     return None

# def extract_age(text: str) -> Optional[str]:
#     """Extract age from text"""
#     age_match = re.search(r'(\d{2})\s*years?\s*old', text, re.IGNORECASE)
#     return age_match.group(1) if age_match else None

# def extract_nationality(text: str) -> Optional[str]:
#     """Extract nationality/citizenship"""
#     nation_match = re.search(
#         r'(?:nationality|citizen|citizenship):?\s*([A-Z][a-z]+(?:[\s-][A-Z][a-z]+)*)', 
#         text, 
#         re.IGNORECASE
#     )
#     return nation_match.group(1) if nation_match else None

# def extract_education(text: str) -> List[Dict[str, str]]:
#     """Extract education history with institution recognition"""
#     education = []
#     lines = [line.strip() for line in text.split('\n') if line.strip()]
    
#     # Find education section
#     edu_section = []
#     in_section = False
    
#     for i, line in enumerate(lines):
#         if re.search(r'(?i)(education|academic background|qualifications)', line):
#             in_section = True
#             # Include the next line if it looks like a continuation
#             if i+1 < len(lines) and not re.search(r':$', line):
#                 edu_section.append(lines[i+1])
#             continue
#         if in_section:
#             if re.search(r'(?i)(experience|work history|skills)', line):
#                 break
#             edu_section.append(line)
    
#     # If no section found, scan entire text
#     if not edu_section:
#         edu_section = lines
    
#     # Parse education entries
#     current_entry = ""
#     for line in edu_section:
#         # Look for degree patterns
#         degree_pattern = r'(?i)\b(B\.?S\.?|B\.?A\.?|Bachelor|M\.?S\.?|M\.?A\.?|Master|PhD|Ph\.?D\.?|Doctorate|Diploma|Certificate|HND)\b'
#         if re.search(degree_pattern, line):
#             if current_entry:
#                 education.append(parse_education_entry(current_entry))
#             current_entry = line
#         elif current_entry:
#             current_entry += " " + line
    
#     if current_entry:
#         education.append(parse_education_entry(current_entry))
    
#     return education

# def parse_education_entry(entry: str) -> Dict[str, str]:
#     """Parse individual education entry"""
#     # Extract year
#     year_match = re.search(
#         r'(?:20\d{2}\s*[-–to]+\s*20\d{2}|20\d{2}\s*[-–]+\s*(?:Present|Now)|20\d{2}|'
#         r'(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\s*\d{4}\s*[-–to]+\s*'
#         r'(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\s*\d{4})',
#         entry
#     )
#     year = year_match.group(0) if year_match else ""
    
#     # Extract degree and institution
#     parts = re.split(r'[,;]|\bat\b|\bfrom\b|\b-\b|\bin\b', entry)
#     parts = [p.strip() for p in parts if p.strip()]
    
#     return {
#         'degree': parts[0] if parts else "",
#         'institution': parts[1] if len(parts) > 1 else "",
#         'year': year,
#         'description': entry
#     }

# def extract_experience_enhanced(text: str) -> List[Dict[str, Any]]:
#     """Enhanced experience extraction that handles fragmented text"""
#     experience = []
#     current_job = None
#     collecting_description = False
#     bullet_points = []
    
#     # Normalize line breaks and split into lines
#     lines = [line.strip() for line in text.replace('\r', '\n').split('\n') if line.strip()]
    
#     for i, line in enumerate(lines):
#         # Skip section headers
#         if re.search(r'(?i)(experience|work history|employment)', line):
#             continue
            
#         # Detect job entry start (position at company with optional date)
#         job_match = re.search(
#             r'^(.*?)\s*(?:at|@)\s*(.*?)\s*([A-Z][a-z]+\s+\d{4}\s*[—\-]\s*[A-Z][a-z]+\s+\d{4}|Present|Current|\d{4}\s*-\s*\d{4})?$',
#             line
#         )
        
#         if job_match:
#             if current_job:  # Save previous job if exists
#                 if bullet_points:
#                     current_job['description'] = bullet_points
#                     bullet_points = []
#                 experience.append(current_job)
                
#             current_job = {
#                 'position': job_match.group(1).strip(),
#                 'company': job_match.group(2).strip(),
#                 'duration': job_match.group(3).strip() if job_match.group(3) else '',
#                 'description': []
#             }
#             collecting_description = True
            
#         # Collect bullet points or description lines
#         elif collecting_description and current_job:
#             if line.startswith(('•', '-', '·')) or (i > 0 and len(line.split()) < 8):
#                 # Bullet point or short line - likely part of description
#                 clean_line = re.sub(r'^[•\-·]\s*', '', line).strip()
#                 if clean_line:
#                     bullet_points.append(clean_line)
#             else:
#                 # Long line might be next section
#                 if bullet_points:
#                     current_job['description'] = bullet_points
#                     bullet_points = []
#                 collecting_description = False
    
#     # Add the last job if exists
#     if current_job:
#         if bullet_points:
#             current_job['description'] = bullet_points
#         experience.append(current_job)
    
#     return experience

# def extract_skills_enhanced(text: str) -> Dict[str, List[str]]:
#     """Enhanced skills extraction with better categorization"""
#     skills = defaultdict(list)
    
#     # Find skills section
#     skills_section = []
#     in_section = False
    
#     lines = [line.strip() for line in text.split('\n') if line.strip()]
#     for line in lines:
#         if re.search(r'(?i)(skills|technical skills|competencies)', line):
#             in_section = True
#             continue
#         if in_section:
#             if re.search(r'(?i)(experience|education|projects)', line):
#                 break
#             skills_section.append(line)
    
#     # If no section found, scan entire text
#     if not skills_section:
#         skills_section = lines
    
#     # Define skill categories and patterns
#     skill_categories = {
#         'programming': [
#             'java', 'python', 'javascript', 'typescript', 'c\\+\\+', 'c#', 'ruby', 'php',
#             'swift', 'kotlin', 'go', 'rust', 'scala', 'r\b', 'dart', 'perl'
#         ],
#         'web': [
#             'html', 'css', 'sass', 'less', 'bootstrap', 'tailwind', 'jquery',
#             'react', 'angular', 'vue', 'ember', 'svelte', 'next\\.js', 'nuxt\\.js'
#         ],
#         'mobile': [
#             'android', 'ios', 'flutter', 'react native', 'xamarin', 'swiftui'
#         ],
#         'databases': [
#             'mysql', 'postgresql', 'mongodb', 'sql\\s*server', 'oracle',
#             'sqlite', 'neo4j', 'cassandra', 'redis', 'dynamodb', 'firebase'
#         ],
#         'devops': [
#             'docker', 'kubernetes', 'aws', 'azure', 'gcp', 'jenkins',
#             'ansible', 'terraform', 'github\\s*actions', 'gitlab\\s*ci',
#             'circleci', 'prometheus', 'grafana'
#         ],
#         'data_science': [
#             'pandas', 'numpy', 'tensorflow', 'pytorch', 'scikit\\-learn',
#             'keras', 'spark', 'hadoop', 'tableau', 'power\\s*bi'
#         ],
#         'soft': [
#             'communication', 'teamwork', 'leadership', 'problem\\s*solving',
#             'time\\s*management', 'adaptability', 'creativity', 'critical\\s*thinking',
#             'negotiation', 'presentation', 'project\\s*management'
#         ]
#     }
    
#     skill_text = " ".join(skills_section).lower()
    
#     for category, patterns in skill_categories.items():
#         for pattern in patterns:
#             if re.search(rf'\b{pattern}\b', skill_text, re.IGNORECASE):
#                 skills[category].append(pattern.replace('\\', '').replace('-', ' ').title())
    
#     # Remove duplicates while preserving order
#     for category in skills:
#         seen = set()
#         skills[category] = [x for x in skills[category] if not (x in seen or seen.add(x))]
    
#     return dict(skills)

# def extract_languages(text: str) -> List[str]:
#     """Extract languages with proficiency levels if available"""
#     languages = []
#     common_languages = [
#         'english', 'spanish', 'french', 'german', 'chinese',
#         'hindi', 'arabic', 'portuguese', 'russian', 'japanese',
#         'dutch', 'italian', 'korean', 'flemish', 'sinhala', 'tamil'
#     ]
    
#     # Try dedicated languages section first
#     lang_section = extract_section(text, r'(?i)(languages?|language proficiency)')
#     if lang_section:
#         for line in lang_section.split('\n'):
#             line = line.strip()
#             if not line:
#                 continue
                
#             # Match language with optional proficiency
#             match = re.search(
#                 r'([A-Za-z]+)\s*(?:\(?(fluent|native|proficient|intermediate|basic|beginner)\)?)?',
#                 line, 
#                 re.IGNORECASE
#             )
#             if match:
#                 lang = match.group(1).lower()
#                 if lang in common_languages:
#                     proficiency = f" ({match.group(2).title()})" if match.group(2) else ""
#                     languages.append(f"{lang.capitalize()}{proficiency}")
    
#     # Fallback to scanning entire text
#     if not languages:
#         text_lower = text.lower()
#         for lang in common_languages:
#             if re.search(rf'\b{lang}\b', text_lower):
#                 languages.append(lang.capitalize())
    
#     return languages

# def extract_certifications(text: str) -> List[Dict[str, str]]:
#     """Extract certifications with issuing organization"""
#     certifications = []
    
#     # Try dedicated certifications section first
#     cert_section = extract_section(text, r'(?i)(certifications?|licenses?|credentials?)')
#     if cert_section:
#         entries = re.split(r'\n(?=\s*(?:[A-Z]|•|\-|\d))', cert_section)
        
#         for entry in entries:
#             if not entry.strip():
#                 continue
                
#             # Extract certification details
#             cert_match = re.search(
#                 r'^(.*?)\s*(?:\(|from|at|,|\-)\s*([A-Z][a-zA-Z\s&.,-]+?(?:'
#                 r'Institute|University|College|Academy|Foundation|Association|'
#                 r'Company|Inc\.?|Ltd\.?)|[A-Z][a-zA-Z\s&.,-]+)',
#                 entry
#             )
            
#             date_match = re.search(
#                 r'(?:20\d{2}\s*[-–to]+\s*20\d{2}|20\d{2}\s*[-–]+\s*'
#                 r'(?:Present|Now)|20\d{2}|since\s+20\d{2})',
#                 entry
#             )
            
#             if cert_match or date_match:
#                 cert_entry = {
#                     'name': cert_match.group(1).strip() if cert_match else entry.split(',')[0].strip(),
#                     'issuer': cert_match.group(2).strip() if cert_match and cert_match.group(2) else '',
#                     'date': date_match.group(0) if date_match else '',
#                     'description': entry.strip()
#                 }
#                 certifications.append(cert_entry)
    
#     # Fallback to scanning entire text
#     if not certifications:
#         lines = [line.strip() for line in text.split('\n') if line.strip()]
#         cert_keywords = [
#             'certified', 'certification', 'certificate', 
#             'license', 'credential', 'qualified as',
#             r'\b[A-Z]{3,}\b'  # Matches all-caps acronyms (like AWS, PMP)
#         ]
        
#         for line in lines:
#             if any(re.search(keyword, line, re.IGNORECASE) for keyword in cert_keywords):
#                 certifications.append({
#                     'name': line.split(',')[0].strip(),
#                     'issuer': '',
#                     'date': '',
#                     'description': line.strip()
#                 })
    
#     return certifications

# def extract_projects_enhanced(text: str) -> List[Dict[str, str]]:
#     """Extract projects with technologies used"""
#     projects = []
    
#     # Try dedicated projects section first
#     project_section = extract_section(text, r'(?i)(projects|portfolio|personal work)')
#     if project_section:
#         entries = re.split(r'\n(?=\s*(?:[A-Z]|•|\-|\d))', project_section)
        
#         for entry in entries:
#             if not entry.strip():
#                 continue
                
#             # Split project name and description
#             if ':' in entry:
#                 parts = entry.split(':', 1)
#                 project_name = parts[0].strip()
#                 description = parts[1].strip()
#             elif '-' in entry:
#                 parts = entry.split('-', 1)
#                 project_name = parts[0].strip()
#                 description = parts[1].strip()
#             else:
#                 project_name = entry.split(',')[0].strip()
#                 description = entry.strip()
            
#             # Extract technologies
#             tech_matches = re.findall(
#                 r'\b(Java|Python|JavaScript|TypeScript|C\+\+|C#|Ruby|PHP|'
#                 r'Swift|Kotlin|Go|Rust|Scala|R|Dart|Perl|HTML|CSS|Sass|'
#                 r'React|Angular|Vue|Django|Flask|Node\.?js|Express|Spring)\b',
#                 description, 
#                 re.IGNORECASE
#             )
            
#             projects.append({
#                 'name': project_name,
#                 'description': description,
#                 'technologies': list(set(tech_matches))  # Remove duplicates
#             })
    
#     # Fallback to scanning entire text
#     if not projects:
#         lines = [line.strip() for line in text.split('\n') if line.strip()]
#         project_keywords = [
#             'developed', 'created', 'built', 'designed', 
#             'project', 'system', 'application', 'website',
#             'app', 'software', 'platform'
#         ]
        
#         current_project = {}
#         for line in lines:
#             if any(keyword in line.lower() for keyword in project_keywords):
#                 if current_project:
#                     projects.append(current_project)
#                     current_project = {}
                
#                 if ':' in line:
#                     parts = line.split(':', 1)
#                     current_project = {
#                         'name': parts[0].strip(),
#                         'description': parts[1].strip(),
#                         'technologies': []
#                     }
#                 else:
#                     current_project = {
#                         'name': line.split(',')[0].strip(),
#                         'description': line.strip(),
#                         'technologies': []
#                     }
#             elif current_project:
#                 current_project['description'] += ' ' + line
        
#         if current_project:
#             projects.append(current_project)
    
#     return projects

# def extract_achievements_enhanced(text: str) -> List[Dict[str, str]]:
#     """Extract achievements and awards with competition results"""
#     achievements = []
    
#     # Try dedicated achievements section first
#     achieve_section = extract_section(text, r'(?i)(achievements|awards|honors)')
#     if achieve_section:
#         entries = re.split(r'\n(?=\s*(?:[A-Z]|•|\-|\d))', achieve_section)
        
#         for entry in entries:
#             if not entry.strip():
#                 continue
                
#             # Split achievement title and description
#             if ':' in entry:
#                 parts = entry.split(':', 1)
#                 title = parts[0].strip()
#                 description = parts[1].strip()
#             else:
#                 title = entry.split(',')[0].strip()
#                 description = entry.strip()
            
#             achievements.append({
#                 'title': title,
#                 'description': description
#             })
    
#     # Fallback to scanning for competition results
#     if not achievements:
#         lines = [line.strip() for line in text.split('\n') if line.strip()]
#         for line in lines:
#             if re.search(r'\b\d+(?:st|nd|rd|th)\s+place\b', line, re.IGNORECASE):
#                 achievements.append({
#                     'title': line.split(',')[0].strip(),
#                     'description': line.strip()
#                 })
    
#     return achievements

# def extract_assets(text: str) -> List[Dict[str, str]]:
#     """Extract personal assets/strengths"""
#     assets = []
    
#     # Try dedicated assets section first
#     assets_section = extract_section(text, r'(?i)(assets|strengths|attributes)')
#     if assets_section:
#         entries = re.split(r'\n(?=\s*(?:[A-Z]|•|\-|\d))', assets_section)
        
#         for entry in entries:
#             if not entry.strip():
#                 continue
                
#             if ':' in entry:
#                 parts = entry.split(':', 1)
#                 asset_type = parts[0].strip()
#                 description = parts[1].strip()
#             else:
#                 asset_type = 'General'
#                 description = entry.strip()
            
#             assets.append({
#                 'type': asset_type,
#                 'description': description
#             })
    
#     return assets

# def extract_references(text: str) -> List[Dict[str, str]]:
#     """Extract professional references"""
#     references = []
    
#     # Try dedicated references section first
#     ref_section = extract_section(text, r'(?i)(references?|referees?)')
#     if ref_section:
#         entries = re.split(r'\n(?=\s*(?:[A-Z][a-z]+ [A-Z][a-z]+))', ref_section)
        
#         for entry in entries:
#             if not entry.strip():
#                 continue
                
#             # Extract reference name and details
#             name_match = re.match(r'^([A-Z][a-z]+ [A-Z][a-z]+)', entry)
#             if name_match:
#                 references.append({
#                     'name': name_match.group(1),
#                     'details': entry[len(name_match.group(1)):].strip()
#                 })
    
#     return references

# def extract_section(text: str, pattern: str) -> str:
#     """Helper to extract a section of text by heading"""
#     # Try with double line breaks first
#     match = re.search(
#         pattern + r'(.*?)(?:\n\s*\n|\Z)', 
#         text, 
#         re.DOTALL | re.IGNORECASE
#     )
    
#     if not match:
#         # Fallback to single line break
#         match = re.search(
#             pattern + r'(.*?)(?:\n|\Z)', 
#             text, 
#             re.DOTALL | re.IGNORECASE
#         )
    
#     return match.group(1).strip() if match else ''

# def handle_uploaded_cv(file_path: str) -> Dict[str, Any]:
#     """
#     Process an uploaded CV file with comprehensive error handling
#     Returns a dictionary with either the parsed data or error information
#     """
#     try:
#         # Validate file exists and is readable
#         if not os.path.exists(file_path):
#             return {"error": "File not found", "status": 404}
            
#         if os.path.getsize(file_path) == 0:
#             return {"error": "Empty file", "status": 400}
            
#         # Check file extension
#         if not file_path.lower().endswith('.pdf'):
#             return {"error": "Only PDF files are supported", "status": 400}
            
#         # Process the CV
#         cv_data = extract_cv_info(file_path)
        
#         if not cv_data:
#             return {"error": "The file doesn't appear to be a valid CV", "status": 400}
            
#         return {
#             "status": 200,
#             "data": cv_data,
#             "message": "CV parsed successfully"
#         }
        
#     except Exception as e:
#         logger.error(f"Error processing uploaded CV: {str(e)}", exc_info=True)
#         return {
#             "error": "Internal server error processing CV",
#             "status": 500,
#             "details": str(e)
#         }