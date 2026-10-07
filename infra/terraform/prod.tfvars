# ── PROD ─────────────────────────────────────────────────────────────────────
# The hand-built prod environment was torn down on 2026-06-27. Prod is rebuilt by
# Terraform on an EMPTY database (the old snapshots were deleted on purpose):
# Flyway creates every table and seed on first boot. Demo data: see RUNBOOK.md
# "Rebuild" step 4 (scripts/demo-reseed.ps1 only drives the local stack).
#
#   terraform workspace select prod || terraform workspace new prod
#   terraform apply -var-file=prod.tfvars    # with TF_VAR_ec2_public_key
#
# Resources are named fitsync-prod-*. Kept outside Terraform on purpose: the
# Cognito pool (real users), the media bucket fitsync-media (prod uploads to it
# with the instance role, granted here), the frontend bucket
# fitsync-frontend-prod and its CloudFront distribution. See RUNBOOK.md "Rebuild".

region = "ap-southeast-1"
env    = "prod"

enable_rds = true
enable_ec2 = true

ec2_instance_type = "t3.large"

# Same PostgreSQL major version as local development and staging, with a standby in AZ-b.
db_engine_version = "16"
db_multi_az       = true

# The existing media bucket (exercise images already uploaded). Staging gets its own.
existing_media_bucket = "fitsync-media"

# Existing pool ap-southeast-1_myVyuOmPD stays in charge of sign-in.
enable_cognito = false

# Only the prod CloudFront distribution reaches the gateway, as before the teardown.
api_from_cloudfront_only = true
