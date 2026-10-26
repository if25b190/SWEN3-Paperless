FROM python:3.13-slim-trixie

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    OMP_THREAD_LIMIT=1

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        tesseract-ocr tesseract-ocr-eng tesseract-ocr-deu poppler-utils \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 worker \
    && useradd --uid 10001 --gid worker --create-home worker \
    && mkdir -p /var/log/paperless-worker \
    && chown worker:worker /var/log/paperless-worker

WORKDIR /app
COPY requirements.txt ./
RUN pip install --requirement requirements.txt
COPY paperless_worker/ ./paperless_worker/
USER worker

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=4 \
    CMD ["python", "-m", "paperless_worker.health"]
CMD ["python", "-m", "paperless_worker"]
