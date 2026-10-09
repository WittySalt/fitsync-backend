# ── VPC, public subnets for the app host and ALB, private subnets for RDS (free) ──
# The app host and the ALB sit in the public subnets. RDS sits in the private subnets,
# whose route table has no internet route and no NAT, so the database cannot reach or
# be reached from outside the VPC whatever its security group says.

resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = { Name = "${local.name}-vpc" }
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id
  tags   = { Name = "${local.name}-igw" }
}

resource "aws_subnet" "public" {
  count                   = var.az_count
  vpc_id                  = aws_vpc.main.id
  cidr_block              = local.public_cidrs[count.index]
  availability_zone       = local.azs[count.index]
  map_public_ip_on_launch = true

  tags = { Name = "${local.name}-public-${count.index}" }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = { Name = "${local.name}-public-rt" }
}

resource "aws_route_table_association" "public" {
  count          = var.az_count
  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

# Data tier: one private subnet per AZ (10.20.10.0/24, 10.20.11.0/24 with the default
# VPC CIDR). Only RDS uses them (data.tf).
resource "aws_subnet" "private" {
  count                   = var.az_count
  vpc_id                  = aws_vpc.main.id
  cidr_block              = local.private_cidrs[count.index]
  availability_zone       = local.azs[count.index]
  map_public_ip_on_launch = false

  tags = { Name = "${local.name}-private-${count.index}" }
}

# No routes besides the implicit local one: no internet gateway, no NAT.
resource "aws_route_table" "private" {
  vpc_id = aws_vpc.main.id
  tags   = { Name = "${local.name}-private-rt" }
}

resource "aws_route_table_association" "private" {
  count          = var.az_count
  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private.id
}

# ── Security groups ──────────────────────────────────────────────────────────
data "aws_ec2_managed_prefix_list" "cloudfront" {
  name = "com.amazonaws.global.cloudfront.origin-facing"
}

resource "aws_security_group" "app" {
  name = "${local.name}-app-sg"
  # The description is older than the move to SSM and is kept as it is: changing it
  # forces a new security group, which would also replace the RDS rule that names it.
  description = "FitSync app host: gateway 8088 + SSH"
  vpc_id      = aws_vpc.main.id

  # With an ALB the gateway port is open to the ALB only. Without one it falls back to
  # the edge rule the ALB would otherwise carry (CloudFront only, or open for demos).
  dynamic "ingress" {
    for_each = var.enable_alb ? [1] : []
    content {
      description     = "API gateway from the ALB"
      from_port       = 8088
      to_port         = 8088
      protocol        = "tcp"
      security_groups = [aws_security_group.alb.id]
    }
  }

  dynamic "ingress" {
    for_each = var.enable_alb ? [] : [1]
    content {
      description     = var.api_from_cloudfront_only ? "API gateway (CloudFront only)" : "API gateway (CloudFront / demo)"
      from_port       = 8088
      to_port         = 8088
      protocol        = "tcp"
      cidr_blocks     = var.api_from_cloudfront_only ? null : ["0.0.0.0/0"]
      prefix_list_ids = var.api_from_cloudfront_only ? [data.aws_ec2_managed_prefix_list.cloudfront.id] : null
    }
  }

  # Operators and CD reach the host with ssh over SSM Session Manager (RUNBOOK.md
  # "SSH over SSM"), which needs no inbound port. Port 22 opens only while
  # var.ssh_ingress_cidrs lists something, which is meant for the switch-over alone.
  dynamic "ingress" {
    for_each = length(var.ssh_ingress_cidrs) > 0 ? [1] : []
    content {
      description = "SSH"
      from_port   = 22
      to_port     = 22
      protocol    = "tcp"
      cidr_blocks = var.ssh_ingress_cidrs
    }
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${local.name}-app-sg" }
}

resource "aws_security_group" "db" {
  name        = "${local.name}-db-sg"
  description = "FitSync RDS: postgres 5432 from app SG only"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "Postgres from app hosts"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.app.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${local.name}-db-sg" }
}
