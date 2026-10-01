# ── EC2 instance role: pull from ECR + SSM-managed (free) ────────────────────
data "aws_iam_policy_document" "ec2_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "ec2_app" {
  name               = "${local.name}-ec2-app"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume.json
  tags               = { Name = "${local.name}-ec2-app" }
}

resource "aws_iam_role_policy_attachment" "ec2_ecr_pull" {
  role       = aws_iam_role.ec2_app.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
}

# Lets the CD `deploy-prod` job drive the host over SSM Run Command.
resource "aws_iam_role_policy_attachment" "ec2_ssm" {
  role       = aws_iam_role.ec2_app.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "ec2_app" {
  name = "${local.name}-ec2-app"
  role = aws_iam_role.ec2_app.name
}

# ── CI deploy policy: push images + trigger SSM deploy (free) ─────────────────
# Attach this to your GitHub Actions principal (the OIDC role in AWS_ROLE_ARN).
data "aws_iam_policy_document" "ci_deploy" {
  statement {
    sid = "EcrAuth"
    actions = [
      "ecr:GetAuthorizationToken",
    ]
    resources = ["*"]
  }

  statement {
    sid = "EcrPushPull"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:GetDownloadUrlForLayer",
      "ecr:BatchGetImage",
      "ecr:PutImage",
      "ecr:InitiateLayerUpload",
      "ecr:UploadLayerPart",
      "ecr:CompleteLayerUpload",
    ]
    resources = [for r in aws_ecr_repository.service : r.arn]
  }

  statement {
    sid = "SsmDeploy"
    actions = [
      "ssm:SendCommand",
      "ssm:GetCommandInvocation",
      "ssm:ListCommandInvocations",
    ]
    resources = ["*"]
  }
}

resource "aws_iam_policy" "ci_deploy" {
  name        = "${local.name}-ci-deploy"
  description = "FitSync CI: push to ECR + deploy via SSM"
  policy      = data.aws_iam_policy_document.ci_deploy.json
}
