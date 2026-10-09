# ── TLS from CloudFront to the ALB (free: ACM public certificates cost nothing) ─
# With var.origin_hostname set, the ALB gets a name in the account's public zone (owned by
# bootstrap/email.tf, only looked up here), an ACM certificate for that name in this region,
# and an HTTPS listener on 443. CloudFront then reaches the API at https://<origin_hostname>
# with origin_protocol_policy = "https-only" (frontend/infra/terraform). The /api behaviour
# forwards every viewer header except Host (Managed-AllViewerExceptHostHeader), so CloudFront
# sends the origin hostname as Host and SNI and checks the certificate against it.
#
# The HTTP listener on 80 stays until var.alb_http_listener_enabled is set to false, which
# is done only after the frontend has switched its origin (RUNBOOK.md "TLS from CloudFront
# to the ALB"). Nothing here exists while origin_hostname is empty.

locals {
  origin_https = var.enable_alb && var.origin_hostname != ""
}

data "aws_route53_zone" "public" {
  count        = local.origin_https ? 1 : 0
  name         = var.hosted_zone_name
  private_zone = false
}

resource "aws_acm_certificate" "origin" {
  count             = local.origin_https ? 1 : 0
  domain_name       = var.origin_hostname
  validation_method = "DNS"

  tags = { Name = "${local.name}-origin" }

  lifecycle {
    create_before_destroy = true

    precondition {
      condition     = endswith(var.origin_hostname, ".${var.hosted_zone_name}")
      error_message = "origin_hostname must be a name inside hosted_zone_name."
    }
  }
}

resource "aws_route53_record" "origin_cert_validation" {
  for_each = local.origin_https ? {
    for dvo in aws_acm_certificate.origin[0].domain_validation_options : dvo.domain_name => {
      name   = dvo.resource_record_name
      type   = dvo.resource_record_type
      record = dvo.resource_record_value
    }
  } : {}

  zone_id         = data.aws_route53_zone.public[0].zone_id
  name            = each.value.name
  type            = each.value.type
  records         = [each.value.record]
  ttl             = 300
  allow_overwrite = true
}

resource "aws_acm_certificate_validation" "origin" {
  count                   = local.origin_https ? 1 : 0
  certificate_arn         = aws_acm_certificate.origin[0].arn
  validation_record_fqdns = [for r in aws_route53_record.origin_cert_validation : r.fqdn]
}

resource "aws_route53_record" "origin" {
  count = local.origin_https ? 1 : 0

  zone_id = data.aws_route53_zone.public[0].zone_id
  name    = var.origin_hostname
  type    = "A"

  alias {
    name                   = aws_lb.app[0].dns_name
    zone_id                = aws_lb.app[0].zone_id
    evaluate_target_health = false
  }
}

resource "aws_lb_listener" "https" {
  count = local.origin_https ? 1 : 0

  load_balancer_arn = aws_lb.app[0].arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = aws_acm_certificate_validation.origin[0].certificate_arn

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.gateway[0].arn
  }
}
