import logging

from .models import Job, Result

logger = logging.getLogger(__name__)


class Processor:
    def __init__(self, objects, extractor):
        self.objects = objects
        self.extractor = extractor

    def process(self, job: Job) -> Result:
        result = self.objects.checkpoint(job)
        if result is None:
            text = self.extractor.extract(self.objects.document(job), job.content_type)
            result = Result.completed(job, text)
            self.objects.save(result)
            logger.info("ocrCompleted", extra={"job_id": job.job_id, "document_id": job.document_id})
        else:
            logger.info("checkpointReused", extra={"job_id": job.job_id, "document_id": job.document_id})
        return result
