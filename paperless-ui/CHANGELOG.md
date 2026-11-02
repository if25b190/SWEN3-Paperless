# Changelog

All notable changes to the Paperless frontend are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [0.1.1]

### Changed
- `DocumentItem` now mirrors the slim backend `DocumentResponse` (no `storage_key`,
  `ocr_content`, or `summary`); new `DocumentDetail` type carries the OCR text and AI summary
  returned by `GET /api/v1/documents/{id}` and is used by the document detail page.

## [0.1.0]

- initial version for the Paperless frontend
