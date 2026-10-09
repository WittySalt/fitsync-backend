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

variable "enable_alb" {
  description = "Put an Application Load Balancer in front of the gateway"
  type        = bool
  default     = true
}

variable "alb_idle_timeout" {
  description = "Seconds the ALB keeps an idle connection (coach replies stream)"
  type        = number
  default     = 120
}

variable "db_multi_az" {
  description = "Run RDS with a synchronous standby in the second AZ (doubles the RDS cost)"
  type        = bool
  default     = false
}

variable "prod_media_bucket" {
  description = "Hand-built prod media bucket that media_prod.tf adopts by import and manages (prod only; empty = nothing adopted)"
  type        = string
  default     = ""
}

variable "existing_media_bucket" {
  description = "Use this existing media bucket instead of creating one (prod: fitsync-media)"
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

variable "notify_email_sender" {
  description = "From address of notification-service emails, verified in SES (empty = no identity, no send permission)"
  type        = string
  default     = ""

  validation {
    condition     = var.notify_email_sender == "" || can(regex("^[^@[:space:]]+@[^@[:space:]]+[.][^@[:space:]]+$", var.notify_email_sender))
    error_message = "notify_email_sender must be empty or one email address."
  }
}

variable "notify_email_create_identity" {
  description = "Create an SES address identity for notify_email_sender in this workspace. False (default) when the sender's domain is verified in bootstrap/email.tf; true only for an address outside that domain"
  type        = bool
  default     = false
}

variable "db_subnet_tier" {
  description = "Subnets of the RDS subnet group: private (target), both (while an existing database moves over) or public (the old layout, for rollback)"
  type        = string
  default     = "private"

  validation {
    condition     = contains(["public", "both", "private"], var.db_subnet_tier)
    error_message = "db_subnet_tier must be one of: public, both, private."
  }
}

variable "ssh_ingress_cidrs" {
  description = "CIDRs allowed to reach port 22 on the app host. Empty (default) = no SSH port; ssh goes over SSM Session Manager. Only for the switch-over to SSM"
  type        = list(string)
  default     = []
}

variable "prod_cognito" {
  description = "IDs of the hand-built prod Cognito pool, app client and domain that cognito_prod.tf adopts with import blocks (prod only; null = nothing adopted)"
  type = object({
    user_pool_id = string
    client_id    = string
    domain       = string
  })
  default = null
}

variable "origin_hostname" {
  description = "Name of the ALB in hosted_zone_name for TLS from CloudFront, e.g. origin.fitsyncapp.click: adds an ACM certificate, an alias record and an HTTPS listener (empty = HTTP only)"
  type        = string
  default     = ""
}

variable "hosted_zone_name" {
  description = "Public hosted zone (owned by bootstrap/email.tf) that holds origin_hostname"
  type        = string
  default     = "fitsyncapp.click"
}

variable "alb_http_listener_enabled" {
  description = "Keep the plain HTTP listener on :80. Set false only after CloudFront reaches the origin over HTTPS"
  type        = bool
  default     = true
}
