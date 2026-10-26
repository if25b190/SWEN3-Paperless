import boto3
from botocore.config import Config
from botocore.exceptions import ClientError

from .config import Settings
from .models import Job, PermanentJobError, Result


class DocumentObjects:
    def __init__(self, client, settings: Settings):
        self.client = client
        self.settings = settings

    @classmethod
    def connect(cls, settings: Settings) -> "DocumentObjects":
        client = boto3.client(
            "s3", endpoint_url=settings.s3_endpoint, region_name=settings.s3_region,
            aws_access_key_id=settings.s3_access_key, aws_secret_access_key=settings.s3_secret_key,
            config=Config(s3={"addressing_style": "path"}, connect_timeout=5, read_timeout=30,
                          retries={"mode": "standard", "max_attempts": 2}),
        )
        return cls(client, settings)

    def document(self, job: Job) -> bytes:
        try:
            return self._read(job.storage_key, self.settings.max_document_bytes)
        except ClientError as error:
            if error.response["Error"]["Code"] in ("NoSuchKey", "404"):
                raise PermanentJobError("Source document no longer exists") from error
            raise

    def checkpoint(self, job: Job) -> Result | None:
        try:
            body = self._read(self._result_key(job), self.settings.max_text_chars * 6 + 65_536)
            return Result.from_bytes(body, job, self.settings.max_text_chars)
        except ClientError as error:
            if error.response["Error"]["Code"] in ("NoSuchKey", "404"):
                return None
            raise

    def save(self, result: Result) -> None:
        self.client.put_object(Bucket=self.settings.s3_bucket, Key=self._result_key(result.job),
                               Body=result.to_bytes(), ContentType="application/json")

    def _read(self, key: str, limit: int) -> bytes:
        response = self.client.get_object(Bucket=self.settings.s3_bucket, Key=key)
        with response["Body"] as stream:
            if response["ContentLength"] > limit:
                raise PermanentJobError("Object exceeds the configured size limit")
            content = stream.read(limit + 1)
        if len(content) > limit:
            raise PermanentJobError("Object exceeds the configured size limit")
        return content

    @staticmethod
    def _result_key(job: Job) -> str:
        return f"ocr-results/{job.job_id}.json"
