from dataclasses import dataclass
from datetime import datetime


@dataclass
class ProcessingJob:
    """Represents a CV processing job in the queue."""
    job_id: str
    batch_id: str
    filename: str
    temp_filepath: str
    user_upload_dir: str
    priority: int = 5
    created_at: datetime = None
    attempts: int = 0
    max_attempts: int = 3

    def __post_init__(self):
        if self.created_at is None:
            self.created_at = datetime.now()
