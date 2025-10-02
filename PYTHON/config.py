import os


class Config:
    # Security
    SECRET_KEY = os.environ.get('JWT_SECRET_KEY', 'bnbewefiduidhwqudh21817161@@@@***')
    JWT_EXPIRATION_HOURS = int(os.environ.get('JWT_EXPIRATION_HOURS', 24))

    # File handling
    UPLOAD_FOLDER = 'uploads/'
    ALLOWED_EXTENSIONS = {'pdf', 'docx'}
    MAX_CONTENT_LENGTH = 500 * 1024 * 1024  # Increased to 500MB for bulk uploads
    USER_PDF_STORAGE_BASE = os.path.join(os.path.expanduser("~"), "python_cv_uploads")

    # Bulk processing limits - SIGNIFICANTLY INCREASED
    MAX_FILES_PER_UPLOAD = int(os.environ.get('MAX_FILES_PER_UPLOAD', 1000))  # Increased from 100
    BULK_PROCESSING_THRESHOLD = int(os.environ.get('BULK_PROCESSING_THRESHOLD', 100))  # Files above this go async
    MAX_CONCURRENT_EXTRACTIONS = int(os.environ.get('MAX_CONCURRENT_EXTRACTIONS', 8))  # Parallel CV processing
    BATCH_PROCESSING_TIMEOUT = int(os.environ.get('BATCH_PROCESSING_TIMEOUT', 1800))  # 30 minutes

    # Memory and performance
    REDIS_CONNECTION_POOL_SIZE = int(os.environ.get('REDIS_CONNECTION_POOL_SIZE', 20))
    CV_PROCESSING_CHUNK_SIZE = int(os.environ.get('CV_PROCESSING_CHUNK_SIZE', 50))  # Process in chunks
    BATCH_RESULT_TTL = int(os.environ.get('BATCH_RESULT_TTL', 7200))  # 2 hours to store results

    # Redis configuration
    REDIS_HOST = os.environ.get('REDIS_HOST', 'localhost')
    REDIS_PORT = int(os.environ.get('REDIS_PORT', 6379))
    REDIS_DB = int(os.environ.get('REDIS_DB', 0))
    REDIS_MAX_CONNECTIONS = int(os.environ.get('REDIS_MAX_CONNECTIONS', 50))  # Connection pool

    # Pagination
    DEFAULT_PAGE_SIZE = int(os.environ.get('DEFAULT_PAGE_SIZE', 10))
    MAX_PAGE_SIZE = int(os.environ.get('MAX_PAGE_SIZE', 500))  # Increased for bulk operations

    # Monitoring and logging
    ENABLE_PROCESSING_METRICS = os.environ.get('ENABLE_PROCESSING_METRICS', 'True').lower() == 'true'
    LOG_PROCESSING_TIMES = os.environ.get('LOG_PROCESSING_TIMES', 'True').lower() == 'true'
    CLEANUP_OLD_BATCHES_HOURS = int(os.environ.get('CLEANUP_OLD_BATCHES_HOURS', 24))

    # Resource management
    MAX_MEMORY_USAGE_MB = int(os.environ.get('MAX_MEMORY_USAGE_MB', 4096))  # 4GB limit
    ENABLE_GARBAGE_COLLECTION = os.environ.get('ENABLE_GARBAGE_COLLECTION', 'True').lower() == 'true'

    @property
    def MAX_WORKERS(self):
        """Dynamic worker count based on system resources"""
        import multiprocessing
        cpu_count = multiprocessing.cpu_count()
        return min(self.MAX_CONCURRENT_EXTRACTIONS, max(2, cpu_count - 1))

    def validate_bulk_upload_request(self, file_count: int) -> tuple[bool, str]:
        """Validate if a bulk upload request can be processed"""
        if file_count <= 0:
            return False, "No files to process"

        if file_count > self.MAX_FILES_PER_UPLOAD:
            return False, f"Too many files. Maximum {self.MAX_FILES_PER_UPLOAD} allowed"

        # Estimate memory usage (rough estimate: 10MB per CV during processing)
        estimated_memory_mb = file_count * 10
        if estimated_memory_mb > self.MAX_MEMORY_USAGE_MB:
            return False, f"Estimated memory usage ({estimated_memory_mb}MB) exceeds limit ({self.MAX_MEMORY_USAGE_MB}MB)"

        return True, "Valid request"

    def get_processing_mode(self, file_count: int) -> str:
        """Determine processing mode based on file count"""
        if file_count <= self.BULK_PROCESSING_THRESHOLD:
            return "synchronous"
        else:
            return "asynchronous"