# ── Application Load Balancer in front of the gateway ────────────────────────
# CloudFront → ALB (:80) → EC2 gateway (:8088). The ALB is the stable origin, so the
# app host can be replaced without touching CloudFront, and it health-checks the
# gateway. It spans both public subnets (AZ-a, AZ-b), ready for a second app host.
# CloudFront → ALB is plain HTTP on :80 unless var.origin_hostname is set: then the ALB
# also answers HTTPS on :443 with an ACM certificate for that name (origin_tls.tf), and
# var.alb_http_listener_enabled = false closes :80 once CloudFront uses HTTPS.

locals {
  # One ingress rule covers both listener ports. The CloudFront prefix list counts as about
  # 55 rules against the security group's limit of 60, so a second rule referencing it would
  # not fit. The ALB only answers on the ports it has listeners for (80 and 443).
  alb_ingress_from = var.alb_http_listener_enabled ? 80 : 443
  alb_ingress_to   = local.origin_https ? 443 : 80
  alb_ingress_desc = join(" + ", compact([
    var.alb_http_listener_enabled ? "HTTP" : "",
    local.origin_https ? "HTTPS" : "",
  ]))
}

resource "aws_security_group" "alb" {
  name        = "${local.name}-alb-sg"
  description = "FitSync ALB: HTTP from CloudFront (prod) or anywhere (staging demos)"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = var.api_from_cloudfront_only ? "${local.alb_ingress_desc} from CloudFront only" : "${local.alb_ingress_desc} (CloudFront / demo)"
    from_port       = local.alb_ingress_from
    to_port         = local.alb_ingress_to
    protocol        = "tcp"
    cidr_blocks     = var.api_from_cloudfront_only ? null : ["0.0.0.0/0"]
    prefix_list_ids = var.api_from_cloudfront_only ? [data.aws_ec2_managed_prefix_list.cloudfront.id] : null
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${local.name}-alb-sg" }

  lifecycle {
    precondition {
      condition     = var.alb_http_listener_enabled || local.origin_https
      error_message = "alb_http_listener_enabled = false needs origin_hostname set: the ALB would have no listener."
    }
  }
}

resource "aws_lb" "app" {
  count = var.enable_alb ? 1 : 0

  name               = "${local.name}-alb"
  load_balancer_type = "application"
  internal           = false
  security_groups    = [aws_security_group.alb.id]
  subnets            = aws_subnet.public[*].id

  # Matches the gateway's two-minute read timeout for the coach, whose replies stream.
  idle_timeout               = var.alb_idle_timeout
  drop_invalid_header_fields = true

  tags = { Name = "${local.name}-alb" }
}

resource "aws_lb_target_group" "gateway" {
  count = var.enable_alb ? 1 : 0

  name     = "${local.name}-gateway"
  port     = 8088
  protocol = "HTTP"
  vpc_id   = aws_vpc.main.id

  health_check {
    path                = "/actuator/health"
    matcher             = "200"
    interval            = 15
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }

  # Let in-flight coach streams finish before a target leaves.
  deregistration_delay = 30

  tags = { Name = "${local.name}-gateway" }
}

resource "aws_lb_target_group_attachment" "gateway" {
  count = var.enable_alb && var.enable_ec2 ? 1 : 0

  target_group_arn = aws_lb_target_group.gateway[0].arn
  target_id        = aws_instance.app[0].id
  port             = 8088
}

resource "aws_lb_listener" "http" {
  count = var.enable_alb && var.alb_http_listener_enabled ? 1 : 0

  load_balancer_arn = aws_lb.app[0].arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.gateway[0].arn
  }
}
