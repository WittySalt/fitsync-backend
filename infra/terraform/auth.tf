# ── Cognito user pool + SPA app client (free tier) ───────────────────────────
# Off for prod: prod keeps its existing pool (created by hand, holds real users), which
# cognito_prod.tf adopts by import; auth-service and the SPA point at it by configuration.
resource "aws_cognito_user_pool" "main" {
  count = var.enable_cognito ? 1 : 0

  name = "${local.name}-users"

  username_attributes      = ["email"]
  auto_verified_attributes = ["email"]

  password_policy {
    minimum_length    = 8
    require_lowercase = true
    require_uppercase = true
    require_numbers   = true
    require_symbols   = true # same rule as the prod pool
  }

  account_recovery_setting {
    recovery_mechanism {
      name     = "verified_email"
      priority = 1
    }
  }

  tags = { Name = "${local.name}-users" }
}

resource "aws_cognito_user_pool_client" "spa" {
  count = var.enable_cognito ? 1 : 0

  name         = "FitSync"
  user_pool_id = aws_cognito_user_pool.main[0].id

  generate_secret = false

  # Sign-in and password reset answer an unknown email like a known one, so neither
  # tells anyone which emails have an account.
  prevent_user_existence_errors = "ENABLED"

  # Custom Angular login UI calls the User Pool directly -> needs USER_PASSWORD_AUTH.
  explicit_auth_flows = [
    "ALLOW_USER_PASSWORD_AUTH",
    "ALLOW_REFRESH_TOKEN_AUTH",
    "ALLOW_USER_SRP_AUTH",
  ]

  allowed_oauth_flows                  = ["code"]
  allowed_oauth_flows_user_pool_client = true
  allowed_oauth_scopes                 = ["openid", "email", "profile"]
  supported_identity_providers         = ["COGNITO"]

  callback_urls = var.cognito_callback_urls
  logout_urls   = var.cognito_logout_urls
}

resource "aws_cognito_user_pool_domain" "main" {
  count = var.enable_cognito ? 1 : 0

  domain       = "${local.name}-${local.account_id}"
  user_pool_id = aws_cognito_user_pool.main[0].id
}

# ── Account purge: delete the Cognito user (free) ────────────────────────────
# When the 30-day grace period of an account deletion ends, the purge deletes the user from
# this environment's pool with cognito-idp:AdminDeleteUser, called with the EC2 instance role.
# The grant names only that action and only this environment's pool: the staging pool built
# above, or the adopted prod pool (cognito_prod.tf). With neither, nothing is granted.
locals {
  # A plain flag for count: the ARN of a pool still to be created is unknown at plan time.
  has_app_user_pool = var.enable_cognito || local.adopt_prod_cognito
  app_user_pool_arn = (var.enable_cognito ? aws_cognito_user_pool.main[0].arn :
  local.adopt_prod_cognito ? aws_cognito_user_pool.prod[0].arn : null)
}

data "aws_iam_policy_document" "ec2_cognito_purge" {
  count = local.has_app_user_pool ? 1 : 0

  statement {
    sid       = "DeletePurgedUsers"
    actions   = ["cognito-idp:AdminDeleteUser"]
    resources = [local.app_user_pool_arn]
  }
}

resource "aws_iam_role_policy" "ec2_cognito_purge" {
  count = local.has_app_user_pool ? 1 : 0

  name   = "${local.name}-ec2-cognito-purge"
  role   = aws_iam_role.ec2_app.id
  policy = data.aws_iam_policy_document.ec2_cognito_purge[0].json
}
