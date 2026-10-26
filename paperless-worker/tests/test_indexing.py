import json
import logging
import unittest
from dataclasses import replace
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from threading import Thread
from unittest.mock import Mock

from paperless_worker.config import Settings
from paperless_worker.indexing import ElasticsearchIndex, IndexingError
from paperless_worker.logging_config import JsonFormatter
from paperless_worker.models import Result
from paperless_worker.processing import Processor
from test_processing import JOB


class IndexingTest(unittest.TestCase):
    def setUp(self):
        self.requests = []
        self.responses = []
        requests, responses = self.requests, self.responses

        class Handler(BaseHTTPRequestHandler):
            def do_PUT(self):
                body = self.rfile.read(int(self.headers["Content-Length"]))
                requests.append((self.path, json.loads(body), self.headers))
                status, response = responses.pop(0) if responses else (201, {"result": "created"})
                self.send_response(status)
                self.send_header("Content-Type", "application/json")
                self.end_headers()
                self.wfile.write(json.dumps(response).encode())

            def log_message(self, *_args):
                pass

        self.server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.thread = Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.settings = replace(Settings(), elasticsearch_url=f"http://127.0.0.1:{self.server.server_port}")

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()

    def test_document_id_is_stable_and_extracted_text_is_searchable(self):
        index = ElasticsearchIndex(self.settings)
        result = Result.completed(JOB, "Rechnung Grüße 12345")
        index.store(result)
        index.store(result)
        self.assertEqual(3, len(self.requests))
        mapping = self.requests[0][1]
        self.assertEqual("text", mapping["mappings"]["properties"]["text"]["type"])
        expected = f"/paperless-documents/_doc/{JOB.document_id}"
        self.assertEqual([expected, expected], [request[0] for request in self.requests[1:]])
        self.assertEqual(result.text, self.requests[1][1]["text"])

    def test_existing_index_creation_race_is_accepted(self):
        self.responses.append((400, {"error": {"type": "resource_already_exists_exception"}}))
        ElasticsearchIndex(self.settings).store(Result.completed(JOB, "content"))
        self.assertEqual(2, len(self.requests))

    def test_unrelated_mapping_error_is_not_ignored(self):
        self.responses.append((400, {"error": {"type": "mapper_parsing_exception"}}))
        with self.assertRaises(IndexingError):
            ElasticsearchIndex(self.settings).store(Result.completed(JOB, "content"))
        self.assertEqual(1, len(self.requests))

    def test_outage_preserves_checkpoint_and_retry_does_not_repeat_ocr(self):
        objects, extractor, index = Mock(), Mock(), Mock()
        objects.checkpoint.return_value = None
        extractor.extract.return_value = "content"
        index.store.side_effect = [IndexingError("unavailable"), None]
        processor = Processor(objects, extractor, index)
        with self.assertRaises(IndexingError):
            processor.process(JOB)
        saved = objects.save.call_args.args[0]
        objects.checkpoint.return_value = saved
        self.assertEqual(saved, processor.process(JOB))
        extractor.extract.assert_called_once()
        objects.save.assert_called_once_with(saved)
        self.assertEqual(2, index.store.call_count)

    def test_deleted_index_is_recreated_before_retrying_write(self):
        self.responses.extend([(201, {}), (404, {"error": {"type": "index_not_found_exception"}}),
                               (201, {}), (201, {})])
        ElasticsearchIndex(self.settings).store(Result.completed(JOB, "content"))
        self.assertEqual(4, len(self.requests))

    def test_basic_auth_uses_header_and_credentials_are_not_in_url(self):
        settings = replace(self.settings, elasticsearch_user="elastic", elasticsearch_password="private")
        ElasticsearchIndex(settings).store(Result.completed(JOB, "content"))
        self.assertTrue(self.requests[0][2]["Authorization"].startswith("Basic "))
        self.assertNotIn("private", self.requests[0][0])

    def test_invalid_index_path_is_rejected_before_network_access(self):
        with self.assertRaises(ValueError):
            ElasticsearchIndex(replace(self.settings, elasticsearch_index="../other-index"))
        self.assertEqual([], self.requests)

    def test_json_logs_have_stable_ids_and_omit_sensitive_exception_data(self):
        record = logging.LogRecord("worker", logging.ERROR, "worker.py", 1, "jobFailed", (), None)
        record.job_id = JOB.job_id
        record.exception_type = "ConnectionError"
        record.secret = "private-password"
        formatter = JsonFormatter()
        stdout, logfile = formatter.format(record), formatter.format(record)
        self.assertEqual(json.loads(stdout)["event_id"], json.loads(logfile)["event_id"])
        self.assertNotIn("private-password", logfile)
        self.assertNotIn("Rechnung", logfile)
