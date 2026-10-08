# ── Email notifications: SES domain identity (account-wide, free) ───────────
# An SES identity belongs to the account and region, so staging and prod share this one:
# every address @<domain> (noreply@ for prod, noreply-staging@ for staging) may send once
# the domain verifies. The workspaces only grant the EC2 role ses:SendEmail pinned to their
# own From address (../email.tf) and create no identity of their own.
#
# Nothing here exists while var.notify_email_domain is empty.
#
# The public hosted zone is created HERE, not looked up. The domain was registered in another
# account (438465161112), whose auto-created zone does not move when the registration is
# transferred into this one. After the first apply, point the registration's name servers at
# notify_email_domain_name_servers; DKIM only verifies once they answer for the domain
# (RUNBOOK.md "Email notifications (SES)").
#
# Records written into the zone:
#   - three DKIM CNAMEs (Easy DKIM, RSA 2048); SES verifies the domain once they resolve
#   - custom MAIL FROM mail.<domain>: MX to SES feedback + SPF, so SPF aligns with the From domain
#   - DMARC p=none on _dmarc.<domain>, reports to var.notify_email_dmarc_rua when set

variable "notify_email_domain" {
  description = "Domain notification emails are sent from, verified in SES with Easy DKIM (empty = nothing created)"
  type        = string
  # The registered domain is the default, so an apply that forgets -var keeps the zone and the
  # identity instead of destroying them.
  default = "fitsync.link"

  validation {
    condition     = var.notify_email_domain == "" || can(regex("^([a-z0-9]([a-z0-9-]*[a-z0-9])?[.])+[a-z]{2,}$", var.notify_email_domain))
    error_message = "notify_email_domain must be empty or a lower-case domain name such as fitsync.link."
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

resource "aws_route53_zone" "notify" {
  count = local.notify_domain_enabled ? 1 : 0

  name    = var.notify_email_domain
  comment = "FitSync sending domain (SES DKIM, MAIL FROM, DMARC); managed by bootstrap/email.tf"
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
  description = "The four name servers of the new hosted zone; set them on the domain registration (update-domain-nameservers)"
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
