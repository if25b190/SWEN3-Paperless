import io
import os
import subprocess
import tempfile
import warnings
from pathlib import Path
from threading import Event

import pytesseract
from PIL import Image, UnidentifiedImageError

from .config import Settings
from .models import PermanentJobError


class TextExtractor:
    def __init__(self, settings: Settings, stopping: Event):
        self.settings = settings
        self.stopping = stopping

    def extract(self, content: bytes, content_type: str) -> str:
        try:
            self._check_stopping()
            if content_type == "text/plain":
                return self._bounded(content.decode("utf-8-sig"))
            if content_type == "application/pdf":
                return self._pdf(content)
            if content_type in {"image/png", "image/jpeg", "image/tiff", "image/bmp", "image/webp"}:
                return self._image(content)
            raise PermanentJobError("Unsupported OCR content type")
        except InterruptedError:
            raise
        except (UnidentifiedImageError, Image.DecompressionBombError, Image.DecompressionBombWarning,
                UnicodeDecodeError, subprocess.CalledProcessError, subprocess.TimeoutExpired,
                pytesseract.TesseractError, RuntimeError, ValueError, EOFError, OSError) as error:
            raise PermanentJobError("Document could not be processed by OCR") from error

    def _image(self, content: bytes) -> str:
        with warnings.catch_warnings():
            warnings.simplefilter("error", Image.DecompressionBombWarning)
            with Image.open(io.BytesIO(content)) as image:
                pages = getattr(image, "n_frames", 1)
                self._check_pages(pages)
                text = []
                for page in range(pages):
                    self._check_stopping()
                    image.seek(page)
                    if image.width * image.height > 20_000_000:
                        raise PermanentJobError("Image exceeds the configured pixel limit")
                    with image.convert("RGB") as rgb:
                        text.append(self._recognize(rgb))
                    self._bounded("\n\n".join(text))
                return "\n\n".join(text)

    def _pdf(self, content: bytes) -> str:
        with tempfile.TemporaryDirectory(prefix="paperless-ocr-") as directory:
            source = Path(directory) / "source.pdf"
            source.write_bytes(content)
            info = subprocess.run(["pdfinfo", str(source)], check=True, capture_output=True, text=True,
                                  timeout=self.settings.page_timeout, env={**os.environ, "LC_ALL": "C"})
            pages = next((int(line.split(":", 1)[1]) for line in info.stdout.splitlines()
                          if line.startswith("Pages:")), 0)
            self._check_pages(pages)
            text = []
            for page in range(1, pages + 1):
                self._check_stopping()
                target = Path(directory) / "page"
                subprocess.run(["pdftoppm", "-f", str(page), "-singlefile", "-scale-to", "2400",
                                "-png", str(source), str(target)], check=True, capture_output=True,
                               timeout=self.settings.page_timeout)
                with Image.open(target.with_suffix(".png")) as image:
                    text.append(self._recognize(image))
                self._bounded("\n\n".join(text))
            return "\n\n".join(text)

    def _recognize(self, image: Image.Image) -> str:
        return pytesseract.image_to_string(image, lang=self.settings.languages,
                                          timeout=self.settings.page_timeout).strip()

    def _check_pages(self, pages: int) -> None:
        if pages < 1 or pages > self.settings.max_pages:
            raise PermanentJobError("Document exceeds the configured page limit")

    def _bounded(self, text: str) -> str:
        if len(text) > self.settings.max_text_chars:
            raise PermanentJobError("Extracted text exceeds the configured limit")
        return text

    def _check_stopping(self) -> None:
        if self.stopping.is_set():
            raise InterruptedError("Worker is shutting down")
