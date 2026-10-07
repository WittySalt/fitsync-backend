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
  # Created by bootstrap/. The CD role may only create roles that carry it.
  permissions_boundary = "arn:aws:iam::${local.account_id}:policy/fitsync-workload-boundary"
  tags                 = { Name = "${local.name}-ec2-app" }
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

# Lets the coach call Amazon Bedrock with the instance role (provider `bedrock`), so no
# model API key lives on the host. Scoped to the models in var.bedrock_models, reached
# either directly or through a cross-region inference profile (global./apac.). A global
# profile routes to a foundation model ARN with no region, which the `*` region matches.
data "aws_iam_policy_document" "ec2_bedrock" {
  statement {
    sid     = "InvokeCoachModels"
    actions = ["bedrock:InvokeModel"]
    resources = concat(
      [for m in var.bedrock_models : "arn:aws:bedrock:*::foundation-model/${m}"],
      [for m in var.bedrock_models : "arn:aws:bedrock:*:${local.account_id}:inference-profile/*.${m}"],
    )
  }
}

resource "aws_iam_role_policy" "ec2_bedrock" {
  name   = "${local.name}-ec2-bedrock"
  role   = aws_iam_role.ec2_app.id
  policy = data.aws_iam_policy_document.ec2_bedrock.json
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
    # The shared repositories created by bootstrap/registry.tf.
    resources = ["arn:aws:ecr:${var.region}:${local.account_id}:repository/${var.project}-*"]
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
