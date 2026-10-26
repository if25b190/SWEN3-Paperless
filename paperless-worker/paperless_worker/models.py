import json
from dataclasses import dataclass
from datetime import datetime, timezone
from uuid import UUID


class PermanentJobError(Exception):
    """Invalid or unsupported input that will not improve with another delivery."""


@dataclass(frozen=True)
class Job:
    job_id: str
    document_id: str
    storage_key: str
    content_type: str

    @classmethod
    def from_bytes(cls, body: bytes) -> "Job":
        try:
            if len(body) > 65_536:
                raise ValueError("Request is too large")
            value = json.loads(body)
            fields = [value[key] for key in ("jobId", "documentId", "storageKey", "contentType")]
            if not all(isinstance(field, str) and field for field in fields):
                raise ValueError("Request fields must be nonempty strings")
            job_id, document_id, key, content_type = fields
            if not key.startswith("documents/") or len(key) > 1024:
                raise ValueError("Invalid document key")
            return cls(str(UUID(job_id)), str(UUID(document_id)), key, content_type.split(";")[0].lower())
        except (ValueError, TypeError, KeyError, UnicodeDecodeError) as error:
            raise PermanentJobError("Invalid OCR request") from error

    def fields(self) -> dict:
        return {"jobId": self.job_id, "documentId": self.document_id,
                "storageKey": self.storage_key, "contentType": self.content_type}


@dataclass(frozen=True)
class Result:
    job: Job
    text: str
    extracted_at: str

    @classmethod
    def completed(cls, job: Job, text: str) -> "Result":
        return cls(job, text, datetime.now(timezone.utc).isoformat())

    def fields(self) -> dict:
        return {**self.job.fields(), "status": "COMPLETED", "text": self.text,
                "extractedAt": self.extracted_at}

    def to_bytes(self) -> bytes:
        return json.dumps(self.fields(), ensure_ascii=False).encode("utf-8")

    @classmethod
    def from_bytes(cls, body: bytes, job: Job, max_text_chars: int) -> "Result":
        try:
            value = json.loads(body)
            if any(value[key] != field for key, field in job.fields().items()):
                raise ValueError("OCR checkpoint belongs to a different request")
            if value["status"] != "COMPLETED" or not isinstance(value["text"], str):
                raise ValueError("Invalid OCR checkpoint")
            if len(value["text"]) > max_text_chars or not isinstance(value["extractedAt"], str):
                raise ValueError("Invalid OCR checkpoint content")
            return cls(job, value["text"], value["extractedAt"])
        except (ValueError, TypeError, KeyError, UnicodeDecodeError) as error:
            raise PermanentJobError("Invalid OCR checkpoint") from error
