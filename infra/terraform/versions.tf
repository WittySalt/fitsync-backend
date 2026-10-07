terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    # Generates the service passwords and JWT secret kept in Secrets Manager (secrets.tf).
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # Remote state in S3 so ephemeral CI runners and local share one state.
  # Native S3 locking (use_lockfile, Terraform >= 1.11) — no DynamoDB table.
  # Workspaces store at  s3://<bucket>/env:/<workspace>/<key>.
  backend "s3" {
    bucket       = "fitsync-tfstate-437659978621"
    key          = "fitsync/terraform.tfstate"
    region       = "ap-southeast-1"
    encrypt      = true
    use_lockfile = true
  }
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project   = var.project
      Env       = var.env
      ManagedBy = "terraform"
    }
  }
}

data "aws_caller_identity" "current" {}
