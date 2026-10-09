# ── EC2 application host (flagged: costs money) ──────────────────────────────
data "aws_ami" "al2023" {
  most_recent = true
  owners      = ["amazon"]

  # The plain AL2023 image. A looser "al2023-ami-*" also matches the ECS and Neuron variants,
  # which ship an ecs-agent container this host never uses.
  filter {
    name   = "name"
    values = ["al2023-ami-2023.*-kernel-6.1-x86_64"]
  }
  filter {
    name   = "architecture"
    values = ["x86_64"]
  }
}

# CD and operators ssh in through SSM Session Manager (no port 22), which still
# authenticates with this key; its private half lives only in the <ENV>_EC2_SSH_KEY secret.
resource "aws_key_pair" "app" {
  count = var.enable_ec2 && var.ec2_public_key != "" ? 1 : 0

  key_name   = "${local.name}-deploy"
  public_key = var.ec2_public_key
}

resource "aws_instance" "app" {
  count = var.enable_ec2 ? 1 : 0

  ami                    = data.aws_ami.al2023.id
  instance_type          = var.ec2_instance_type
  subnet_id              = aws_subnet.public[0].id
  vpc_security_group_ids = [aws_security_group.app.id]
  iam_instance_profile   = aws_iam_instance_profile.ec2_app.name
  key_name               = var.ec2_public_key != "" ? aws_key_pair.app[0].key_name : (var.ec2_key_name != "" ? var.ec2_key_name : null)

  # Mirrors the bootstrap in backend/scripts/ec2-deploy.sh: install docker +
  # compose plugin so the CD deploy job can render docker-compose.prod.yml.
  user_data = <<-EOF
    #!/bin/bash
    set -euo pipefail
    dnf install -y docker
    systemctl enable --now docker
    mkdir -p /usr/libexec/docker/cli-plugins
    curl -fsSL \
      "https://github.com/docker/compose/releases/download/v2.29.7/docker-compose-linux-$(uname -m)" \
      -o /usr/libexec/docker/cli-plugins/docker-compose
    chmod +x /usr/libexec/docker/cli-plugins/docker-compose
    mkdir -p /opt/fitsync
  EOF

  # Containers sit one network hop behind the host, so the instance role (Bedrock, ECR)
  # is only reachable from inside docker with a hop limit of 2. IMDSv2 stays mandatory.
  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 2
  }

  root_block_device {
    volume_size = 30
    volume_type = "gp3"
  }

  tags = { Name = "${local.name}-app" }

  # A new AL2023 release must not silently replace the host on the next apply; move to a
  # newer image on purpose with `terraform apply -replace='aws_instance.app[0]'`.
  # user_data runs only on the first boot, and its hash depends on the line endings of the
  # checkout that applied it (prod was applied from a CRLF Windows checkout, staging from
  # CD's LF one), so a later change would only stop and start a running host for nothing.
  lifecycle {
    ignore_changes = [ami, user_data]
  }
}

resource "aws_eip" "app" {
  count    = var.enable_ec2 ? 1 : 0
  instance = aws_instance.app[0].id
  domain   = "vpc"
  tags     = { Name = "${local.name}-app-eip" }
}
