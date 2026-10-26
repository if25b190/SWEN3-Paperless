import base64
import json
import logging
import re
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urlsplit
from urllib.request import Request, urlopen

from .config import Settings
from .models import Result

logger = logging.getLogger(__name__)


class IndexingError(Exception):
    """An Elasticsearch failure; the durable OCR checkpoint remains available."""


class ElasticsearchIndex:
    def __init__(self, settings: Settings):
        endpoint = urlsplit(settings.elasticsearch_url)
        if endpoint.scheme not in {"http", "https"} or not endpoint.netloc or endpoint.username is not None:
            raise ValueError("ELASTICSEARCH_URL must be an HTTP(S) endpoint without embedded credentials")
        if not re.fullmatch(r"[a-z0-9][a-z0-9_-]{0,254}", settings.elasticsearch_index):
            raise ValueError("ELASTICSEARCH_INDEX must be a valid lowercase index name")
        self.url = settings.elasticsearch_url.rstrip("/")
        self.index = quote(settings.elasticsearch_index, safe="")
        self.headers = {"Content-Type": "application/json"}
        if settings.elasticsearch_user:
            credentials = f"{settings.elasticsearch_user}:{settings.elasticsearch_password}".encode()
            self.headers["Authorization"] = "Basic " + base64.b64encode(credentials).decode("ascii")
        self.ready = False

    def store(self, result: Result) -> None:
        try:
            self._ensure_index()
            document = {"job_id": result.job.job_id, "document_id": result.job.document_id,
                        "storage_key": result.job.storage_key, "content_type": result.job.content_type,
                        "text": result.text, "extracted_at": result.extracted_at}
            path = f"/{self.index}/_doc/{quote(result.job.document_id, safe='')}"
            try:
                self._request("PUT", path, document)
            except HTTPError as error:
                if error.code != 404:
                    raise
                self.ready = False
                self._ensure_index()
                self._request("PUT", path, document)
        except (HTTPError, URLError, TimeoutError, OSError, ValueError) as error:
            raise IndexingError("Extracted text could not be indexed.") from error
        logger.info("textIndexed", extra={"job_id": result.job.job_id, "document_id": result.job.document_id})

    def _ensure_index(self) -> None:
        if self.ready:
            return
        mapping = {
            "settings": {"number_of_shards": 1, "number_of_replicas": 0},
            "mappings": {"dynamic": "strict", "properties": {
                "job_id": {"type": "keyword"}, "document_id": {"type": "keyword"},
                "storage_key": {"type": "keyword"}, "content_type": {"type": "keyword"},
                "text": {"type": "text"}, "extracted_at": {"type": "date"},
            }},
        }
        try:
            self._request("PUT", f"/{self.index}", mapping)
        except HTTPError as error:
            try:
                body = json.loads(error.read(65_536))
            except (ValueError, OSError):
                raise error
            if error.code != 400 or body.get("error", {}).get("type") != "resource_already_exists_exception":
                raise
        self.ready = True

    def _request(self, method: str, path: str, document: dict) -> None:
        request = Request(self.url + path, method=method, headers=self.headers,
                          data=json.dumps(document, ensure_ascii=False).encode("utf-8"))
        with urlopen(request, timeout=15) as response:
            response.read(65_536)
