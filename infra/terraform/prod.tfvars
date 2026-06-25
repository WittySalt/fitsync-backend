# ── PROD — RESERVED INTERFACE. DO NOT APPLY BEFORE THE DEFENSE. ───────────────
# This file exists so the prod environment is defined and ready, but it is NOT
# provisioned yet. The live production app currently runs on the existing
# hand-built environment, which Terraform deliberately does not manage.
#
# Post-defense cut-over (see RUNBOOK.md "Cut-over plan"):
#   terraform workspace new prod
#   terraform apply -var-file=prod.tfvars      # stand up the IaC prod env
#   ...deploy services + migrate data, then switch traffic...
#
# Resources are named fitsync-prod-* and do NOT collide with the existing
# unprefixed prod resources (fitsync-db, fitsync-media, fitsync-frontend-prod).

region = "ap-southeast-1"
env    = "prod"

# Reserved: keep paid/compute resources OFF until the deliberate cut-over.
enable_rds        = false
enable_ec2        = false
enable_cloudfront = false

# Point these at the real production frontend origin during cut-over.
cognito_callback_urls = ["http://localhost:3000/auth/callback"]
cognito_logout_urls   = ["http://localhost:3000/logout/callback"]
