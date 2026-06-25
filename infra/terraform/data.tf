# ── RDS PostgreSQL (flagged: costs money, ~10 min to create) ─────────────────
resource "aws_db_subnet_group" "main" {
  count      = var.enable_rds ? 1 : 0
  name       = "${local.name}-db-subnets"
  subnet_ids = aws_subnet.public[*].id
  tags       = { Name = "${local.name}-db-subnets" }
}

resource "aws_db_instance" "main" {
  count = var.enable_rds ? 1 : 0

  identifier     = "${local.name}-db"
  engine         = "postgres"
  engine_version = "16"
  instance_class = var.db_instance_class

  allocated_storage = 20
  storage_type      = "gp3"
  storage_encrypted = true

  db_name  = var.db_name
  username = var.db_username
  password = var.db_password

  db_subnet_group_name   = aws_db_subnet_group.main[0].name
  vpc_security_group_ids = [aws_security_group.db.id]
  publicly_accessible    = false

  backup_retention_period = 7
  skip_final_snapshot     = true
  apply_immediately       = true

  lifecycle {
    # Guard against accidental `terraform destroy` wiping the database.
    # Tighten to true once this holds real data.
    prevent_destroy = false
  }

  tags = { Name = "${local.name}-db" }
}
