output "region" {
  value = var.region
}

output "vpc_id" {
  value = aws_vpc.main.id
}

output "public_subnet_ids" {
  value = aws_subnet.public[*].id
}

output "private_subnet_ids" {
  description = "Data-tier subnets (no internet route) that hold RDS, in AZ order"
  value       = aws_subnet.private[*].id
}

output "media_bucket" {
  value = local.media_bucket
}

output "media_public_base_url" {
  description = "FITSYNC_MEDIA_PUBLIC_BASE_URL for this environment"
  value       = "https://${local.media_bucket}.s3.${var.region}.amazonaws.com"
}

output "cognito_user_pool_id" {
  description = "The environment's own pool (enable_cognito), the adopted prod pool (prod_cognito), else null"
  value       = var.enable_cognito ? aws_cognito_user_pool.main[0].id : (local.adopt_prod_cognito ? aws_cognito_user_pool.prod[0].id : null)
}

output "cognito_spa_client_id" {
  value = var.enable_cognito ? aws_cognito_user_pool_client.spa[0].id : (local.adopt_prod_cognito ? aws_cognito_user_pool_client.prod[0].id : null)
}

output "cognito_domain" {
  value = var.enable_cognito ? "${aws_cognito_user_pool_domain.main[0].domain}.auth.${var.region}.amazoncognito.com" : (local.adopt_prod_cognito ? "${aws_cognito_user_pool_domain.prod[0].domain}.auth.${var.region}.amazoncognito.com" : null)
}

output "ci_deploy_policy_arn" {
  value = aws_iam_policy.ci_deploy.arn
}

output "rds_endpoint" {
  description = "RDS endpoint (null unless enable_rds=true)"
  value       = var.enable_rds ? aws_db_instance.main[0].endpoint : null
}

output "ec2_instance_id" {
  description = "App host instance id, the ssh target over SSM Session Manager (null unless enable_ec2=true)"
  value       = var.enable_ec2 ? aws_instance.app[0].id : null
}

output "ssm_operator_policy_arn" {
  description = "Managed policy to attach to an operator's IAM user for ssh over SSM (null unless enable_ec2=true)"
  value       = var.enable_ec2 ? aws_iam_policy.ssm_operator[0].arn : null
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

output "notify_email_identity_arn" {
  description = "SES address identity of the notification sender (null unless notify_email_create_identity; the domain identity is a bootstrap output)"
  value       = length(aws_sesv2_email_identity.notify_sender) > 0 ? aws_sesv2_email_identity.notify_sender[0].arn : null
}
