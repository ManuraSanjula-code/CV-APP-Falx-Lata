import os

class Config:
    SECRET_KEY = os.environ.get('JWT_SECRET_KEY', 'bnbewefiduidhwqudh21817161@@@@***')
    UPLOAD_FOLDER = 'uploads/'
    ALLOWED_EXTENSIONS = {'pdf', 'docx'}
    REDIS_HOST = os.environ.get('REDIS_HOST', 'localhost')
    REDIS_PORT = int(os.environ.get('REDIS_PORT', 6379))
    REDIS_DB = int(os.environ.get('REDIS_DB', 0))
    MAX_CONTENT_LENGTH = 100 * 1024 * 1024  # 100MB
    MAX_FILES_PER_UPLOAD = int(os.environ.get('MAX_FILES_PER_UPLOAD', 100))
    USER_PDF_STORAGE_BASE = os.path.join(os.path.expanduser("~"), "python_cv_uploads")

    JWT_EXPIRATION_HOURS = int(os.environ.get('JWT_EXPIRATION_HOURS', 24))

    DEFAULT_PAGE_SIZE = int(os.environ.get('DEFAULT_PAGE_SIZE', 10))
    MAX_PAGE_SIZE = int(os.environ.get('MAX_PAGE_SIZE', 100))
