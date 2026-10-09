# ── STAGING — the environment we run for deploy→test→promote ─────────────────
# Apply with:  terraform workspace select staging && terraform apply -var-file=staging.tfvars
# All resources are named fitsync-staging-* and are fully isolated from prod
# (the prod workspace, prod.tfvars).
#
# ⚠️ COST: enable_ec2 + enable_rds bill while up. This config stands up the full
# app+DB so stress/DAST can run against it (through CloudFront); `terraform destroy`
# after the demo.
# Also required at apply time:
#   - (no DB password: RDS manages the master one, Terraform generates the rest into Secrets Manager)
#   - TF_VAR_ec2_public_key  SSH public key for the deploy key pair; its private half
#                            is the STAGING_EC2_SSH_KEY secret (CI reads the public
#                            half from the STAGING_EC2_PUBLIC_KEY variable)

region = "ap-southeast-1"
env    = "staging"

# Full staging environment (EC2 app host + RDS). The gateway port is open to the ALB
# only, and the ALB to CloudFront only, as in prod: everything reaches staging through
# https://staging.fitsyncapp.click (the distribution is owned by frontend/infra/terraform/).
# Stress and DAST target that address too.
enable_rds = true
enable_ec2 = true

api_from_cloudfront_only = true

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

# ── Switch-over to SSM (RUNBOOK.md "Hardening switch-over") ──
# The database moved onto the private subnets on 2026-10-09 (db_subnet_tier now defaults to
# "private"). Port 22 stays open until CD has deployed through SSM once; delete this line
# then (the default is no SSH port).
ssh_ingress_cidrs = ["0.0.0.0/0"]
