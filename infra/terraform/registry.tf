# ── ECR: one repo per backend microservice (free when empty) ─────────────────
resource "aws_ecr_repository" "service" {
  for_each = toset(var.services)

  # Env-prefixed so TF-managed repos never collide with the hand-built prod
  # repos (which are named fitsync-<svc> with no env segment).
  name                 = "${local.name}-${each.value}"
  image_tag_mutability = "MUTABLE"
  force_delete         = true

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = { Name = "${var.project}-${each.value}" }
}

# Keep only the last 10 images per repo to control storage cost.
resource "aws_ecr_lifecycle_policy" "service" {
  for_each   = aws_ecr_repository.service
  repository = each.value.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Expire untagged / keep last 10"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 10
      }
      action = { type = "expire" }
    }]
  })
}
