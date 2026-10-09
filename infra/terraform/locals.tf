data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  name         = "${var.project}-${var.env}"
  account_id   = data.aws_caller_identity.current.account_id
  azs          = slice(data.aws_availability_zones.available.names, 0, var.az_count)
  public_cidrs = [for i in range(var.az_count) : cidrsubnet(var.vpc_cidr, 8, i)]
  # 10.20.10.0/24, 10.20.11.0/24 with the default VPC CIDR.
  private_cidrs = [for i in range(var.az_count) : cidrsubnet(var.vpc_cidr, 8, 10 + i)]
}
