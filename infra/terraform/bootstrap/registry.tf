# ── ECR: one shared repository per service ──────────────────────────────────
# Shared by staging and prod on purpose: CD builds one image per commit, staging
# runs it first, and prod deploys the same tag. The names match what CD pushes
# (fitsync-<service>) and what docker-compose.prod.yml pulls.

variable "services" {
  description = "Backend services that ship an image"
  type        = list(string)
  default = [
    "api-gateway",
    "auth-service",
    "user-service",
    "workout-service",
    "nutrition-service",
    "analytics-service",
    "recommendation-service",
  ]
}

# Six repositories were created by hand on 2026-06-20; adopt them instead of
# recreating. recommendation-service is new and is simply created.
import {
  for_each = toset([
    "api-gateway",
    "auth-service",
    "user-service",
    "workout-service",
    "nutrition-service",
    "analytics-service",
  ])
  to = aws_ecr_repository.service[each.value]
  id = "fitsync-${each.value}"
}

resource "aws_ecr_repository" "service" {
  for_each = toset(var.services)

  name                 = "fitsync-${each.value}"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }
}

# Keep the last 10 images per repository to cap storage cost.
resource "aws_ecr_lifecycle_policy" "service" {
  for_each   = aws_ecr_repository.service
  repository = each.value.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep the last 10 images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 10
      }
      action = { type = "expire" }
    }]
  })
}

output "ecr_repository_urls" {
  description = "Push targets for the CD docker-image job"
  value       = { for k, r in aws_ecr_repository.service : k => r.repository_url }
}
