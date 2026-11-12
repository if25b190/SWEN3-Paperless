#!/bin/sh
set -eu

aws_s3() {
    aws --endpoint-url "$S3_ENDPOINT" "$@"
}

if ! aws_s3 s3api head-bucket --bucket "$S3_BUCKET" >/dev/null 2>&1; then
    aws_s3 s3api create-bucket --bucket "$S3_BUCKET" >/dev/null
fi

# Copy the previous flat filesystem layout without deleting the source files.
if [ "${MIGRATE_LEGACY_DOCUMENTS:-false}" = "true" ]; then
    aws_s3 s3 sync /legacy/ "s3://$S3_BUCKET/documents/" --no-progress --only-show-errors
fi
