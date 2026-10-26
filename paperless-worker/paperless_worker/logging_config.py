import json
import logging
from datetime import datetime, timezone


class JsonFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        event = {"timestamp": datetime.now(timezone.utc).isoformat(), "level": record.levelname,
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
    root.setLevel(logging.INFO)
    logging.getLogger("pika").setLevel(logging.CRITICAL)
    logging.getLogger("botocore").setLevel(logging.CRITICAL)
