# ── Prod media bucket: the hand-built fitsync-media, adopted (prod only) ─────
# Prod serves exercise images and avatars from fitsync-media, created by hand in June 2026
# and holding about 1750 live objects. It is ADOPTED with the import blocks below and must
# never be recreated or emptied. force_destroy is never set and prevent_destroy refuses any
# plan that would delete it.
#
# Everything here exists only when var.prod_media_bucket is set, which only prod.tfvars
# does. Staging keeps creating its own bucket in storage.tf. As in cognito_prod.tf, the
# import blocks iterate over a set that is empty unless the variable is set.
#
# The arguments mirror the live bucket as `terraform plan -generate-config-out` read it on
# 2026-10-09, so the import changes nothing:
#   - bucket policy: public GetObject on every object (Sid PublicReadObjects); the SPA links
#     the images directly. CHANGED since the import: public read now covers exercises/ only,
#     and avatars/ is read through pre-signed URLs (media_avatars.tf). The first apply after
#     this change updates the policy in place.
#   - public access block: public ACLs blocked and ignored, public policies allowed. This is
#     stricter than the staging bucket, which also allows public ACLs.
#   - default encryption SSE-S3 (AES256), no bucket key; object ownership BucketOwnerEnforced
#     (ACLs disabled).
#   - Not managed because they do not exist on the bucket: versioning (never enabled),
#     lifecycle rules, CORS, website hosting, logging. Adding any of them is a deliberate
#     change for later.
#   - tags are ignored: the default tags would be the bucket's only change.

locals {
  adopt_media_bucket = var.prod_media_bucket != ""
  adopted_media      = local.adopt_media_bucket ? toset([var.prod_media_bucket]) : toset([])
}

import {
  for_each = local.adopted_media
  to       = aws_s3_bucket.media_prod[0]
  id       = each.value
}

import {
  for_each = local.adopted_media
  to       = aws_s3_bucket_public_access_block.media_prod[0]
  id       = each.value
}

import {
  for_each = local.adopted_media
  to       = aws_s3_bucket_policy.media_prod[0]
  id       = each.value
}

import {
  for_each = local.adopted_media
  to       = aws_s3_bucket_server_side_encryption_configuration.media_prod[0]
  id       = each.value
}

import {
  for_each = local.adopted_media
  to       = aws_s3_bucket_ownership_controls.media_prod[0]
  id       = each.value
}

resource "aws_s3_bucket" "media_prod" {
  count = local.adopt_media_bucket ? 1 : 0

  bucket              = var.prod_media_bucket
  object_lock_enabled = false

  lifecycle {
    prevent_destroy = true
    # force_destroy is left unset in state as the import writes it (null, read as false), so
    # the import plans no change; nothing here may ever set it to true.
    ignore_changes = [force_destroy, tags, tags_all]

    precondition {
      condition     = var.env == "prod"
      error_message = "prod_media_bucket adopts the prod media bucket and may only be set in the prod workspace."
    }
    precondition {
      condition     = var.existing_media_bucket == ""
      error_message = "Set either prod_media_bucket (adopt and manage) or existing_media_bucket (use unmanaged), not both."
    }
  }
}

resource "aws_s3_bucket_public_access_block" "media_prod" {
  count = local.adopt_media_bucket ? 1 : 0

  bucket                  = aws_s3_bucket.media_prod[0].id
  block_public_acls       = true
  ignore_public_acls      = true
  block_public_policy     = false
  restrict_public_buckets = false

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_s3_bucket_policy" "media_prod" {
  count = local.adopt_media_bucket ? 1 : 0

  bucket = aws_s3_bucket.media_prod[0].id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "PublicReadObjects"
      Effect    = "Allow"
      Principal = "*"
      Action    = "s3:GetObject"
      # Exercise images only; avatars/ is private (pre-signed reads, media_avatars.tf).
      Resource = "${aws_s3_bucket.media_prod[0].arn}/exercises/*"
    }]
  })

  # Deleting this policy would turn every image link into a 403.
  lifecycle {
    prevent_destroy = true
  }
  depends_on = [aws_s3_bucket_public_access_block.media_prod]
}

resource "aws_s3_bucket_server_side_encryption_configuration" "media_prod" {
  count = local.adopt_media_bucket ? 1 : 0

  bucket = aws_s3_bucket.media_prod[0].id
  rule {
    bucket_key_enabled = false
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_ownership_controls" "media_prod" {
  count = local.adopt_media_bucket ? 1 : 0

  bucket = aws_s3_bucket.media_prod[0].id
  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}
