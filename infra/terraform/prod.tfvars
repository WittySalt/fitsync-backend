# ── PROD ─────────────────────────────────────────────────────────────────────
# The hand-built prod environment was torn down on 2026-06-27. Prod is rebuilt by
# Terraform on an EMPTY database (the old snapshots were deleted on purpose):
# Flyway creates every table and seed on first boot. Demo data: see RUNBOOK.md
# "Rebuild" step 4 (scripts/demo-reseed.ps1 only drives the local stack).
#
#   terraform workspace select prod || terraform workspace new prod
#   terraform apply -var-file=prod.tfvars    # with TF_VAR_ec2_public_key
#
# Resources are named fitsync-prod-*. The hand-built Cognito pool (real users) is
# adopted by import (cognito_prod.tf), and so is the media bucket fitsync-media
# (media_prod.tf; prod uploads to it with the instance role, granted here). Kept outside
# this stack: the frontend bucket fitsync-frontend-prod and its CloudFront distribution
# (frontend repo). See RUNBOOK.md "Rebuild".

region = "ap-southeast-1"
env    = "prod"

enable_rds = true
enable_ec2 = true

ec2_instance_type = "t3.large"

# Same PostgreSQL major version as local development and staging, with a standby in AZ-b.
db_engine_version = "16"
db_multi_az       = true

# The existing media bucket (exercise images already uploaded), adopted by import in
# media_prod.tf and never recreated or emptied. Staging gets its own.
prod_media_bucket = "fitsync-media"

# Existing pool ap-southeast-1_myVyuOmPD stays in charge of sign-in. auth.tf builds no
# pool here; cognito_prod.tf adopts the existing one by import and must never recreate it
# (it holds the real users). The client id is the one the SPA (auth.config.ts) and
# auth-service use.
enable_cognito = false
prod_cognito = {
  user_pool_id = "ap-southeast-1_myVyuOmPD"
  client_id    = "4mvse0cgmkplgocfg827uolvad"
  domain       = "ap-southeast-1myvyuompd"
}

# Only the prod CloudFront distribution reaches the gateway, as before the teardown.
api_from_cloudfront_only = true

# From address of notification-service emails. Empty = no send permission (emails
# are only logged). The fitsyncapp.click domain identity lives in bootstrap/email.tf (see
# staging.tfvars). Set the line below once the domain verifies, and keep it equal to
# the production NOTIFY_EMAIL_SENDER variable in GitHub.
notify_email_sender = "noreply@fitsyncapp.click"

# ── Hardening switch-over done (RUNBOOK.md "Hardening switch-over") ──
# 2026-10-09: the database runs on the private subnets (primary and standby) and the host has
# no SSH port; operators and CD reach it through SSM Session Manager. db_subnet_tier and
# ssh_ingress_cidrs use their defaults ("private", no SSH ingress).
