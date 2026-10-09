# ── Account-wide security settings (free or nearly free) ─────────────────────
# Both settings below belong to the account (and, for EBS, the region), not to an
# environment, so they live here and not in the staging or prod workspace.
#
#   - Default EBS encryption: every new volume in ap-southeast-1 is encrypted with the
#     AWS-managed key aws/ebs, whatever created it. Existing volumes are not touched; the app
#     hosts get encrypted root volumes through a planned rebuild (../RUNBOOK.md "Rebuild the app
#     host with an encrypted root volume"). Free.
#   - CloudTrail: one multi-region trail of management events with log file validation,
#     delivered to a private, encrypted bucket that expires logs after
#     var.cloudtrail_retention_days. The first copy of management events is free; the cost
#     is the S3 storage of the logs, a few cents a month at this account's volume. No data
#     events and no CloudWatch Logs delivery, both of which bill per event.

variable "cloudtrail_retention_days" {
  description = "Days the CloudTrail log files are kept in S3 before they expire"
  type        = number
  default     = 365

  validation {
    condition     = var.cloudtrail_retention_days >= 90
    error_message = "Keep CloudTrail logs at least 90 days (the event history already covers 90)."
  }
}

resource "aws_ebs_encryption_by_default" "this" {
  enabled = true
}

# ── CloudTrail ───────────────────────────────────────────────────────────────
locals {
  trail_name   = "fitsync-account-trail"
  trail_arn    = "arn:aws:cloudtrail:${var.region}:${local.account_id}:trail/${local.trail_name}"
  trail_bucket = "fitsync-cloudtrail-${local.account_id}"
}

resource "aws_s3_bucket" "cloudtrail" {
  bucket = local.trail_bucket

  # Audit logs are never emptied by Terraform; deleting the bucket means emptying it by hand.
  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_s3_bucket_public_access_block" "cloudtrail" {
  bucket                  = aws_s3_bucket.cloudtrail.id
  block_public_acls       = true
  ignore_public_acls      = true
  block_public_policy     = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "cloudtrail" {
  bucket = aws_s3_bucket.cloudtrail.id
  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

# SSE-S3: encrypted at rest without the monthly fee and per-request charges of a KMS key.
resource "aws_s3_bucket_server_side_encryption_configuration" "cloudtrail" {
  bucket = aws_s3_bucket.cloudtrail.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "cloudtrail" {
  bucket = aws_s3_bucket.cloudtrail.id

  rule {
    id     = "expire-trail-logs"
    status = "Enabled"
    filter {}

    expiration {
      days = var.cloudtrail_retention_days
    }
    abort_incomplete_multipart_upload {
      days_after_initiation = 7
    }
  }
}

# CloudTrail may write only into its own prefix, only for this trail, and with the bucket
# owner in full control. Every request must use TLS.
data "aws_iam_policy_document" "cloudtrail_bucket" {
  statement {
    sid       = "CloudTrailAclCheck"
    actions   = ["s3:GetBucketAcl"]
    resources = [aws_s3_bucket.cloudtrail.arn]
    principals {
      type        = "Service"
      identifiers = ["cloudtrail.amazonaws.com"]
    }
    condition {
      test     = "StringEquals"
      variable = "aws:SourceArn"
      values   = [local.trail_arn]
    }
  }

  statement {
    sid       = "CloudTrailWrite"
    actions   = ["s3:PutObject"]
    resources = ["${aws_s3_bucket.cloudtrail.arn}/AWSLogs/${local.account_id}/*"]
    principals {
      type        = "Service"
      identifiers = ["cloudtrail.amazonaws.com"]
    }
    condition {
      test     = "StringEquals"
      variable = "s3:x-amz-acl"
      values   = ["bucket-owner-full-control"]
    }
    condition {
      test     = "StringEquals"
      variable = "aws:SourceArn"
      values   = [local.trail_arn]
    }
  }

  statement {
    sid       = "DenyInsecureTransport"
    effect    = "Deny"
    actions   = ["s3:*"]
    resources = [aws_s3_bucket.cloudtrail.arn, "${aws_s3_bucket.cloudtrail.arn}/*"]
    principals {
      type        = "*"
      identifiers = ["*"]
    }
    condition {
      test     = "Bool"
      variable = "aws:SecureTransport"
      values   = ["false"]
    }
  }
}

resource "aws_s3_bucket_policy" "cloudtrail" {
  bucket     = aws_s3_bucket.cloudtrail.id
  policy     = data.aws_iam_policy_document.cloudtrail_bucket.json
  depends_on = [aws_s3_bucket_public_access_block.cloudtrail]
}

resource "aws_cloudtrail" "account" {
  name           = local.trail_name
  s3_bucket_name = aws_s3_bucket.cloudtrail.id

  is_multi_region_trail         = true
  include_global_service_events = true
  enable_log_file_validation    = true

  # Management events only (reads and writes), which is the free first copy.
  event_selector {
    read_write_type           = "All"
    include_management_events = true
  }

  # CloudTrail checks the bucket policy when the trail is created.
  depends_on = [aws_s3_bucket_policy.cloudtrail]
}

output "cloudtrail_bucket" {
  description = "Bucket that holds the CloudTrail log files (AWSLogs/<account>/CloudTrail/<region>/...)"
  value       = aws_s3_bucket.cloudtrail.bucket
}

output "ebs_encryption_by_default" {
  value = aws_ebs_encryption_by_default.this.enabled
}
