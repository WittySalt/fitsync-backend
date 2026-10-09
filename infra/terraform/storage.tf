# ── S3: media (exercise images + avatars, public read) ───────────────────────
# Each environment writes to its own bucket. Staging gets a bucket made here; prod
# adopts the existing fitsync-media by import (var.prod_media_bucket, media_prod.tf).
# var.existing_media_bucket still names a bucket to use without managing it. The services
# upload with the EC2 instance role (s3:PutObject), and the account purge deletes avatars
# with it (s3:DeleteObject and s3:ListBucket, under avatars/ only).
locals {
  create_media_bucket = var.existing_media_bucket == "" && !local.adopt_media_bucket
  media_bucket = (local.create_media_bucket ? aws_s3_bucket.media[0].bucket :
  local.adopt_media_bucket ? aws_s3_bucket.media_prod[0].bucket : var.existing_media_bucket)
}

resource "aws_s3_bucket" "media" {
  count = local.create_media_bucket ? 1 : 0

  bucket = "${local.name}-media-${local.account_id}"
  tags   = { Name = "${local.name}-media" }
}

resource "aws_s3_bucket_public_access_block" "media" {
  count = local.create_media_bucket ? 1 : 0

  bucket                  = aws_s3_bucket.media[0].id
  block_public_acls       = false
  block_public_policy     = false
  ignore_public_acls      = false
  restrict_public_buckets = false
}

resource "aws_s3_bucket_policy" "media_public_read" {
  count = local.create_media_bucket ? 1 : 0

  bucket = aws_s3_bucket.media[0].id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "PublicRead"
      Effect    = "Allow"
      Principal = "*"
      Action    = "s3:GetObject"
      Resource  = "${aws_s3_bucket.media[0].arn}/*"
    }]
  })
  depends_on = [aws_s3_bucket_public_access_block.media]
}

data "aws_iam_policy_document" "ec2_media" {
  statement {
    sid       = "UploadMedia"
    actions   = ["s3:PutObject"]
    resources = ["arn:aws:s3:::${local.media_bucket}/*"]
  }

  # The account purge removes a user's avatars. user-service stores them at
  # avatars/<userId>/<uuid>.<ext>, so the purge lists avatars/<userId>/ and deletes what it
  # finds. Both rights stop at the avatars/ prefix: the exercise images stay undeletable.
  statement {
    sid       = "DeleteAvatars"
    actions   = ["s3:DeleteObject"]
    resources = ["arn:aws:s3:::${local.media_bucket}/avatars/*"]
  }

  statement {
    sid       = "ListAvatars"
    actions   = ["s3:ListBucket"]
    resources = ["arn:aws:s3:::${local.media_bucket}"]
    condition {
      test     = "StringLike"
      variable = "s3:prefix"
      values   = ["avatars/*"]
    }
  }
}

resource "aws_iam_role_policy" "ec2_media" {
  name   = "${local.name}-ec2-media"
  role   = aws_iam_role.ec2_app.id
  policy = data.aws_iam_policy_document.ec2_media.json
}

# ── Frontend SPA bucket + CloudFront MOVED to frontend/infra/terraform/ ───────
# These resources now live in the frontend repo's Terraform with their own state.
# The `removed` blocks below drop them from THIS state WITHOUT destroying the real
# AWS resources, so the frontend state can adopt them via `terraform import`
# (see frontend/infra/terraform/RUNBOOK.md). Delete these blocks once the
# migration apply has run and `terraform plan` is clean here.
removed {
  from = aws_s3_bucket.frontend
  lifecycle {
    destroy = false
  }
}

removed {
  from = aws_s3_bucket_website_configuration.frontend
  lifecycle {
    destroy = false
  }
}

removed {
  from = aws_s3_bucket_public_access_block.frontend
  lifecycle {
    destroy = false
  }
}

removed {
  from = aws_s3_bucket_policy.frontend_public_read
  lifecycle {
    destroy = false
  }
}

removed {
  from = aws_cloudfront_distribution.frontend
  lifecycle {
    destroy = false
  }
}
