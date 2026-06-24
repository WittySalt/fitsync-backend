#!/usr/bin/env bash
# Runs ON the production EC2 host. Bootstraps Docker + compose on first run, then
# deploys the full FitSync stack from ECR via docker-compose.prod.yml with a
# health-gated automatic rollback. Invoked by the GitLab `deploy_prod` job over
# SSH; can also be run by hand for debugging.
#
#   Required env : ECR_REGISTRY  IMAGE_TAG  AWS_REGION
#   Optional env : DEPLOY_DIR (default /opt/fitsync)
#
# The runtime .env (DB creds, JWT_SECRET, ...) is rendered by the CI job from
# GitLab CI/CD variables and placed in $DEPLOY_DIR/.env before this runs.
# nginx/TLS is intentionally NOT started here (see EC2 setup runbook) so a fresh
# host without certs can still come up.
set -euo pipefail

ECR_REGISTRY="${ECR_REGISTRY:?ECR_REGISTRY required}"
IMAGE_TAG="${IMAGE_TAG:?IMAGE_TAG required}"
AWS_REGION="${AWS_REGION:?AWS_REGION required}"
DEPLOY_DIR="${DEPLOY_DIR:-/opt/fitsync}"
ROLLBACK_FILE="$DEPLOY_DIR/.previous_prod_tag"

# The runnable images (nginx is handled separately once TLS certs exist). `kafka`
# is in the `events` compose profile (enabled below) and backs the cross-service
# event projections (e.g. goal targets → nutrition daily budget).
SERVICES="redis kafka api-gateway auth-service user-service workout-service nutrition-service analytics-service"

# --profile events so the in-stack kafka broker (profiled) is eligible to start.
compose() { sudo docker compose -f docker-compose.prod.yml --profile events "$@"; }

# ── one-time bootstrap (Amazon Linux 2023) ───────────────────────────────────
if ! command -v docker >/dev/null 2>&1; then
  echo "==> installing Docker"
  sudo dnf install -y docker
  sudo systemctl enable --now docker
  sudo usermod -aG docker "$(id -un)" || true
fi
if ! sudo docker compose version >/dev/null 2>&1; then
  echo "==> installing docker compose plugin"
  sudo mkdir -p /usr/libexec/docker/cli-plugins
  sudo curl -fsSL \
    "https://github.com/docker/compose/releases/download/v2.29.7/docker-compose-linux-$(uname -m)" \
    -o /usr/libexec/docker/cli-plugins/docker-compose
  sudo chmod +x /usr/libexec/docker/cli-plugins/docker-compose
fi
if ! command -v aws >/dev/null 2>&1; then
  echo "==> installing AWS CLI"
  sudo dnf install -y awscli >/dev/null 2>&1 || {
    sudo dnf install -y unzip >/dev/null
    curl -fsSL "https://awscli.amazonaws.com/awscli-exe-linux-$(uname -m).zip" -o /tmp/awscliv2.zip
    unzip -q -o /tmp/awscliv2.zip -d /tmp
    sudo /tmp/aws/install --update
  }
fi

cd "$DEPLOY_DIR"

# ── ECR login (EC2 instance role supplies creds via IMDS) ────────────────────
aws ecr get-login-password --region "$AWS_REGION" \
  | sudo docker login --username AWS --password-stdin "$ECR_REGISTRY"

PREV_TAG="$(cat "$ROLLBACK_FILE" 2>/dev/null || echo '')"

echo "==> pulling images (tag: $IMAGE_TAG)"
compose pull $SERVICES

echo "==> starting stack"
if compose up -d --wait --wait-timeout 180 $SERVICES; then
  echo "$IMAGE_TAG" > "$ROLLBACK_FILE"
  echo "==> deploy OK ($IMAGE_TAG)"
else
  echo "!! stack did not become healthy"
  if [ -n "$PREV_TAG" ] && [ "$PREV_TAG" != "$IMAGE_TAG" ]; then
    echo "==> rolling back to $PREV_TAG"
    IMAGE_TAG="$PREV_TAG" compose up -d --wait --wait-timeout 180 $SERVICES
  fi
  exit 1
fi
