# ── Email notifications: send permission (+ optional address identity) (free) ─
# notification-service sends through Amazon SES (SDK v2 sesv2) with the EC2 instance role,
# so no key lives on the host. Nothing here exists while var.notify_email_sender is empty:
# no permission, and the service keeps logging emails instead of sending.
#
# The usual sender is an address on the FitSync domain (noreply@fitsyncapp.click for prod,
# noreply-staging@fitsyncapp.click for staging). That domain is one account-wide SES identity
# with DKIM, managed in bootstrap/email.tf, so this workspace creates no identity
# (notify_email_create_identity = false, the default) and only grants the send permission.
#
# For a sender outside that domain, set notify_email_create_identity = true: the workspace
# then creates an address identity and SES mails a verification link to it, which someone
# must click before anything can be sent. While the account is in the SES sandbox every
# recipient must be verified too (RUNBOOK.md "Email notifications (SES)").

resource "aws_sesv2_email_identity" "notify_sender" {
  count = var.notify_email_sender != "" && var.notify_email_create_identity ? 1 : 0

  email_identity = var.notify_email_sender
}

# The policy limits the From address, not the identity resource. In the sandbox SES also
# authorizes the send against each recipient's identity, so a policy that names only the
# sender identity is refused there; ses:FromAddress keeps the host from sending as anyone
# else in both modes. The condition compares the bare address: the display name the service
# puts in front ("FitSync Staging <noreply-staging@...>") has its own key, ses:FromDisplayName.
data "aws_iam_policy_document" "ec2_ses" {
  count = var.notify_email_sender != "" ? 1 : 0

  statement {
    sid       = "SendAsNotificationSender"
    actions   = ["ses:SendEmail", "ses:SendRawEmail"]
    resources = ["arn:aws:ses:${var.region}:${local.account_id}:identity/*"]

    condition {
      test     = "StringEquals"
      variable = "ses:FromAddress"
      values   = [var.notify_email_sender]
    }
  }
}

resource "aws_iam_role_policy" "ec2_ses" {
  count = var.notify_email_sender != "" ? 1 : 0

  name   = "${local.name}-ec2-ses"
  role   = aws_iam_role.ec2_app.id
  policy = data.aws_iam_policy_document.ec2_ses[0].json
}
