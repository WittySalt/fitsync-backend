# ── Inbound mail: privacy@<domain> forwarded to the owner (account-wide) ────
# The privacy policy names privacy@<domain> as the contact for data requests, so mail sent
# there has to reach a person. SES receives it in this region, keeps the raw message in a
# private bucket for 30 days, and a small Lambda forwards a copy to var.privacy_forward_to:
#
#   sender ─MX→ inbound-smtp.<region>.amazonaws.com ─rule→ S3 (raw copy) ─→ Lambda ─SendRawEmail→ owner
#
# SES receiving is supported in ap-southeast-1, so everything stays in var.region.
# Only one receipt rule set can be active per region; the account had none, so this file
# creates fitsync-inbound and activates it. Add any future inbound rule to that set.
#
# The forward keeps the sender in Reply-To and sends From our own verified domain, because
# SES may only send from identities it has verified (and DMARC would reject a spoofed From).
# While the account is in the SES sandbox the destination must be a verified identity as
# well; it is created here only when var.privacy_forward_to_create_identity is true.
#
# Nothing here exists while notify_email_domain or privacy_forward_to is empty.
# Test and apply steps: RUNBOOK.md "Inbound mail (privacy@)".

variable "privacy_forward_to" {
  description = "Inbox that receives a copy of every mail sent to privacy@<notify_email_domain> (empty = no inbound mail)"
  type        = string
  # No default on purpose: an apply that forgets the value stops instead of tearing the
  # forwarder down. Pass it with -var or an untracked bootstrap/terraform.tfvars.

  validation {
    condition     = var.privacy_forward_to == "" || can(regex("^[^@[:space:];,]+@[^@[:space:];,]+[.][^@[:space:];,]+$", var.privacy_forward_to))
    error_message = "privacy_forward_to must be empty or one email address."
  }
}

variable "privacy_forward_to_create_identity" {
  description = "Create an SES email identity for privacy_forward_to (needed in the sandbox when the address is not verified yet; the owner clicks the link SES sends)"
  type        = bool
  default     = false
}

locals {
  privacy_inbound_enabled = local.notify_domain_enabled && var.privacy_forward_to != ""
  privacy_address         = "privacy@${var.notify_email_domain}"
  privacy_sender          = "noreply@${var.notify_email_domain}"
  privacy_rule_set        = "fitsync-inbound"
  privacy_rule            = "privacy-forward"
  privacy_rule_arn        = "arn:aws:ses:${var.region}:${local.account_id}:receipt-rule-set/${local.privacy_rule_set}:receipt-rule/${local.privacy_rule}"
  privacy_bucket          = "fitsync-privacy-inbound-${local.account_id}"
  privacy_prefix          = "privacy/"
  privacy_function        = "fitsync-privacy-forwarder"
}

# ── DNS: mail for the apex goes to SES ───────────────────────────────────────
# The apex had no MX before this (only the CloudFront aliases, NS and SOA). The MAIL FROM
# subdomain keeps its own MX to feedback-smtp in email.tf.
resource "aws_route53_record" "inbound_mx" {
  count = local.privacy_inbound_enabled ? 1 : 0

  zone_id = aws_route53_zone.notify[0].zone_id
  name    = var.notify_email_domain
  type    = "MX"
  ttl     = 1800
  records = ["10 inbound-smtp.${var.region}.amazonaws.com"]
}

# ── Destination identity (sandbox only) ──────────────────────────────────────
resource "aws_sesv2_email_identity" "privacy_forward_to" {
  count = local.privacy_inbound_enabled && var.privacy_forward_to_create_identity ? 1 : 0

  email_identity = var.privacy_forward_to
}

# ── Raw message store ────────────────────────────────────────────────────────
resource "aws_s3_bucket" "privacy_inbound" {
  count = local.privacy_inbound_enabled ? 1 : 0

  bucket = local.privacy_bucket
}

resource "aws_s3_bucket_ownership_controls" "privacy_inbound" {
  count = local.privacy_inbound_enabled ? 1 : 0

  bucket = aws_s3_bucket.privacy_inbound[0].id
  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_public_access_block" "privacy_inbound" {
  count = local.privacy_inbound_enabled ? 1 : 0

  bucket                  = aws_s3_bucket.privacy_inbound[0].id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "privacy_inbound" {
  count = local.privacy_inbound_enabled ? 1 : 0

  bucket = aws_s3_bucket.privacy_inbound[0].id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# Messages carry personal data; the forwarded copy is the working one, so the raw copy
# only needs to outlive a failed forward.
resource "aws_s3_bucket_lifecycle_configuration" "privacy_inbound" {
  count = local.privacy_inbound_enabled ? 1 : 0

  bucket = aws_s3_bucket.privacy_inbound[0].id
  rule {
    id     = "expire-after-30-days"
    status = "Enabled"
    filter {}
    expiration {
      days = 30
    }
    abort_incomplete_multipart_upload {
      days_after_initiation = 1
    }
  }
}

data "aws_iam_policy_document" "privacy_inbound_bucket" {
  count = local.privacy_inbound_enabled ? 1 : 0

  statement {
    sid       = "SesWritesFromThisRuleOnly"
    actions   = ["s3:PutObject"]
    resources = ["${aws_s3_bucket.privacy_inbound[0].arn}/${local.privacy_prefix}*"]
    principals {
      type        = "Service"
      identifiers = ["ses.amazonaws.com"]
    }
    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [local.account_id]
    }
    condition {
      test     = "ArnLike"
      variable = "aws:SourceArn"
      values   = [local.privacy_rule_arn]
    }
  }

  statement {
    sid     = "DenyInsecureTransport"
    effect  = "Deny"
    actions = ["s3:*"]
    resources = [
      aws_s3_bucket.privacy_inbound[0].arn,
      "${aws_s3_bucket.privacy_inbound[0].arn}/*",
    ]
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

resource "aws_s3_bucket_policy" "privacy_inbound" {
  count = local.privacy_inbound_enabled ? 1 : 0

  bucket = aws_s3_bucket.privacy_inbound[0].id
  policy = data.aws_iam_policy_document.privacy_inbound_bucket[0].json

  depends_on = [aws_s3_bucket_public_access_block.privacy_inbound]
}

# ── Forwarder Lambda ─────────────────────────────────────────────────────────
# Standard library and boto3 only, so the archive is the one source file. The zip is a
# build output under .build/ (gitignored).
data "archive_file" "privacy_forwarder" {
  type        = "zip"
  source_file = "${path.module}/lambda/privacy_forwarder/forwarder.py"
  output_path = "${path.module}/.build/privacy_forwarder.zip"
}

resource "aws_cloudwatch_log_group" "privacy_forwarder" {
  count = local.privacy_inbound_enabled ? 1 : 0

  name              = "/aws/lambda/${local.privacy_function}"
  retention_in_days = 30
}

data "aws_iam_policy_document" "privacy_forwarder_trust" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "privacy_forwarder" {
  count = local.privacy_inbound_enabled ? 1 : 0

  name               = local.privacy_function
  description        = "FitSync privacy@ forwarder Lambda: reads the stored message and re-sends it"
  assume_role_policy = data.aws_iam_policy_document.privacy_forwarder_trust.json
}

data "aws_iam_policy_document" "privacy_forwarder" {
  count = local.privacy_inbound_enabled ? 1 : 0

  statement {
    sid       = "ReadStoredMessages"
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.privacy_inbound[0].arn}/${local.privacy_prefix}*"]
  }

  # SES authorises SendRawEmail against the From identity and, in the sandbox, against every
  # recipient identity too, so both ARNs are listed and nothing else.
  statement {
    sid     = "ForwardFromDomainToOwner"
    actions = ["ses:SendRawEmail"]
    resources = [
      aws_sesv2_email_identity.notify_domain[0].arn,
      "arn:aws:ses:${var.region}:${local.account_id}:identity/${var.privacy_forward_to}",
    ]
  }

  statement {
    sid       = "WriteOwnLogs"
    actions   = ["logs:CreateLogStream", "logs:PutLogEvents"]
    resources = ["${aws_cloudwatch_log_group.privacy_forwarder[0].arn}:*"]
  }
}

resource "aws_iam_role_policy" "privacy_forwarder" {
  count = local.privacy_inbound_enabled ? 1 : 0

  name   = "${local.privacy_function}-policy"
  role   = aws_iam_role.privacy_forwarder[0].id
  policy = data.aws_iam_policy_document.privacy_forwarder[0].json
}

resource "aws_lambda_function" "privacy_forwarder" {
  count = local.privacy_inbound_enabled ? 1 : 0

  function_name    = local.privacy_function
  description      = "Forwards mail received at ${local.privacy_address}"
  role             = aws_iam_role.privacy_forwarder[0].arn
  runtime          = "python3.12"
  handler          = "forwarder.handler"
  filename         = data.archive_file.privacy_forwarder.output_path
  source_code_hash = data.archive_file.privacy_forwarder.output_base64sha256
  timeout          = 30
  memory_size      = 256

  environment {
    variables = {
      MAIL_BUCKET    = aws_s3_bucket.privacy_inbound[0].id
      MAIL_PREFIX    = local.privacy_prefix
      FORWARD_TO     = var.privacy_forward_to
      SENDER_ADDRESS = local.privacy_sender
      SENDER_NAME    = "FitSync Privacy"
    }
  }

  depends_on = [aws_cloudwatch_log_group.privacy_forwarder, aws_iam_role_policy.privacy_forwarder]
}

resource "aws_lambda_permission" "privacy_forwarder_ses" {
  count = local.privacy_inbound_enabled ? 1 : 0

  statement_id   = "AllowSesReceiptRule"
  action         = "lambda:InvokeFunction"
  function_name  = aws_lambda_function.privacy_forwarder[0].function_name
  principal      = "ses.amazonaws.com"
  source_account = local.account_id
  source_arn     = local.privacy_rule_arn
}

# ── Receipt rule ─────────────────────────────────────────────────────────────
resource "aws_ses_receipt_rule_set" "inbound" {
  count = local.privacy_inbound_enabled ? 1 : 0

  rule_set_name = local.privacy_rule_set
}

resource "aws_ses_active_receipt_rule_set" "inbound" {
  count = local.privacy_inbound_enabled ? 1 : 0

  rule_set_name = aws_ses_receipt_rule_set.inbound[0].rule_set_name
}

# SES checks on creation that it may write to the bucket and invoke the function, so the
# rule waits for the bucket policy and the Lambda permission.
resource "aws_ses_receipt_rule" "privacy" {
  count = local.privacy_inbound_enabled ? 1 : 0

  name          = local.privacy_rule
  rule_set_name = aws_ses_receipt_rule_set.inbound[0].rule_set_name
  recipients    = [local.privacy_address]
  enabled       = true
  scan_enabled  = true
  tls_policy    = "Require"

  s3_action {
    position          = 1
    bucket_name       = aws_s3_bucket.privacy_inbound[0].id
    object_key_prefix = local.privacy_prefix
  }

  lambda_action {
    position        = 2
    function_arn    = aws_lambda_function.privacy_forwarder[0].arn
    invocation_type = "Event"
  }

  depends_on = [aws_s3_bucket_policy.privacy_inbound, aws_lambda_permission.privacy_forwarder_ses]
}

output "privacy_inbound_address" {
  description = "Address whose mail is forwarded to privacy_forward_to (null while disabled)"
  value       = local.privacy_inbound_enabled ? local.privacy_address : null
}

output "privacy_inbound_bucket" {
  description = "Bucket holding the raw received messages for 30 days (null while disabled)"
  value       = local.privacy_inbound_enabled ? aws_s3_bucket.privacy_inbound[0].id : null
}
