# ─────────────────────────────────────────────────────────────────────────────
# One-time, account-wide bootstrap for GitHub Actions CD. Applied by hand with an
# admin profile BEFORE the first CD run; it has its own state and no workspaces,
# because these resources exist once per account, not once per environment.
#
#   cd infra/terraform/bootstrap && terraform init && terraform apply
#
# Creates:
#   - the GitHub OIDC identity provider
#   - fitsync-github-cd: the role the backend workflows assume (AWS_ROLE_ARN)
#   - fitsync-frontend-github-deploy: the frontend CD's own S3 + CloudFront role
#     (frontend_cd.tf)
#   - fitsync-workload-boundary: the permissions boundary every role CD creates
#     must carry, so CD can never mint a role stronger than itself
#   - the shared ECR repositories (registry.tf), empty ones cost nothing
#   - the SES sending domain, its public hosted zone and DNS records (email.tf), only when
#     notify_email_domain is set
# All of this is free, except the hosted zone at $0.50 a month.
# ─────────────────────────────────────────────────────────────────────────────

terraform {
  required_version = ">= 1.7.0" # import blocks with for_each

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  backend "s3" {
    bucket       = "fitsync-tfstate-437659978621"
    key          = "fitsync/bootstrap/terraform.tfstate"
    region       = "ap-southeast-1"
    encrypt      = true
    use_lockfile = true
  }
}

variable "region" {
  type    = string
  default = "ap-southeast-1"
}

variable "github_repositories" {
  description = "owner/repo of every repository whose workflows may assume the CD role"
  type        = list(string)
  default     = ["WittySalt/fitsync-backend"]
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project   = "fitsync"
      Env       = "account"
      ManagedBy = "terraform"
    }
  }
}

data "aws_caller_identity" "current" {}

locals {
  account_id = data.aws_caller_identity.current.account_id
  # Roles, policies and instance profiles the environment stacks create
  # (local.name = fitsync-<env>). The CD role itself is deliberately outside them.
  workload_patterns = ["fitsync-staging-*", "fitsync-prod-*"]
  # Only main and the two approval-gated environments may assume the role, so a
  # workflow on a feature branch or a fork's pull request cannot.
  # The frontend keeps its old place in this trust until phase 2 (frontend_cd.tf), so
  # its CD never loses access while AWS_ROLE_ARN is switched to its own role.
  cd_trusted_repositories = concat(
    var.github_repositories,
    var.frontend_cd_phase == 1 ? [var.frontend_github_repository] : [],
  )
  oidc_subjects = flatten([for repo in local.cd_trusted_repositories : [
    "repo:${repo}:ref:refs/heads/main",
    "repo:${repo}:environment:staging",
    "repo:${repo}:environment:production",
  ]])
}

resource "aws_iam_openid_connect_provider" "github" {
  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]
  # AWS validates GitHub's certificate itself now; the list is still required by the API.
  thumbprint_list = ["6938fd4d98bab03faadb97b34396831e3780aea1", "1c58a3a8518e8759bf075b76b750d4f2df264fcd"]
}

# ── Boundary for every workload role CD creates ──────────────────────────────
# Everything except identity administration. A role carrying it can never grant
# itself or anyone else more power, whatever policies are attached to it.
resource "aws_iam_policy" "workload_boundary" {
  name        = "fitsync-workload-boundary"
  description = "Permissions boundary for roles created by FitSync CD"
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "EverythingButIdentityAdmin"
      Effect    = "Allow"
      NotAction = ["iam:*", "organizations:*", "account:*"]
      Resource  = "*"
    }]
  })
}

# ── The CD role ──────────────────────────────────────────────────────────────
data "aws_iam_policy_document" "github_trust" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]
    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = local.oidc_subjects
    }
  }
}

resource "aws_iam_role" "github_cd" {
  name                 = "fitsync-github-cd"
  description          = "Assumed by FitSync GitHub Actions CD (AWS_ROLE_ARN)"
  assume_role_policy   = data.aws_iam_policy_document.github_trust.json
  max_session_duration = 3600
}

# PowerUserAccess covers VPC, EC2, RDS, ECR, S3, Cognito, CloudFront and SSM, and
# grants no IAM at all. The inline policy below adds back only the IAM that the
# environment stacks need.
resource "aws_iam_role_policy_attachment" "github_cd_power_user" {
  role       = aws_iam_role.github_cd.name
  policy_arn = "arn:aws:iam::aws:policy/PowerUserAccess"
}

data "aws_iam_policy_document" "github_cd_iam" {
  statement {
    sid       = "ReadIam"
    actions   = ["iam:Get*", "iam:List*"]
    resources = ["*"]
  }

  # Creating a role or changing what it may do requires the workload boundary on it.
  statement {
    sid = "ManageWorkloadRolesWithinBoundary"
    actions = [
      "iam:CreateRole",
      "iam:PutRolePermissionsBoundary",
      "iam:AttachRolePolicy",
      "iam:PutRolePolicy",
    ]
    resources = [for p in local.workload_patterns : "arn:aws:iam::${local.account_id}:role/${p}"]
    condition {
      test     = "StringEquals"
      variable = "iam:PermissionsBoundary"
      values   = [aws_iam_policy.workload_boundary.arn]
    }
  }

  statement {
    sid = "MaintainWorkloadRoles"
    actions = [
      "iam:DeleteRole",
      "iam:DetachRolePolicy",
      "iam:DeleteRolePolicy",
      "iam:UpdateRole",
      "iam:UpdateRoleDescription",
      "iam:TagRole",
      "iam:UntagRole",
      "iam:PassRole",
    ]
    resources = [for p in local.workload_patterns : "arn:aws:iam::${local.account_id}:role/${p}"]
  }

  statement {
    sid = "WorkloadInstanceProfiles"
    actions = [
      "iam:CreateInstanceProfile",
      "iam:DeleteInstanceProfile",
      "iam:AddRoleToInstanceProfile",
      "iam:RemoveRoleFromInstanceProfile",
      "iam:TagInstanceProfile",
      "iam:UntagInstanceProfile",
    ]
    resources = [for p in local.workload_patterns : "arn:aws:iam::${local.account_id}:instance-profile/${p}"]
  }

  statement {
    sid = "WorkloadManagedPolicies"
    actions = [
      "iam:CreatePolicy",
      "iam:DeletePolicy",
      "iam:CreatePolicyVersion",
      "iam:DeletePolicyVersion",
      "iam:TagPolicy",
      "iam:UntagPolicy",
    ]
    resources = [for p in local.workload_patterns : "arn:aws:iam::${local.account_id}:policy/${p}"]
  }

  # RDS and EC2 create their service-linked roles on first use in an account.
  statement {
    sid       = "ServiceLinkedRoles"
    actions   = ["iam:CreateServiceLinkedRole"]
    resources = ["arn:aws:iam::${local.account_id}:role/aws-service-role/*"]
  }

  statement {
    sid       = "NeverDropTheBoundary"
    effect    = "Deny"
    actions   = ["iam:DeleteRolePermissionsBoundary"]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "github_cd_iam" {
  name   = "fitsync-github-cd-iam"
  role   = aws_iam_role.github_cd.id
  policy = data.aws_iam_policy_document.github_cd_iam.json
}

output "github_cd_role_arn" {
  description = "Set as the AWS_ROLE_ARN repository variable in the backend repo"
  value       = aws_iam_role.github_cd.arn
}

output "workload_boundary_arn" {
  value = aws_iam_policy.workload_boundary.arn
}
