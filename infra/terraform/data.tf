# ── RDS PostgreSQL (flagged: costs money, ~10 min to create) ─────────────────
# The database lives in the private subnets. The group keeps its name and is re-pointed
# in place rather than replaced by a new group, because RDS refuses to move an instance
# to another subnet group in the same VPC ("The specified DB subnet group and DB instance
# are in the same VPC"). RDS also refuses to drop a subnet the instance still uses, so
# an existing database is moved in three steps (RUNBOOK.md "Move RDS into the private
# subnets"): db_subnet_tier = "both" adds the private subnets, a manual Multi-AZ
# failover sequence moves the instance onto them, and "private" then drops the public ones.
locals {
  db_subnet_ids = {
    public  = aws_subnet.public[*].id
    both    = concat(aws_subnet.public[*].id, aws_subnet.private[*].id)
    private = aws_subnet.private[*].id
  }[var.db_subnet_tier]
}

resource "aws_db_subnet_group" "main" {
  count      = var.enable_rds ? 1 : 0
  name       = "${local.name}-db-subnets"
  subnet_ids = local.db_subnet_ids
  tags       = { Name = "${local.name}-db-subnets" }
}

resource "aws_db_instance" "main" {
  count = var.enable_rds ? 1 : 0

  identifier     = "${local.name}-db"
  engine         = "postgres"
  engine_version = var.db_engine_version
  instance_class = var.db_instance_class

  allocated_storage = 20
  storage_type      = "gp3"
  storage_encrypted = true

  # Restoring a snapshot brings its own database and master user.
  snapshot_identifier = var.db_snapshot_identifier
  db_name             = var.db_snapshot_identifier == null ? var.db_name : null
  username            = var.db_snapshot_identifier == null ? var.db_username : null
  # RDS generates the master password, keeps it in Secrets Manager and rotates it; nobody
  # types or stores it. The app host reads it there when it bootstraps the service roles.
  manage_master_user_password = true

  # A synchronous standby in the second AZ with automatic failover (prod).
  multi_az = var.db_multi_az

  db_subnet_group_name   = aws_db_subnet_group.main[0].name
  vpc_security_group_ids = [aws_security_group.db.id]
  publicly_accessible    = false

  backup_retention_period = 7
  apply_immediately       = true

  # Prod keeps a final snapshot and refuses deletion; staging is throwaway.
  deletion_protection       = var.env == "prod"
  skip_final_snapshot       = var.env != "prod"
  final_snapshot_identifier = var.env == "prod" ? "${local.name}-db-final" : null

  lifecycle {
    # Guard against accidental `terraform destroy` wiping the database.
    # Tighten to true once this holds real data.
    prevent_destroy = false
  }

  tags = { Name = "${local.name}-db" }
}
