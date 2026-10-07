# ── PROD ─────────────────────────────────────────────────────────────────────
# The hand-built prod environment was torn down on 2026-06-27 (EC2, RDS and EIP
# gone; RDS snapshot kept). Prod is now rebuilt by Terraform from that snapshot:
#
#   terraform workspace select prod || terraform workspace new prod
#   terraform apply -var-file=prod.tfvars    # with TF_VAR_db_password, TF_VAR_ec2_public_key
#
# Resources are named fitsync-prod-*. Kept outside Terraform on purpose: the
# Cognito pool (real users), the media bucket fitsync-media, the frontend bucket
# fitsync-frontend-prod and its CloudFront distribution. See RUNBOOK.md "Rebuild".

region = "ap-southeast-1"
env    = "prod"

enable_rds = true
enable_ec2 = true

ec2_instance_type = "t3.large"

# The 2026-06-27 teardown snapshot of the old fitsync-db (PostgreSQL 18.3).
db_snapshot_identifier = "fitsync-db-teardown-20260627-1945"
db_engine_version      = "18"

# Existing pool ap-southeast-1_myVyuOmPD stays in charge of sign-in.
enable_cognito = false

# Only the prod CloudFront distribution reaches the gateway, as before the teardown.
api_from_cloudfront_only = true
