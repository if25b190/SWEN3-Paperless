import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Settings:
    rabbit_host: str = "localhost"
    rabbit_port: int = 5672
    rabbit_user: str = "paperless"
    rabbit_password: str = "paperless_secure_password"
    exchange: str = "paperless.ocr"
    queue: str = "paperless.ocr.requests"
    routing_key: str = "document.uploaded"
    dead_letter_exchange: str = "paperless.ocr.dead-letter"
    failed_queue: str = "paperless.ocr.failed"
    retry_queue: str = "paperless.ocr.retry"
    result_queue: str = "paperless.ocr.results"
    result_routing_key: str = "document.ocr.result"
    retry_delay_ms: int = 10_000
    max_attempts: int = 3
    s3_endpoint: str = "http://localhost:9000"
    s3_bucket: str = "paperless-documents"
    s3_region: str = "us-east-1"
    s3_access_key: str = "paperless"
    s3_secret_key: str = "paperless_secure_password"
    languages: str = "eng+deu"
    page_timeout: int = 60
    max_document_bytes: int = 50 * 1024 * 1024
    max_pages: int = 100
    max_text_chars: int = 2_000_000
    elasticsearch_url: str = "http://localhost:9200"
    elasticsearch_index: str = "paperless-documents"
    elasticsearch_user: str = ""
    elasticsearch_password: str = ""

    @classmethod
    def from_env(cls) -> "Settings":
        mapping = {
            "rabbit_host": "RABBITMQ_HOST", "rabbit_port": "RABBITMQ_PORT",
            "rabbit_user": "RABBITMQ_USER", "rabbit_password": "RABBITMQ_PASS",
            "exchange": "OCR_EXCHANGE", "queue": "OCR_QUEUE", "routing_key": "OCR_ROUTING_KEY",
            "dead_letter_exchange": "OCR_DEAD_LETTER_EXCHANGE", "failed_queue": "OCR_FAILED_QUEUE",
            "retry_queue": "OCR_RETRY_QUEUE", "result_queue": "OCR_RESULT_QUEUE",
            "result_routing_key": "OCR_RESULT_ROUTING_KEY", "retry_delay_ms": "OCR_RETRY_DELAY_MS",
            "max_attempts": "OCR_MAX_ATTEMPTS", "s3_endpoint": "S3_ENDPOINT",
            "s3_bucket": "S3_BUCKET", "s3_region": "S3_REGION", "s3_access_key": "RUSTFS_ACCESS_KEY",
            "s3_secret_key": "RUSTFS_SECRET_KEY", "languages": "OCR_LANGUAGES",
            "page_timeout": "OCR_PAGE_TIMEOUT", "max_document_bytes": "OCR_MAX_DOCUMENT_BYTES",
            "max_pages": "OCR_MAX_PAGES", "max_text_chars": "OCR_MAX_TEXT_CHARS",
            "elasticsearch_url": "ELASTICSEARCH_URL", "elasticsearch_index": "ELASTICSEARCH_INDEX",
            "elasticsearch_user": "ELASTICSEARCH_USER", "elasticsearch_password": "ELASTICSEARCH_PASSWORD",
        }
        defaults = cls()
        values = {}
        for field, variable in mapping.items():
            default = getattr(defaults, field)
            value = os.getenv(variable, str(default))
            values[field] = int(value) if isinstance(default, int) else value
        settings = cls(**values)
        for field in ("rabbit_port", "retry_delay_ms", "max_attempts", "page_timeout",
                      "max_document_bytes", "max_pages", "max_text_chars"):
            if getattr(settings, field) <= 0:
                raise ValueError(f"{field} must be positive")
        return settings
