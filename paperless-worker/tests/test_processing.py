import json
import unittest
from dataclasses import replace
from threading import Event
from unittest.mock import Mock

from paperless_worker.config import Settings
from paperless_worker.models import Job, PermanentJobError, Result
from paperless_worker.ocr import TextExtractor
from paperless_worker.processing import Processor

JOB = Job("00000000-0000-0000-0000-000000000001", "00000000-0000-0000-0000-000000000002",
          "documents/test.pdf", "application/pdf")


class ProcessingTest(unittest.TestCase):
    def test_java_message_contract_is_preserved(self):
        self.assertEqual(JOB, Job.from_bytes(json.dumps(JOB.fields()).encode()))

    def test_invalid_request_is_permanent(self):
        for body in (b"not-json", b"[]", b"null", b'{"jobId": 1}',
                     json.dumps({**JOB.fields(), "storageKey": "other/file"}).encode()):
            with self.subTest(body=body), self.assertRaises(PermanentJobError):
                Job.from_bytes(body)

    def test_ocr_is_checkpointed_before_success(self):
        objects, extractor = Mock(), Mock()
        objects.checkpoint.return_value = None
        objects.document.return_value = b"source"
        extractor.extract.return_value = "invoice content"
        result = Processor(objects, extractor).process(JOB)
        objects.save.assert_called_once_with(result)
        self.assertEqual("invoice content", result.text)

    def test_redelivery_reuses_checkpoint_without_fetching_or_ocr(self):
        objects, extractor = Mock(), Mock()
        objects.checkpoint.return_value = Result.completed(JOB, "already extracted")
        result = Processor(objects, extractor).process(JOB)
        objects.document.assert_not_called()
        objects.save.assert_not_called()
        extractor.extract.assert_not_called()
        self.assertEqual("already extracted", result.text)

    def test_failed_checkpoint_write_is_not_a_success(self):
        objects, extractor = Mock(), Mock()
        objects.checkpoint.return_value = None
        extractor.extract.return_value = "content"
        objects.save.side_effect = ConnectionError("storage unavailable")
        with self.assertRaises(ConnectionError):
            Processor(objects, extractor).process(JOB)

    def test_checkpoint_cannot_be_reused_for_another_document(self):
        result = Result.completed(JOB, "private content")
        other = replace(JOB, document_id="00000000-0000-0000-0000-000000000003")
        with self.assertRaises(PermanentJobError):
            Result.from_bytes(result.to_bytes(), other, 100)

    def test_plain_text_and_unsupported_content_type(self):
        extractor = TextExtractor(Settings(), Event())
        self.assertEqual("Hello", extractor.extract(b"Hello", "text/plain"))
        with self.assertRaises(PermanentJobError):
            extractor.extract(b"binary", "application/octet-stream")

    def test_text_limit_and_shutdown_are_enforced(self):
        stopping = Event()
        extractor = TextExtractor(replace(Settings(), max_text_chars=3), stopping)
        with self.assertRaises(PermanentJobError):
            extractor.extract(b"too long", "text/plain")
        stopping.set()
        with self.assertRaises(InterruptedError):
            extractor.extract(b"ok", "text/plain")
