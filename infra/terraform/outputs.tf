output "region" {
  value = var.region
}

output "vpc_id" {
  value = aws_vpc.main.id
}

output "public_subnet_ids" {
  value = aws_subnet.public[*].id
}

output "media_bucket" {
  value = local.media_bucket
}

output "media_public_base_url" {
  description = "FITSYNC_MEDIA_PUBLIC_BASE_URL for this environment"
  value       = "https://${local.media_bucket}.s3.${var.region}.amazonaws.com"
}

output "cognito_user_pool_id" {
  description = "null unless enable_cognito=true"
  value       = var.enable_cognito ? aws_cognito_user_pool.main[0].id : null
}

output "cognito_spa_client_id" {
  value = var.enable_cognito ? aws_cognito_user_pool_client.spa[0].id : null
}

output "cognito_domain" {
  value = var.enable_cognito ? "${aws_cognito_user_pool_domain.main[0].domain}.auth.${var.region}.amazoncognito.com" : null
}

output "ci_deploy_policy_arn" {
  value = aws_iam_policy.ci_deploy.arn
}

output "rds_endpoint" {
  description = "RDS endpoint (null unless enable_rds=true)"
  value       = var.enable_rds ? aws_db_instance.main[0].endpoint : null
}

output "ec2_public_ip" {
  description = "App host EIP (null unless enable_ec2=true)"
  value       = var.enable_ec2 ? aws_eip.app[0].public_ip : null
}

output "alb_dns_name" {
  description = "CloudFront /api origin and the stress/DAST target (null unless enable_alb=true)"
  value       = var.enable_alb ? aws_lb.app[0].dns_name : null
}

output "app_secret_name" {
  description = "Secrets Manager secret the app host reads at deploy time"
  value       = var.enable_rds ? aws_secretsmanager_secret.app[0].name : null
}
