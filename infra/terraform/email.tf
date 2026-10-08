# ── Email notifications: SES sender identity + send permission (free) ───────
# notification-service sends through Amazon SES (SDK v2 sesv2) with the EC2 instance role,
# so no key lives on the host. Nothing here exists while var.notify_email_sender is empty:
# no identity and no permission, and the service keeps logging emails instead of sending.
#
# An SES identity belongs to the account and region, not to a workspace. Staging and prod
# share the account, so either give each its own sender (plus-addressing such as
# name+staging@example.com counts as a separate identity) or let one workspace create the
# identity and set notify_email_create_identity = false in the other.
#
# Creating the identity makes SES send a verification email to the sender address; nothing
# can be sent until someone clicks that link. While the account is in the SES sandbox every
# recipient must be verified too (RUNBOOK.md "Email notifications (SES)").

resource "aws_sesv2_email_identity" "notify_sender" {
  count = var.notify_email_sender != "" && var.notify_email_create_identity ? 1 : 0

  email_identity = var.notify_email_sender
}

# The policy limits the From address, not the identity resource. In the sandbox SES also
# authorizes the send against each recipient's identity, so a policy that names only the
# sender identity is refused there; ses:FromAddress keeps the host from sending as anyone
# else in both modes.
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
