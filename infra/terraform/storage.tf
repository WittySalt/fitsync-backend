# ── S3: exercise demo media (public read) ────────────────────────────────────
resource "aws_s3_bucket" "media" {
  bucket = "${local.name}-media-${local.account_id}"
  tags   = { Name = "${local.name}-media" }
}

resource "aws_s3_bucket_public_access_block" "media" {
  bucket                  = aws_s3_bucket.media.id
  block_public_acls       = false
  block_public_policy     = false
  ignore_public_acls      = false
  restrict_public_buckets = false
}

resource "aws_s3_bucket_policy" "media_public_read" {
  bucket = aws_s3_bucket.media.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "PublicRead"
      Effect    = "Allow"
      Principal = "*"
      Action    = "s3:GetObject"
      Resource  = "${aws_s3_bucket.media.arn}/*"
    }]
  })
  depends_on = [aws_s3_bucket_public_access_block.media]
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
