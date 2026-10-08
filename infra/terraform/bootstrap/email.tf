# ── Email notifications: SES domain identity (account-wide, free) ───────────
# An SES identity belongs to the account and region, so staging and prod share this one:
# every address @<domain> (noreply@ for prod, noreply-staging@ for staging) may send once
# the domain verifies. The workspaces only grant the EC2 role ses:SendEmail pinned to their
# own From address (../email.tf) and create no identity of their own.
#
# Nothing here exists while var.notify_email_domain is empty.
#
# The public hosted zone is ADOPTED, not created. Route 53 created it in this account
# (437659978621) when fitsyncapp.click was registered here, and the registration already
# points at its name servers, so nothing changes at the registrar. The import block below
# brings that zone under Terraform on the first apply; prevent_destroy keeps a later apply
# from deleting it (RUNBOOK.md "Email notifications (SES)").
#
# Records written into the zone:
#   - three DKIM CNAMEs (Easy DKIM, RSA 2048); SES verifies the domain once they resolve
#   - custom MAIL FROM mail.<domain>: MX to SES feedback + SPF, so SPF aligns with the From domain
#   - DMARC p=none on _dmarc.<domain>, reports to var.notify_email_dmarc_rua when set

variable "notify_email_domain" {
  description = "Domain notification emails are sent from, verified in SES with Easy DKIM (empty = nothing created)"
  type        = string
  # The domain registered in this account is the default, so a plain apply adopts its zone and
  # creates the identity, and an apply that forgets -var does not try to remove them.
  default = "fitsyncapp.click"

  validation {
    condition     = var.notify_email_domain == "" || can(regex("^([a-z0-9]([a-z0-9-]*[a-z0-9])?[.])+[a-z]{2,}$", var.notify_email_domain))
    error_message = "notify_email_domain must be empty or a lower-case domain name such as fitsyncapp.click."
  }
}

variable "notify_email_dmarc_rua" {
  description = "Mailbox for DMARC aggregate reports, e.g. dmarc@example.com (empty = no rua tag)"
  type        = string
  default     = ""

  validation {
    condition     = var.notify_email_dmarc_rua == "" || can(regex("^[^@[:space:];,]+@[^@[:space:];,]+[.][^@[:space:];,]+$", var.notify_email_dmarc_rua))
    error_message = "notify_email_dmarc_rua must be empty or one email address."
  }
}

locals {
  notify_domain_enabled = var.notify_email_domain != ""
  notify_mail_from      = "mail.${var.notify_email_domain}"
  notify_dmarc          = var.notify_email_dmarc_rua == "" ? "v=DMARC1; p=none;" : "v=DMARC1; p=none; rua=mailto:${var.notify_email_dmarc_rua}"
}

# The zone Route 53 Registrar created for fitsyncapp.click. Its comment is kept as the
# registrar wrote it so the import plans no change. If notify_email_domain is ever pointed at
# another domain, remove this import block first.
import {
  to = aws_route53_zone.notify[0]
  id = "Z03373831R8G5N1TEF6LB"
}

resource "aws_route53_zone" "notify" {
  count = local.notify_domain_enabled ? 1 : 0

  name    = var.notify_email_domain
  comment = "HostedZone created by Route53 Registrar"

  # The zone is left as the registrar made it: without default tags, and with force_destroy
  # unset in state (false), so the import changes nothing on it.
  lifecycle {
    prevent_destroy = true
    ignore_changes  = [force_destroy, tags_all]
  }
}

resource "aws_sesv2_email_identity" "notify_domain" {
  count = local.notify_domain_enabled ? 1 : 0

  email_identity = var.notify_email_domain

  dkim_signing_attributes {
    next_signing_key_length = "RSA_2048_BIT"
  }
}

# SES hands back three tokens; each becomes <token>._domainkey.<domain> CNAME <token>.dkim.amazonses.com.
# The count is fixed at 3 because the tokens are only known after the identity exists.
resource "aws_route53_record" "notify_dkim" {
  count = local.notify_domain_enabled ? 3 : 0

  zone_id = aws_route53_zone.notify[0].zone_id
  name    = "${aws_sesv2_email_identity.notify_domain[0].dkim_signing_attributes[0].tokens[count.index]}._domainkey.${var.notify_email_domain}"
  type    = "CNAME"
  ttl     = 1800
  records = ["${aws_sesv2_email_identity.notify_domain[0].dkim_signing_attributes[0].tokens[count.index]}.dkim.amazonses.com"]
}

# USE_DEFAULT_VALUE: if the MX record ever goes missing SES falls back to amazonses.com as
# the MAIL FROM instead of refusing to send.
resource "aws_sesv2_email_identity_mail_from_attributes" "notify_domain" {
  count = local.notify_domain_enabled ? 1 : 0

  email_identity         = aws_sesv2_email_identity.notify_domain[0].email_identity
  mail_from_domain       = local.notify_mail_from
  behavior_on_mx_failure = "USE_DEFAULT_VALUE"
}

resource "aws_route53_record" "notify_mail_from_mx" {
  count = local.notify_domain_enabled ? 1 : 0

  zone_id = aws_route53_zone.notify[0].zone_id
  name    = local.notify_mail_from
  type    = "MX"
  ttl     = 1800
  records = ["10 feedback-smtp.${var.region}.amazonses.com"]
}

resource "aws_route53_record" "notify_mail_from_spf" {
  count = local.notify_domain_enabled ? 1 : 0

  zone_id = aws_route53_zone.notify[0].zone_id
  name    = local.notify_mail_from
  type    = "TXT"
  ttl     = 1800
  records = ["v=spf1 include:amazonses.com ~all"]
}

# p=none only reports; tighten to quarantine once the reports show DKIM and SPF passing.
resource "aws_route53_record" "notify_dmarc" {
  count = local.notify_domain_enabled ? 1 : 0

  zone_id = aws_route53_zone.notify[0].zone_id
  name    = "_dmarc.${var.notify_email_domain}"
  type    = "TXT"
  ttl     = 1800
  records = [local.notify_dmarc]
}

output "notify_email_domain_name_servers" {
  description = "The four name servers of the adopted hosted zone (the registration already uses them)"
  value       = local.notify_domain_enabled ? aws_route53_zone.notify[0].name_servers : null
}

output "notify_email_domain_identity_arn" {
  description = "SES identity of the sending domain (null while notify_email_domain is empty)"
  value       = local.notify_domain_enabled ? aws_sesv2_email_identity.notify_domain[0].arn : null
}

output "notify_email_domain_dkim_status" {
  description = "Easy DKIM status of the sending domain: PENDING until the CNAMEs resolve, then SUCCESS"
  value       = local.notify_domain_enabled ? aws_sesv2_email_identity.notify_domain[0].dkim_signing_attributes[0].status : null
}
