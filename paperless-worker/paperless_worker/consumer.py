import json
import logging
import signal
from concurrent.futures import ThreadPoolExecutor
from functools import partial
from threading import Event

import pika

from .config import Settings
from .health import HEARTBEAT
from .models import Job, PermanentJobError
from .topology import declare

logger = logging.getLogger(__name__)


class DeliveryHandler:
    def __init__(self, channel, settings: Settings):
        self.channel = channel
        self.settings = settings

    def complete(self, job: Job, body: bytes, properties, delivery_tag: int, future) -> None:
        if not self.channel.is_open:
            return
        try:
            result = future.result()
        except InterruptedError:
            self.channel.basic_nack(delivery_tag=delivery_tag, requeue=True)
            return
        except Exception as error:
            headers = properties.headers if isinstance(properties.headers, dict) else {}
            attempt = headers.get("x-paperless-attempt", 0)
            attempt = attempt if isinstance(attempt, int) and attempt >= 0 else 0
            if isinstance(error, PermanentJobError) or attempt + 1 >= self.settings.max_attempts:
                self._publish_result({**job.fields(), "status": "FAILED", "text": None,
                                      "errorCode": "OCR_PROCESSING_FAILED"}, job.job_id)
                self.channel.basic_reject(delivery_tag=delivery_tag, requeue=False)
                logger.warning("jobFailed", extra={"job_id": job.job_id, "document_id": job.document_id,
                                                   "exception_type": type(error).__name__})
            else:
                self.channel.basic_publish(
                    exchange="", routing_key=self.settings.retry_queue, body=body, mandatory=True,
                    properties=pika.BasicProperties(content_type="application/json", delivery_mode=2,
                                                    message_id=job.job_id,
                                                    headers={"x-paperless-attempt": attempt + 1}),
                )
                self.channel.basic_ack(delivery_tag=delivery_tag)
                logger.warning("jobRetryScheduled", extra={"job_id": job.job_id, "attempt": attempt + 1,
                                                           "exception_type": type(error).__name__})
            return
        self._publish_result(result.fields(), job.job_id)
        self.channel.basic_ack(delivery_tag=delivery_tag)
        logger.info("jobAcknowledged", extra={"job_id": job.job_id, "document_id": job.document_id})

    def _publish_result(self, result: dict, job_id: str) -> None:
        self.channel.basic_publish(
            exchange=self.settings.exchange, routing_key=self.settings.result_routing_key,
            body=json.dumps(result, ensure_ascii=False).encode("utf-8"), mandatory=True,
            properties=pika.BasicProperties(content_type="application/json", delivery_mode=2, message_id=job_id),
        )


def run(settings: Settings, processor, stopping: Event) -> None:
    connection = None
    channel = None

    def stop(_signum, _frame):
        stopping.set()
        if connection is not None and connection.is_open and channel is not None and channel.is_open:
            connection.add_callback_threadsafe(channel.stop_consuming)

    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)

    while not stopping.is_set():
        pool = ThreadPoolExecutor(max_workers=1, thread_name_prefix="ocr")
        try:
            connection = pika.BlockingConnection(pika.ConnectionParameters(
                host=settings.rabbit_host, port=settings.rabbit_port,
                credentials=pika.PlainCredentials(settings.rabbit_user, settings.rabbit_password),
                heartbeat=30, blocked_connection_timeout=15, socket_timeout=10, stack_timeout=20,
            ))
            channel = connection.channel()
            declare(channel, settings)
            channel.confirm_delivery()
            channel.basic_qos(prefetch_count=1)
            delivery = DeliveryHandler(channel, settings)

            def receive(current_channel, method, properties, body):
                try:
                    job = Job.from_bytes(body)
                except PermanentJobError:
                    current_channel.basic_reject(delivery_tag=method.delivery_tag, requeue=False)
                    logger.warning("invalidRequestRejected")
                    return
                logger.info("jobReceived", extra={"job_id": job.job_id, "document_id": job.document_id})
                future = pool.submit(processor.process, job)

                def completed(done, current_connection=connection, handler=delivery):
                    callback = partial(handler.complete, job, body, properties, method.delivery_tag, done)
                    try:
                        current_connection.add_callback_threadsafe(callback)
                    except pika.exceptions.ConnectionWrongStateError:
                        pass  # The broker will redeliver the unacknowledged request.

                future.add_done_callback(completed)

            def heartbeat(current_connection=connection):
                HEARTBEAT.touch()
                current_connection.call_later(10, heartbeat)

            heartbeat()
            channel.basic_consume(queue=settings.queue, on_message_callback=receive, auto_ack=False)
            logger.info("consumerStarted")
            channel.start_consuming()
        except (pika.exceptions.AMQPError, OSError) as error:
            logger.warning("brokerConnectionLost", extra={"exception_type": type(error).__name__})
        finally:
            HEARTBEAT.unlink(missing_ok=True)
            if connection is not None and connection.is_open:
                connection.close()
            pool.shutdown(wait=True, cancel_futures=True)
        stopping.wait(5)
