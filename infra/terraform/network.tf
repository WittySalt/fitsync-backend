# ── VPC + public subnets (free) ──────────────────────────────────────────────
# A minimal public-subnet topology: enough to host the EC2 app box and an RDS
# subnet group. A production hardening step is to split app (public) from data
# (private) subnets + NAT; kept flat here for a fast, free first apply.

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

# ── Security groups ──────────────────────────────────────────────────────────
data "aws_ec2_managed_prefix_list" "cloudfront" {
  name = "com.amazonaws.global.cloudfront.origin-facing"
}

resource "aws_security_group" "app" {
  name        = "${local.name}-app-sg"
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

  ingress {
    description = "SSH"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
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
