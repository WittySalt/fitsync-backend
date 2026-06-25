output "region" {
  value = var.region
}

output "vpc_id" {
  value = aws_vpc.main.id
}

output "public_subnet_ids" {
  value = aws_subnet.public[*].id
}

output "ecr_repository_urls" {
  description = "Push targets for the CI build job"
  value       = { for k, r in aws_ecr_repository.service : k => r.repository_url }
}

output "media_bucket" {
  value = aws_s3_bucket.media.bucket
}

output "cognito_user_pool_id" {
  value = aws_cognito_user_pool.main.id
}

output "cognito_spa_client_id" {
  value = aws_cognito_user_pool_client.spa.id
}

output "cognito_domain" {
  value = "${aws_cognito_user_pool_domain.main.domain}.auth.${var.region}.amazoncognito.com"
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
