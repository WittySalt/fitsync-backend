# ── Cognito user pool + SPA app client (free tier) ───────────────────────────
# Off for prod: prod keeps its existing pool (created by hand, holds real users), and
# auth-service / the SPA are pointed at it by configuration instead.
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
    require_symbols   = false
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
