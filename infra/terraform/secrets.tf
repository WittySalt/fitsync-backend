# ── Secrets Manager: everything the app host needs to know ──────────────────
# One secret per environment, fitsync-<env>/app, holding the database endpoint, the
# seven service passwords and the JWT signing key. Terraform generates the values; the
# EC2 instance role reads them at deploy time (scripts/ec2-deploy.sh), so none of them
# lives in GitHub. The RDS master password is a separate secret that RDS manages and
# rotates itself (manage_master_user_password in data.tf).

locals {
  db_services = ["auth", "user", "workout", "nutrition", "analytics", "recommendation", "notification"]
}

resource "random_password" "db" {
  for_each = var.enable_rds ? toset(local.db_services) : toset([])

  length  = 32
  special = false # ends up in a JDBC .env line and a psql variable
}

resource "random_password" "jwt" {
  length  = 64
  special = false
}

resource "aws_secretsmanager_secret" "app" {
  count = var.enable_rds ? 1 : 0

  name        = "${local.name}/app"
  description = "FitSync ${var.env}: database endpoint, service passwords, JWT key"
  # Staging is destroyed and rebuilt often; a recovery window would block reusing the name.
  recovery_window_in_days = var.env == "prod" ? 7 : 0

  tags = { Name = "${local.name}-app-secret" }
}

resource "aws_secretsmanager_secret_version" "app" {
  count = var.enable_rds ? 1 : 0

  secret_id = aws_secretsmanager_secret.app[0].id
  secret_string = jsonencode(merge(
    {
      db_host              = aws_db_instance.main[0].address
      db_port              = aws_db_instance.main[0].port
      db_name              = var.db_name
      db_master_secret_arn = aws_db_instance.main[0].master_user_secret[0].secret_arn
      jwt_secret           = random_password.jwt.result
    },
    { for s in local.db_services : "${s}_db_password" => random_password.db[s].result },
  ))
}

data "aws_iam_policy_document" "ec2_secrets" {
  count = var.enable_rds ? 1 : 0

  statement {
    sid     = "ReadAppSecrets"
    actions = ["secretsmanager:GetSecretValue"]
    resources = [
      aws_secretsmanager_secret.app[0].arn,
      aws_db_instance.main[0].master_user_secret[0].secret_arn,
    ]
  }
}

resource "aws_iam_role_policy" "ec2_secrets" {
  count = var.enable_rds ? 1 : 0

  name   = "${local.name}-ec2-secrets"
  role   = aws_iam_role.ec2_app.id
  policy = data.aws_iam_policy_document.ec2_secrets[0].json
}
