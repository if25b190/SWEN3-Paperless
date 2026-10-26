import json
import unittest
from concurrent.futures import Future
from types import SimpleNamespace
from unittest.mock import Mock

import pika

from paperless_worker.config import Settings
from paperless_worker.consumer import DeliveryHandler
from paperless_worker.models import PermanentJobError, Result
from test_processing import JOB


class DeliveryTest(unittest.TestCase):
    def setUp(self):
        self.channel = Mock(is_open=True)
        self.handler = DeliveryHandler(self.channel, Settings())
        self.body = json.dumps(JOB.fields()).encode()

    def deliver(self, error=None, attempt=0):
        future = Future()
        if error:
            future.set_exception(error)
        else:
            future.set_result(Result.completed(JOB, "private extracted content"))
        self.handler.complete(JOB, self.body, SimpleNamespace(headers={"x-paperless-attempt": attempt}),
                              7, future)

    def test_result_publish_precedes_acknowledgement(self):
        self.deliver()
        calls = self.channel.method_calls
        self.assertEqual(["basic_publish", "basic_ack"], [call[0] for call in calls])
        self.assertEqual("COMPLETED", json.loads(calls[0].kwargs["body"])["status"])
        self.assertTrue(calls[0].kwargs["mandatory"])

    def test_unconfirmed_result_is_not_acknowledged(self):
        self.channel.basic_publish.side_effect = pika.exceptions.NackError([])
        with self.assertRaises(pika.exceptions.NackError):
            self.deliver()
        self.channel.basic_ack.assert_not_called()
        self.channel.basic_reject.assert_not_called()

    def test_temporary_failure_is_republished_before_ack(self):
        self.deliver(ConnectionError("private secret"))
        publish = self.channel.basic_publish.call_args.kwargs
        self.assertEqual("paperless.ocr.retry", publish["routing_key"])
        self.assertEqual(1, publish["properties"].headers["x-paperless-attempt"])
        self.assertEqual(self.body, publish["body"])
        self.assertEqual(["basic_publish", "basic_ack"], [c[0] for c in self.channel.method_calls])

    def test_permanent_failure_and_exhausted_retries_go_to_dead_letter(self):
        for error, attempt in [(PermanentJobError("bad document"), 0), (ConnectionError(), 2)]:
            with self.subTest(error=type(error)):
                self.channel.reset_mock()
                self.deliver(error, attempt)
                self.channel.basic_reject.assert_called_once_with(delivery_tag=7, requeue=False)
                self.channel.basic_ack.assert_not_called()
                result = json.loads(self.channel.basic_publish.call_args.kwargs["body"])
                self.assertEqual("FAILED", result["status"])
                self.assertNotIn("bad document", json.dumps(result))

    def test_shutdown_returns_request_to_broker(self):
        self.deliver(InterruptedError())
        self.channel.basic_nack.assert_called_once_with(delivery_tag=7, requeue=True)
        self.channel.basic_ack.assert_not_called()

    def test_closed_delivery_channel_is_never_acknowledged(self):
        self.channel.is_open = False
        self.deliver()
        self.channel.basic_ack.assert_not_called()
        self.channel.basic_publish.assert_not_called()
