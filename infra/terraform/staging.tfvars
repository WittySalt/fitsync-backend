# ── STAGING — the environment we actually run now ────────────────────────────
# Apply with:  terraform workspace select staging && terraform apply -var-file=staging.tfvars
# All resources are named fitsync-staging-* and are fully isolated from the
# existing hand-built production environment (which Terraform never touches).

region = "ap-southeast-1"
env    = "staging"

# Free + instant footprint only. Flip these on (one at a time) when you want to
# demo the full environment, then `terraform destroy` to stop the bill.
enable_rds        = false
enable_ec2        = false
enable_cloudfront = false

cognito_callback_urls = ["http://localhost:3000/auth/callback"]
cognito_logout_urls   = ["http://localhost:3000/logout/callback"]
