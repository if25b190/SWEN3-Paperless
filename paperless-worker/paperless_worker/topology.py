from .config import Settings


def declare(channel, settings: Settings) -> None:
    channel.exchange_declare(exchange=settings.exchange, exchange_type="direct", durable=True)
    channel.exchange_declare(exchange=settings.dead_letter_exchange, exchange_type="direct", durable=True)
    channel.queue_declare(queue=settings.failed_queue, durable=True)
    channel.queue_declare(queue=settings.queue, durable=True, arguments={
        "x-dead-letter-exchange": settings.dead_letter_exchange,
        "x-dead-letter-routing-key": settings.routing_key,
    })
    channel.queue_bind(queue=settings.queue, exchange=settings.exchange, routing_key=settings.routing_key)
    channel.queue_bind(queue=settings.failed_queue, exchange=settings.dead_letter_exchange,
                       routing_key=settings.routing_key)
    channel.queue_declare(queue=settings.result_queue, durable=True, arguments={
        "x-dead-letter-exchange": settings.dead_letter_exchange,
        "x-dead-letter-routing-key": settings.result_routing_key,
    })
    channel.queue_bind(queue=settings.result_queue, exchange=settings.exchange,
                       routing_key=settings.result_routing_key)
    channel.queue_bind(queue=settings.failed_queue, exchange=settings.dead_letter_exchange,
                       routing_key=settings.result_routing_key)
    channel.queue_declare(queue=settings.retry_queue, durable=True, arguments={
        "x-message-ttl": settings.retry_delay_ms,
        "x-dead-letter-exchange": settings.exchange,
        "x-dead-letter-routing-key": settings.routing_key,
    })
