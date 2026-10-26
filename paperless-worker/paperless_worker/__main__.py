from threading import Event

from .config import Settings
from .consumer import run
from .logging_config import configure
from .indexing import ElasticsearchIndex
from .ocr import TextExtractor
from .processing import Processor
from .storage import DocumentObjects


def main() -> None:
    configure()
    settings = Settings.from_env()
    stopping = Event()
    objects = DocumentObjects.connect(settings)
    processor = Processor(objects, TextExtractor(settings, stopping), ElasticsearchIndex(settings))
    run(settings, processor, stopping)


if __name__ == "__main__":
    main()
