"""Live RabbitMQ -> RustFS -> Tesseract -> result queue check using isolated resources.

Run from paperless-worker with the normal broker/storage environment variables.
Only the randomly named bucket, exchanges, and queues created here are removed.
"""
import io
import json
import os
import subprocess
import sys
import tempfile
import time
from dataclasses import replace
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from threading import Thread
from uuid import uuid4
from urllib.error import HTTPError
from urllib.request import Request, urlopen

import pika
from PIL import Image, ImageDraw, ImageFont

from paperless_worker.config import Settings
from paperless_worker.models import Job
from paperless_worker.indexing import ElasticsearchIndex
from paperless_worker.storage import DocumentObjects
from paperless_worker.topology import declare


def main():
    prefix = f"paperless-test-{uuid4().hex}"
    settings = replace(Settings.from_env(), s3_bucket=prefix, exchange=prefix,
                       queue=f"{prefix}.requests", routing_key="document.uploaded",
                       dead_letter_exchange=f"{prefix}.dlx", failed_queue=f"{prefix}.failed",
                       retry_queue=f"{prefix}.retry", result_queue=f"{prefix}.results",
                       languages="eng", retry_delay_ms=1000, elasticsearch_index=prefix)
    objects = DocumentObjects.connect(settings)
    objects.client.create_bucket(Bucket=settings.s3_bucket)
    connection = pika.BlockingConnection(pika.ConnectionParameters(
        settings.rabbit_host, settings.rabbit_port,
        credentials=pika.PlainCredentials(settings.rabbit_user, settings.rabbit_password), heartbeat=30,
    ))
    channel = connection.channel()
    declare(channel, settings)
    channel.confirm_delivery()
    environment = {**os.environ, "S3_BUCKET": settings.s3_bucket, "OCR_EXCHANGE": settings.exchange,
                   "OCR_QUEUE": settings.queue, "OCR_ROUTING_KEY": settings.routing_key,
                   "OCR_DEAD_LETTER_EXCHANGE": settings.dead_letter_exchange,
                   "OCR_FAILED_QUEUE": settings.failed_queue, "OCR_RETRY_QUEUE": settings.retry_queue,
                   "OCR_RESULT_QUEUE": settings.result_queue, "OCR_RESULT_ROUTING_KEY": settings.result_routing_key,
                   "OCR_LANGUAGES": "eng", "OCR_RETRY_DELAY_MS": str(settings.retry_delay_ms),
                   "ELASTICSEARCH_INDEX": settings.elasticsearch_index}
    log = tempfile.TemporaryFile(mode="w+")
    index_requests = []

    class IndexProxy(BaseHTTPRequestHandler):
        def do_PUT(self):
            body = self.rfile.read(int(self.headers.get("Content-Length", "0")))
            index_requests.append(self.path)
            if len(index_requests) == 1:
                self.send_response(503)
                self.end_headers()
                self.wfile.write(b'{"error":{"type":"unavailable_shards_exception"}}')
                return
            request = Request(settings.elasticsearch_url.rstrip("/") + self.path, data=body, method="PUT",
                              headers=ElasticsearchIndex(settings).headers)
            try:
                with urlopen(request, timeout=15) as response:
                    self.send_response(response.status)
                    self.end_headers()
                    self.wfile.write(response.read())
            except HTTPError as error:
                self.send_response(error.code)
                self.end_headers()
                self.wfile.write(error.read())

        def log_message(self, *_args):
            pass

    proxy = ThreadingHTTPServer(("127.0.0.1", 0), IndexProxy)
    proxy_thread = Thread(target=proxy.serve_forever, daemon=True)
    proxy_thread.start()
    environment["ELASTICSEARCH_URL"] = f"http://127.0.0.1:{proxy.server_port}"
    worker = subprocess.Popen([sys.executable, "-m", "paperless_worker"], env=environment,
                              stdout=log, stderr=subprocess.STDOUT)

    def receive(queue):
        deadline = time.monotonic() + 45
        while time.monotonic() < deadline:
            if worker.poll() is not None:
                raise AssertionError("Worker stopped before completing the smoke test")
            method, properties, body = channel.basic_get(queue=queue, auto_ack=False)
            if method is not None:
                channel.basic_ack(delivery_tag=method.delivery_tag)
                return body
            connection.sleep(0.2)
        raise AssertionError(f"Timed out waiting for {queue}")

    def publish(body, job_id=None):
        channel.basic_publish(exchange=settings.exchange, routing_key=settings.routing_key, body=body,
                              mandatory=True, properties=pika.BasicProperties(
                                  content_type="application/json", delivery_mode=2, message_id=job_id))

    def elasticsearch(method, path, data=None):
        headers = ElasticsearchIndex(settings).headers
        request = Request(settings.elasticsearch_url.rstrip("/") + path, method=method, headers=headers,
                          data=json.dumps(data).encode() if data is not None else None)
        with urlopen(request, timeout=15) as response:
            return json.load(response)

    try:
        image = Image.new("RGB", (1200, 250), "white")
        try:
            font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 55)
        except OSError:
            font = ImageFont.load_default(size=55)
        ImageDraw.Draw(image).text((50, 80), "Paperless invoice 12345", fill="black", font=font)
        pdf = io.BytesIO()
        image.save(pdf, format="PDF")
        job = Job(str(uuid4()), str(uuid4()), f"documents/{uuid4()}.pdf", "application/pdf")
        objects.client.put_object(Bucket=settings.s3_bucket, Key=job.storage_key,
                                  Body=pdf.getvalue(), ContentType=job.content_type)
        body = json.dumps(job.fields()).encode()
        publish(body, job.job_id)
        result = json.loads(receive(settings.result_queue))
        assert result["status"] == "COMPLETED", result["status"]
        assert "12345" in result["text"], "Expected text was not recognized"
        assert objects.checkpoint(job).text == result["text"]
        indexed = elasticsearch("GET", f"/{prefix}/_doc/{job.document_id}")
        assert indexed["_source"]["text"] == result["text"]

        publish(body, job.job_id)
        duplicate = json.loads(receive(settings.result_queue))
        assert duplicate == result, "Redelivery did not reuse the persisted OCR result"
        elasticsearch("POST", f"/{prefix}/_refresh")
        assert elasticsearch("GET", f"/{prefix}/_count")["count"] == 1
        matches = elasticsearch("POST", f"/{prefix}/_search", {"query": {"match": {"text": "invoice"}}})
        assert matches["hits"]["total"]["value"] == 1

        publish(b"malformed-json")
        assert receive(settings.failed_queue) == b"malformed-json"

        unsupported = replace(job, job_id=str(uuid4()), document_id=str(uuid4()), content_type="application/zip")
        publish(json.dumps(unsupported.fields()).encode(), unsupported.job_id)
        failure = json.loads(receive(settings.result_queue))
        assert failure["status"] == "FAILED"
        assert json.loads(receive(settings.failed_queue))["jobId"] == unsupported.job_id
        log.seek(0)
        events = [json.loads(line) for line in log.read().splitlines() if line.startswith("{")]
        completed = [event for event in events if event.get("event") == "ocrCompleted"
                     and event.get("job_id") == job.job_id]
        retried = [event for event in events if event.get("event") == "jobRetryScheduled"
                   and event.get("job_id") == job.job_id]
        assert len(completed) == 1 and retried, "Index outage repeated OCR or bypassed the retry queue"
        print("PASS: live PDF OCR, RustFS checkpoint, idempotent Elasticsearch indexing/search, "
              "indexing-outage recovery without repeated OCR, duplicate delivery, and failed-job routing")
    except BaseException:
        log.seek(0)
        print(log.read(), file=sys.stderr)
        raise
    finally:
        worker.terminate()
        try:
            worker.wait(timeout=75)
        except subprocess.TimeoutExpired:
            worker.kill()
            worker.wait()
        log.close()
        proxy.shutdown()
        proxy.server_close()
        proxy_thread.join()
        if channel.is_open:
            for queue in (settings.queue, settings.result_queue, settings.retry_queue, settings.failed_queue):
                channel.queue_delete(queue=queue)
            channel.exchange_delete(exchange=settings.exchange)
            channel.exchange_delete(exchange=settings.dead_letter_exchange)
        if connection.is_open:
            connection.close()
        for page in objects.client.get_paginator("list_objects_v2").paginate(Bucket=settings.s3_bucket):
            for entry in page.get("Contents", []):
                objects.client.delete_object(Bucket=settings.s3_bucket, Key=entry["Key"])
        objects.client.delete_bucket(Bucket=settings.s3_bucket)
        try:
            elasticsearch("DELETE", f"/{prefix}")
        except HTTPError as error:
            if error.code != 404:
                raise


if __name__ == "__main__":
    main()
