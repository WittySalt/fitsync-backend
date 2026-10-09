# ── Private avatars: pre-signed reads ────────────────────────────────────────
# The media bucket policies (storage.tf, media_prod.tf) make only exercises/ public.
# Avatars live at avatars/<userId>/<uuid>.<ext> and are read through pre-signed GET URLs
# that user-service signs with the EC2 instance role when it returns a profile. A
# pre-signed URL carries the signer's rights, so the role itself needs s3:GetObject on
# avatars/. It is kept in its own policy so it does not touch the upload policy in
# storage.tf.
data "aws_iam_policy_document" "ec2_media_avatar_read" {
  statement {
    sid       = "ReadAvatarsForPresignedUrls"
    actions   = ["s3:GetObject"]
    resources = ["arn:aws:s3:::${local.media_bucket}/avatars/*"]
  }
}

resource "aws_iam_role_policy" "ec2_media_avatar_read" {
  name   = "${local.name}-ec2-media-avatar-read"
  role   = aws_iam_role.ec2_app.id
  policy = data.aws_iam_policy_document.ec2_media_avatar_read.json
}
