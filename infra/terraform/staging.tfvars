# ── STAGING — the environment we run for deploy→test→promote ─────────────────
# Apply with:  terraform workspace select staging && terraform apply -var-file=staging.tfvars
# All resources are named fitsync-staging-* and are fully isolated from prod
# (the prod workspace, prod.tfvars).
#
# ⚠️ COST: enable_ec2 + enable_rds bill while up. This config stands up the full
# app+DB so stress/DAST can run against it; `terraform destroy` after the demo.
# Also required at apply time:
#   - (no DB password: RDS manages the master one, Terraform generates the rest into Secrets Manager)
#   - TF_VAR_ec2_public_key  SSH public key for the deploy key pair; its private half
#                            is the STAGING_EC2_SSH_KEY secret (CI reads the public
#                            half from the STAGING_EC2_PUBLIC_KEY variable)

region = "ap-southeast-1"
env    = "staging"

# Full staging environment (EC2 app host + RDS). The app SG exposes the gateway
# on :8088 to 0.0.0.0/0, so stress/DAST hit it directly (frontend CloudFront, if
# any, is now owned by frontend/infra/terraform/).
enable_rds = true
enable_ec2 = true

# The full 7-service stack + kafka + redis idles at ~3 GB with ~7 GB of container
# limits; the t3.small default (2 GB) cannot run it. Match prod's t3.large, and
# move both to t3.xlarge if deploys hit the health-check timeout.
ec2_instance_type = "t3.large"

cognito_callback_urls = ["http://localhost:3000/auth/callback"]
cognito_logout_urls   = ["http://localhost:3000/logout/callback"]

# From address of notification-service emails. Empty = no send permission (emails
# are only logged). The fitsyncapp.click domain identity lives in bootstrap/email.tf, so
# this workspace creates none (notify_email_create_identity defaults to false). Set
# the line below once bootstrap's notify_email_domain_dkim_status reads SUCCESS, and
# keep it equal to the staging NOTIFY_EMAIL_SENDER variable in GitHub.
notify_email_sender = "noreply-staging@fitsyncapp.click"

# ── Switch-over to private DB subnets and SSM (RUNBOOK.md "Hardening switch-over") ──
# Transitional values. "both" keeps the public subnets in the DB subnet group while the
# database moves onto the private ones, and port 22 stays open until CD connects through
# SSM. Delete both lines (the defaults are "private" and no SSH port) in the commit that
# finishes the switch-over for this environment; until then a CD apply changes neither.
db_subnet_tier    = "both"
ssh_ingress_cidrs = ["0.0.0.0/0"]
