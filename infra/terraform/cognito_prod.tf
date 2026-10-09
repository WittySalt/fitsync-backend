# ── Prod Cognito: the hand-built pool, adopted (prod only) ───────────────────
# Prod signs in with ap-southeast-1_myVyuOmPD ("User pool - 8lgadn"), created by hand in
# June 2026 and holding every real user and password. It is ADOPTED with the import
# blocks below and must never be recreated: a new pool would lose every account.
#
# Everything here exists only when var.prod_cognito is set, which only prod.tfvars does
# (staging builds its own pool in auth.tf). Import blocks cannot depend on the workspace,
# so they iterate over a set that is empty unless var.prod_cognito is set: in staging
# they import nothing, in prod they adopt the three resources on the first apply and are
# no-ops afterwards.
#
# The arguments mirror the live pool as `terraform plan -generate-config-out` read it on
# 2026-10-09, so the import plans no change. Do not tidy them to match auth.tf, because
# every difference is a live change to the pool that real users sign in with:
#   - schema: only email differs from the Cognito defaults. Schema entries can be added
#     but never changed or removed, and a wrong one fails the apply.
#   - account recovery keeps verified_phone_number as the second mechanism.
#   - the client keeps every auth flow it has today. The SPA and auth-service use its id
#     4mvse0cgmkplgocfg827uolvad (frontend auth.config.ts PROD_POOL, and the compose
#     default of AUTH_COGNITO_AUDIENCE).
#   - tags_all is ignored on the pool: the default tags would be its only change.

locals {
  adopt_prod_cognito = var.prod_cognito != null
}

import {
  for_each = local.adopt_prod_cognito ? toset([var.prod_cognito.user_pool_id]) : toset([])
  to       = aws_cognito_user_pool.prod[0]
  id       = each.value
}

import {
  for_each = local.adopt_prod_cognito ? toset(["${var.prod_cognito.user_pool_id}/${var.prod_cognito.client_id}"]) : toset([])
  to       = aws_cognito_user_pool_client.prod[0]
  id       = each.value
}

import {
  for_each = local.adopt_prod_cognito ? toset([var.prod_cognito.domain]) : toset([])
  to       = aws_cognito_user_pool_domain.prod[0]
  id       = each.value
}

resource "aws_cognito_user_pool" "prod" {
  count = local.adopt_prod_cognito ? 1 : 0

  name                     = "User pool - 8lgadn"
  username_attributes      = ["email"]
  auto_verified_attributes = ["email"]
  mfa_configuration        = "OFF"
  user_pool_tier           = "ESSENTIALS"
  deletion_protection      = "ACTIVE"

  account_recovery_setting {
    recovery_mechanism {
      name     = "verified_email"
      priority = 1
    }
    recovery_mechanism {
      name     = "verified_phone_number"
      priority = 2
    }
  }

  admin_create_user_config {
    allow_admin_create_user_only = false
  }

  email_configuration {
    email_sending_account = "COGNITO_DEFAULT"
  }

  password_policy {
    minimum_length                   = 8
    password_history_size            = 0
    require_lowercase                = true
    require_numbers                  = true
    require_symbols                  = true
    require_uppercase                = true
    temporary_password_validity_days = 7
  }

  schema {
    name                     = "email"
    attribute_data_type      = "String"
    developer_only_attribute = false
    mutable                  = true
    required                 = true
    string_attribute_constraints {
      min_length = "0"
      max_length = "2048"
    }
  }

  sign_in_policy {
    allowed_first_auth_factors = ["PASSWORD"]
  }

  username_configuration {
    case_sensitive = false
  }

  verification_message_template {
    default_email_option = "CONFIRM_WITH_CODE"
  }

  lifecycle {
    prevent_destroy = true
    ignore_changes  = [tags_all]

    precondition {
      condition     = var.env == "prod"
      error_message = "prod_cognito adopts the prod user pool and may only be set in the prod workspace."
    }
    precondition {
      condition     = !var.enable_cognito
      error_message = "Set enable_cognito = false when prod_cognito is set: the environment uses the adopted pool."
    }
  }
}

resource "aws_cognito_user_pool_client" "prod" {
  count = local.adopt_prod_cognito ? 1 : 0

  name         = "FitSync"
  user_pool_id = aws_cognito_user_pool.prod[0].id

  explicit_auth_flows = [
    "ALLOW_ADMIN_USER_PASSWORD_AUTH",
    "ALLOW_CUSTOM_AUTH",
    "ALLOW_REFRESH_TOKEN_AUTH",
    "ALLOW_USER_AUTH",
    "ALLOW_USER_PASSWORD_AUTH",
    "ALLOW_USER_SRP_AUTH",
  ]
  prevent_user_existence_errors                 = "ENABLED"
  enable_token_revocation                       = true
  enable_propagate_additional_user_context_data = false
  auth_session_validity                         = 3

  access_token_validity  = 60
  id_token_validity      = 60
  refresh_token_validity = 5
  token_validity_units {
    access_token  = "minutes"
    id_token      = "minutes"
    refresh_token = "days"
  }

  allowed_oauth_flows                  = ["code"]
  allowed_oauth_flows_user_pool_client = true
  allowed_oauth_scopes                 = ["email", "openid", "profile"]
  supported_identity_providers         = ["COGNITO"]
  callback_urls                        = ["http://localhost:3000/auth/callback"]
  logout_urls                          = ["http://localhost:3000/logout/callback"]

  read_attributes  = []
  write_attributes = []

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_cognito_user_pool_domain" "prod" {
  count = local.adopt_prod_cognito ? 1 : 0

  domain                = var.prod_cognito.domain
  user_pool_id          = aws_cognito_user_pool.prod[0].id
  managed_login_version = 2

  lifecycle {
    prevent_destroy = true
  }
}
