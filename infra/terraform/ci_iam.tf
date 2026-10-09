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
# What CD needs, written down per environment. It is attached to nothing today: the CD
# role fitsync-github-cd (bootstrap/) holds PowerUserAccess, which already covers all of
# it, and this stack may not attach policies to that role. It becomes the CD role's grant
# if PowerUserAccess is ever narrowed (attach it in bootstrap/).
data "aws_iam_policy_document" "ci_deploy" {
  # ssh/scp over SSM Session Manager (cd.yml ProxyCommand), to this environment's host and
  # only with the SSH document (statements below, "ssh over SSM Session Manager").
  source_policy_documents = [data.aws_iam_policy_document.ssm_ssh_ci.json]

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

# ── ssh over SSM Session Manager (free) ─────────────────────────────────────
# Port 22 is closed (network.tf); ssh and scp reach the host through
# `aws ssm start-session --document-name AWS-StartSSHSession`, which still needs the
# deploy key. These statements allow exactly that: a session to this environment's
# instance, only with the AWS-StartSSHSession document (SessionDocumentAccessCheck makes
# a StartSession without a document, the plain shell, fail), and ending or resuming only
# the caller's own sessions. A session id starts with the IAM user name for a user and
# with the role session name for a role (configure-aws-credentials names CD's
# GitHubActions). AWS-owned documents have no account in their ARN, hence the `*`.
data "aws_iam_policy_document" "ssm_ssh_start" {
  statement {
    sid     = "StartSshSessionToAppHost"
    actions = ["ssm:StartSession"]
    resources = concat(
      [for i in aws_instance.app : i.arn],
      ["arn:aws:ssm:${var.region}:*:document/AWS-StartSSHSession"],
    )
    condition {
      test     = "BoolIfExists"
      variable = "ssm:SessionDocumentAccessCheck"
      values   = ["true"]
    }
  }
}

data "aws_iam_policy_document" "ssm_ssh_ci" {
  source_policy_documents = [data.aws_iam_policy_document.ssm_ssh_start.json]

  statement {
    sid       = "OwnSessionsOnly"
    actions   = ["ssm:TerminateSession", "ssm:ResumeSession", "ssmmessages:OpenDataChannel"]
    resources = ["arn:aws:ssm:${var.region}:${local.account_id}:session/GitHubActions-*"]
  }
}

data "aws_iam_policy_document" "ssm_ssh_operator" {
  source_policy_documents = [data.aws_iam_policy_document.ssm_ssh_start.json]

  statement {
    sid     = "OwnSessionsOnly"
    actions = ["ssm:TerminateSession", "ssm:ResumeSession", "ssmmessages:OpenDataChannel"]
    resources = [
      "arn:aws:ssm:${var.region}:${local.account_id}:session/$${aws:username}-*",
      "arn:aws:ssm:${var.region}:${local.account_id}:session/$${aws:userid}-*",
    ]
  }
}

# For people: attach to the operator's IAM user (or group) by hand, see RUNBOOK.md
# "SSH over SSM". The CD stack cannot attach policies to users, and an admin profile
# already has these rights; the policy is what a least-privilege operator needs.
resource "aws_iam_policy" "ssm_operator" {
  count = var.enable_ec2 ? 1 : 0

  name        = "${local.name}-ssm-operator"
  description = "FitSync ${var.env}: ssh to the app host over SSM Session Manager"
  policy      = data.aws_iam_policy_document.ssm_ssh_operator.json
}
