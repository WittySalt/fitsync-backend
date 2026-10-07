variable "region" {
  description = "AWS region"
  type        = string
  default     = "ap-southeast-1"
}

variable "project" {
  description = "Project name, used as a resource name prefix"
  type        = string
  default     = "fitsync"
}

variable "env" {
  description = "Environment name (staging / prod). Suffixes all resource names."
  type        = string
  default     = "staging"

  validation {
    condition     = contains(["staging", "prod"], var.env)
    error_message = "env must be one of: staging, prod."
  }
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC"
  type        = string
  default     = "10.20.0.0/16"
}

variable "az_count" {
  description = "Number of AZs / public subnets to spread across"
  type        = number
  default     = 2
}

# ── Cost / time feature flags ────────────────────────────────────────────────
# Free + fast resources (network, ECR, S3, Cognito, IAM) always apply.
# The flags below gate resources that cost money or take minutes to provision,
# so the first `apply` is free and instant. Flip to true when you want to demo
# the full environment, then `terraform destroy` to stop the bill.

variable "enable_rds" {
  description = "Create the RDS PostgreSQL instance (costs money, ~10 min to create)"
  type        = bool
  default     = false
}

variable "enable_ec2" {
  description = "Create the EC2 application host (costs money)"
  type        = bool
  default     = false
}

# ── App config ───────────────────────────────────────────────────────────────
variable "db_instance_class" {
  description = "RDS instance class"
  type        = string
  default     = "db.t3.micro"
}

variable "db_name" {
  description = "Initial database name"
  type        = string
  default     = "fitsync"
}

variable "db_username" {
  description = "RDS master username"
  type        = string
  default     = "fitsync_master"
}

variable "db_password" {
  description = "RDS master password (set via TF_VAR_db_password env var, never commit)"
  type        = string
  default     = ""
  sensitive   = true
}

variable "ec2_instance_type" {
  description = "EC2 instance type for the app host"
  type        = string
  default     = "t3.small"
}

variable "ec2_public_key" {
  description = "SSH public key for the deploy key pair (preferred over ec2_key_name)"
  type        = string
  default     = ""
}

variable "enable_cognito" {
  description = "Create a Cognito user pool for this environment (false = use an existing pool)"
  type        = bool
  default     = true
}

variable "db_snapshot_identifier" {
  description = "Restore RDS from this snapshot instead of creating an empty database"
  type        = string
  default     = null
}

variable "db_engine_version" {
  description = "PostgreSQL major version (must match the snapshot when restoring)"
  type        = string
  default     = "16"
}

variable "api_from_cloudfront_only" {
  description = "Allow the gateway port only from CloudFront's origin-facing IP ranges"
  type        = bool
  default     = false
}

variable "ec2_key_name" {
  description = "Existing EC2 key pair name for SSH (optional, empty = no key)"
  type        = string
  default     = ""
}

variable "cognito_callback_urls" {
  description = "Allowed OAuth callback URLs for the SPA client"
  type        = list(string)
  default     = ["http://localhost:3000/auth/callback"]
}

variable "cognito_logout_urls" {
  description = "Allowed sign-out URLs for the SPA client"
  type        = list(string)
  default     = ["http://localhost:3000/logout/callback"]
}

variable "bedrock_models" {
  description = "Bedrock foundation model ID patterns the app host may invoke (coach AI tier)"
  type        = list(string)
  default = [
    "anthropic.claude-haiku-4-5-*",
    "amazon.nova-micro-v1:0",
    "amazon.nova-lite-v1:0",
  ]
}
