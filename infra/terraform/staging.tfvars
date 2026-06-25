# ── STAGING — the environment we run for deploy→test→promote ─────────────────
# Apply with:  terraform workspace select staging && terraform apply -var-file=staging.tfvars
# All resources are named fitsync-staging-* and are fully isolated from the
# existing hand-built production environment (which Terraform never touches).
#
# ⚠️ COST: enable_ec2 + enable_rds bill while up. This config stands up the full
# app+DB so stress/DAST can run against it; `terraform destroy` after the demo.
# Also required at apply time:
#   - TF_VAR_db_password   RDS master password (env var; never commit)
#   - ec2_key_name below   must match an EXISTING EC2 key pair; its private half
#                          is the STAGING_EC2_SSH_KEY CI variable

region = "ap-southeast-1"
env    = "staging"

# Full staging environment (EC2 app host + RDS). CloudFront stays OFF — the app
# SG exposes the gateway on :8088 to 0.0.0.0/0, so stress/DAST hit it directly.
enable_rds        = true
enable_ec2        = true
enable_cloudfront = false

# Existing EC2 key pair name (create once: `aws ec2 create-key-pair --key-name
# fitsync-staging ...`). Empty = no SSH key on the instance (deploy_staging_app
# would not be able to connect).
ec2_key_name = "fitsync-staging"

cognito_callback_urls = ["http://localhost:3000/auth/callback"]
cognito_logout_urls   = ["http://localhost:3000/logout/callback"]
