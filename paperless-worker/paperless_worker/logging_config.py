import json
import logging
import os
from datetime import datetime, timezone
from logging.handlers import RotatingFileHandler
from pathlib import Path
from uuid import uuid4


class JsonFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        if not hasattr(record, "event_id"):
            record.event_id = str(uuid4())
        event = {"timestamp": datetime.fromtimestamp(record.created, timezone.utc).isoformat(),
                 "event_id": record.event_id, "level": record.levelname,
                 "service": "paperless-worker", "event": record.getMessage()}
        for key in ("job_id", "document_id", "attempt", "exception_type"):
            if hasattr(record, key):
                event[key] = getattr(record, key)
        return json.dumps(event)


def configure() -> None:
    handler = logging.StreamHandler()
    handler.setFormatter(JsonFormatter())
    root = logging.getLogger()
    root.handlers = [handler]
    path = os.getenv("WORKER_LOG_PATH", "")
    if path:
        Path(path).parent.mkdir(parents=True, exist_ok=True)
        file_handler = RotatingFileHandler(path, maxBytes=5 * 1024 * 1024, backupCount=3, encoding="utf-8")
        file_handler.setFormatter(JsonFormatter())
        root.addHandler(file_handler)
    root.setLevel(logging.INFO)
    logging.getLogger("pika").setLevel(logging.CRITICAL)
    logging.getLogger("botocore").setLevel(logging.CRITICAL)
