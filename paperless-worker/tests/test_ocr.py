import io
import shutil
import subprocess
import unittest
from dataclasses import replace
from threading import Event
from unittest.mock import patch

from PIL import Image, ImageDraw, ImageFont

from paperless_worker.config import Settings
from paperless_worker.models import PermanentJobError
from paperless_worker.ocr import TextExtractor


def ocr_available():
    if not shutil.which("tesseract") or not shutil.which("pdftoppm"):
        return False
    result = subprocess.run(["tesseract", "--list-langs"], capture_output=True, text=True, timeout=5)
    return "eng" in result.stdout.splitlines()


@unittest.skipUnless(ocr_available(), "Tesseract English data and Poppler required")
class RealOcrTest(unittest.TestCase):
    def setUp(self):
        self.extractor = TextExtractor(replace(Settings(), languages="eng"), Event())
        self.image = Image.new("RGB", (1200, 250), "white")
        font_path = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
        try:
            font = ImageFont.truetype(font_path, 55)
        except OSError:
            font = ImageFont.load_default(size=55)
        ImageDraw.Draw(self.image).text((50, 80), "Paperless invoice 12345", fill="black", font=font)

    def test_actual_image_ocr(self):
        content = io.BytesIO()
        self.image.save(content, format="PNG")
        self.assertIn("12345", self.extractor.extract(content.getvalue(), "image/png"))

    def test_actual_two_page_pdf_ocr(self):
        content = io.BytesIO()
        self.image.save(content, format="PDF", save_all=True, append_images=[self.image])
        text = self.extractor.extract(content.getvalue(), "application/pdf")
        self.assertEqual(2, text.count("12345"))

    def test_pdf_page_limit_is_checked_before_rendering(self):
        content = io.BytesIO()
        self.image.save(content, format="PDF", save_all=True, append_images=[self.image])
        extractor = TextExtractor(replace(Settings(), max_pages=1), Event())
        with self.assertRaises(PermanentJobError), patch.object(extractor, "_recognize") as recognize:
            extractor.extract(content.getvalue(), "application/pdf")
        recognize.assert_not_called()
