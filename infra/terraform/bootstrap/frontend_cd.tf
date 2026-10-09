# ── Frontend CD role ─────────────────────────────────────────────────────────
# The frontend CD (fitsync-frontend .github/workflows/cd.yml) only syncs a built SPA
# to two S3 buckets and invalidates two CloudFront distributions. It runs no
# Terraform, so it gets its own role with exactly that and nothing else, instead of
# sharing fitsync-github-cd (PowerUserAccess).
#
# Switching over takes two applies with a step in GitHub between them, so the
# frontend CD never loses access (RUNBOOK.md, "Frontend CD role"):
#
#   phase 1 (default): create this role; adopt the GitLab-era fitsync-frontend-deploy
#     role into state with import blocks (no change in AWS); fitsync-github-cd still
#     trusts the frontend repository.
#   then: point the frontend's AWS_ROLE_ARN at frontend_cd_role_arn and run its CD
#     once (staging only) to prove the new role works.
#   phase 2 (-var frontend_cd_phase=2): drop the frontend from fitsync-github-cd's
#     trust and destroy fitsync-frontend-deploy with its two inline policies.
#
# The role is created here by an admin, not by CD, and holds no IAM permission, so it
# does not carry fitsync-workload-boundary (that boundary is for the roles CD creates).

variable "frontend_cd_phase" {
  description = "1 = new frontend role added, old access kept; 2 = old access removed (see RUNBOOK.md)"
  type        = number
  default     = 2

  validation {
    condition     = contains([1, 2], var.frontend_cd_phase)
    error_message = "frontend_cd_phase must be 1 or 2."
  }
}

variable "frontend_github_repository" {
  description = "owner/repo of the frontend repository"
  type        = string
  default     = "WittySalt/fitsync-frontend"
}

variable "frontend_buckets" {
  description = "S3 buckets the frontend CD syncs the SPA into (staging, prod)"
  type        = list(string)
  default     = ["fitsync-staging-frontend-437659978621", "fitsync-frontend-prod"]
}

variable "frontend_distribution_ids" {
  description = "CloudFront distributions the frontend CD invalidates (staging, prod)"
  type        = list(string)
  default     = ["EE1XAW2NAZ576", "E1APOLMHRQOW5Y"]
}

data "aws_iam_policy_document" "frontend_cd_trust" {
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
    # Only the two jobs that deploy assume a role, and both run in an environment
    # (deploy-staging in staging, deploy-production in production), so they present
    # the environment subject. ci-gate and build have no id-token permission and
    # never assume a role, so main's ref subject is not trusted.
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values = [
        "repo:${var.frontend_github_repository}:environment:staging",
        "repo:${var.frontend_github_repository}:environment:production",
      ]
    }
  }
}

resource "aws_iam_role" "frontend_cd" {
  name                 = "fitsync-frontend-github-deploy"
  description          = "Assumed by the FitSync frontend GitHub Actions CD: S3 sync and CloudFront invalidation only"
  assume_role_policy   = data.aws_iam_policy_document.frontend_cd_trust.json
  max_session_duration = 3600
}

data "aws_iam_policy_document" "frontend_cd_deploy" {
  # aws s3 sync --delete lists the bucket to compare and find what to delete.
  statement {
    sid       = "ListSpaBuckets"
    actions   = ["s3:ListBucket"]
    resources = [for b in var.frontend_buckets : "arn:aws:s3:::${b}"]
  }

  # sync and cp upload (PutObject, including the --cache-control metadata) and
  # sync --delete removes files the new build no longer has.
  statement {
    sid       = "WriteSpaObjects"
    actions   = ["s3:PutObject", "s3:DeleteObject", "s3:GetObject"]
    resources = [for b in var.frontend_buckets : "arn:aws:s3:::${b}/*"]
  }

  statement {
    sid       = "InvalidateSpaDistributions"
    actions   = ["cloudfront:CreateInvalidation", "cloudfront:GetInvalidation"]
    resources = [for id in var.frontend_distribution_ids : "arn:aws:cloudfront::${local.account_id}:distribution/${id}"]
  }
}

resource "aws_iam_role_policy" "frontend_cd_deploy" {
  name   = "fitsync-frontend-github-deploy"
  role   = aws_iam_role.frontend_cd.id
  policy = data.aws_iam_policy_document.frontend_cd_deploy.json
}

output "frontend_cd_role_arn" {
  description = "Set as the AWS_ROLE_ARN repository variable in the frontend repo"
  value       = aws_iam_role.frontend_cd.arn
}

# ── Retiring the GitLab-era role ─────────────────────────────────────────────
# fitsync-frontend-deploy was made by hand for the GitLab pipeline (trusts the
# gitlab.com OIDC provider, project fitsyncs/fitsync-frontend), last used 2026-06-26.
# Phase 1 imports it and its two inline policies exactly as they are, so the plan
# shows nothing to change on them; phase 2 sets count to 0 and the plan shows the
# three deletions. The gitlab.com OIDC provider stays: fitsync-gitlab-ci still trusts it.
locals {
  legacy_frontend_role     = "fitsync-frontend-deploy"
  legacy_frontend_policies = ["fe-terraform-staging", "s3-cloudfront-deploy"]
  legacy_frontend_count    = var.frontend_cd_phase == 1 ? 1 : 0
}

import {
  for_each = var.frontend_cd_phase == 1 ? toset(["role"]) : toset([])
  to       = aws_iam_role.legacy_frontend_deploy[0]
  id       = local.legacy_frontend_role
}

import {
  for_each = var.frontend_cd_phase == 1 ? toset(local.legacy_frontend_policies) : toset([])
  to       = aws_iam_role_policy.legacy_frontend_deploy[each.value]
  id       = "${local.legacy_frontend_role}:${each.value}"
}

resource "aws_iam_role" "legacy_frontend_deploy" {
  count = local.legacy_frontend_count

  name                 = local.legacy_frontend_role
  description          = "GitLab OIDC role for fitsync frontend S3+CloudFront deploy"
  max_session_duration = 3600
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Federated = "arn:aws:iam::${local.account_id}:oidc-provider/gitlab.com" }
      Action    = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = { "gitlab.com:aud" = "sts.amazonaws.com" }
        StringLike   = { "gitlab.com:sub" = "project_path:fitsyncs/fitsync-frontend:*" }
      }
    }]
  })

  # Adopted only to be deleted in phase 2; do not stamp the default tags on it first.
  lifecycle {
    ignore_changes = [tags_all]
  }
}

resource "aws_iam_role_policy" "legacy_frontend_deploy" {
  for_each = var.frontend_cd_phase == 1 ? toset(local.legacy_frontend_policies) : toset([])

  name = each.value
  role = aws_iam_role.legacy_frontend_deploy[0].id
  policy = each.value == "fe-terraform-staging" ? jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "FrontendBuckets"
        Effect = "Allow"
        Action = "s3:*"
        Resource = [
          "arn:aws:s3:::fitsync-staging-frontend-437659978621",
          "arn:aws:s3:::fitsync-staging-frontend-437659978621/*",
          "arn:aws:s3:::fitsync-frontend-prod",
          "arn:aws:s3:::fitsync-frontend-prod/*",
        ]
      },
      {
        Sid    = "TerraformState"
        Effect = "Allow"
        Action = "s3:*"
        Resource = [
          "arn:aws:s3:::fitsync-tfstate-437659978621",
          "arn:aws:s3:::fitsync-tfstate-437659978621/*",
        ]
      },
      {
        Sid    = "CloudFront"
        Effect = "Allow"
        Action = [
          "cloudfront:CreateDistribution",
          "cloudfront:GetDistribution",
          "cloudfront:GetDistributionConfig",
          "cloudfront:UpdateDistribution",
          "cloudfront:DeleteDistribution",
          "cloudfront:TagResource",
          "cloudfront:ListTagsForResource",
          "cloudfront:CreateInvalidation",
          "cloudfront:ListDistributions",
        ]
        Resource = "*"
      },
    ]
    }) : jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "S3Deploy"
        Effect   = "Allow"
        Action   = ["s3:PutObject", "s3:DeleteObject", "s3:ListBucket", "s3:GetObject"]
        Resource = ["arn:aws:s3:::fitsync-frontend-prod", "arn:aws:s3:::fitsync-frontend-prod/*"]
      },
      {
        Sid      = "CFInvalidate"
        Effect   = "Allow"
        Action   = ["cloudfront:CreateInvalidation"]
        Resource = "arn:aws:cloudfront::437659978621:distribution/E1APOLMHRQOW5Y"
      },
    ]
  })
}
