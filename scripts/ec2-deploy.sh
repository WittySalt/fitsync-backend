#!/usr/bin/env bash
# Runs ON the production EC2 host. Bootstraps Docker + compose on first run, then
# deploys the full FitSync stack from ECR via docker-compose.prod.yml with a
# health-gated automatic rollback. Invoked by the GitHub Actions `deploy-prod` job over
# SSH; can also be run by hand for debugging.
#
#   Required env : ECR_REGISTRY  IMAGE_TAG  AWS_REGION  APP_SECRET_ID
#   Optional env : DEPLOY_DIR (default /opt/fitsync)
#
# CI renders the non-secret settings into $DEPLOY_DIR/.env before this runs; this
# script adds the DB endpoint, service passwords and JWT key from Secrets Manager,
# and bootstraps the per-service DB roles (bootstrap-db.sql) on every deploy.
# nginx/TLS is intentionally NOT started here (see EC2 setup runbook) so a fresh
# host without certs can still come up.
set -euo pipefail

APP_SECRET_ID="${APP_SECRET_ID:?APP_SECRET_ID required (fitsync-<env>/app)}"
ECR_REGISTRY="${ECR_REGISTRY:?ECR_REGISTRY required}"
IMAGE_TAG="${IMAGE_TAG:?IMAGE_TAG required}"
AWS_REGION="${AWS_REGION:?AWS_REGION required}"
DEPLOY_DIR="${DEPLOY_DIR:-/opt/fitsync}"
ROLLBACK_FILE="$DEPLOY_DIR/.previous_prod_tag"

# The runnable images (nginx is handled separately once TLS certs exist). `kafka`
# is in the `events` compose profile (enabled below) and backs the cross-service
# event projections (e.g. goal targets → nutrition daily budget).
INFRA_SERVICES="redis kafka"
APP_SERVICES="api-gateway auth-service user-service workout-service nutrition-service analytics-service recommendation-service notification-service"
SERVICES="$INFRA_SERVICES $APP_SERVICES"

# --profile events so the in-stack kafka broker (profiled) is eligible to start.
# IMAGE_TAG is passed through sudo explicitly: sudo resets the environment, so without
# it compose would read IMAGE_TAG from .env (the new tag) and a rollback would re-up
# the very images that just failed.
compose() { sudo env IMAGE_TAG="$IMAGE_TAG" docker compose -f docker-compose.prod.yml --profile events "$@"; }

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

# ── secrets from Secrets Manager (EC2 instance role) ─────────────────────────
# fitsync-<env>/app holds the DB endpoint, the seven service passwords and the JWT key;
# the RDS master credentials live in the secret RDS manages (and rotates) itself.
# Nothing here is passed in by CI, and nothing is echoed.
secret_json() {
  aws secretsmanager get-secret-value --region "$AWS_REGION" --secret-id "$1" \
    --query SecretString --output text
}
json_field() { python3 -c 'import json,sys; print(json.load(sys.stdin)[sys.argv[1]])' "$1"; }

APP_SECRET="$(secret_json "$APP_SECRET_ID")"
DB_HOST="$(json_field db_host <<<"$APP_SECRET")"
DB_PORT="$(json_field db_port <<<"$APP_SECRET")"
DB_NAME="$(json_field db_name <<<"$APP_SECRET")"
MASTER_SECRET="$(secret_json "$(json_field db_master_secret_arn <<<"$APP_SECRET")")"

# ── per-service DB roles + schemas (idempotent; also re-applies passwords) ───
umask 077
BOOT_DIR="$(mktemp -d)"
trap 'rm -rf "$BOOT_DIR"' EXIT
{
  echo "PGPASSWORD=$(json_field password <<<"$MASTER_SECRET")"
} > "$BOOT_DIR/pg.env"
{
  for svc in auth user workout nutrition analytics recommendation notification; do
    echo "\\set ${svc}_pw '$(json_field "${svc}_db_password" <<<"$APP_SECRET")'"
  done
} > "$BOOT_DIR/vars.sql"
echo "==> bootstrapping DB roles + schemas"
sudo docker run --rm --env-file "$BOOT_DIR/pg.env" \
  -v "$BOOT_DIR/vars.sql:/vars.sql:ro" -v "$DEPLOY_DIR/bootstrap-db.sql:/b.sql:ro" \
  postgres:16 psql -q -h "$DB_HOST" -p "$DB_PORT" -U "$(json_field username <<<"$MASTER_SECRET")" \
  -d "$DB_NAME" -v ON_ERROR_STOP=1 -f /vars.sql -f /b.sql

# ── runtime .env: CI's rendered settings + the secrets above ─────────────────
{
  grep -vE '^(DB_URL|[A-Z]+_DB_PASSWORD|JWT_SECRET)=' .env || true
  echo "DB_URL=jdbc:postgresql://$DB_HOST:$DB_PORT/$DB_NAME"
  for svc in auth user workout nutrition analytics recommendation notification; do
    echo "$(tr '[:lower:]' '[:upper:]' <<<"$svc")_DB_PASSWORD=$(json_field "${svc}_db_password" <<<"$APP_SECRET")"
  done
  echo "JWT_SECRET=$(json_field jwt_secret <<<"$APP_SECRET")"
} > .env.next
mv .env.next .env
umask 022

# ── ECR login (EC2 instance role supplies creds via IMDS) ────────────────────
aws ecr get-login-password --region "$AWS_REGION" \
  | sudo docker login --username AWS --password-stdin "$ECR_REGISTRY"

PREV_TAG="$(cat "$ROLLBACK_FILE" 2>/dev/null || echo '')"

echo "==> pulling images (tag: $IMAGE_TAG)"
compose pull $SERVICES

# Two phases. Infra (redis, kafka) is brought up and health-checked first, while the host
# is quiet, so a broken broker still fails the deploy. The app services are then started
# and waited on by themselves. Kafka is no depends_on of any service, so it is not in the
# second wait set: its probe starts a JVM, and during the burst of eight JVM services
# starting at once it could time out and fail a deploy whose broker was fine all along.
# redis stays in both, as a service_healthy dependency of the services.
bring_up() {
  compose up -d --wait --wait-timeout 300 $INFRA_SERVICES \
    && compose up -d --wait --wait-timeout 300 $APP_SERVICES
}

echo "==> starting stack"
if bring_up; then
  echo "$IMAGE_TAG" > "$ROLLBACK_FILE"
  echo "==> deploy OK ($IMAGE_TAG)"
else
  echo "!! stack did not become healthy — dumping container state + recent logs:"
  # Without this, the CI trace only shows compose orchestration, never WHY a
  # container is unhealthy. Surface ps + each service's last log lines.
  compose ps || true
  compose logs --no-color --tail 120 || true
  if [ -n "$PREV_TAG" ] && [ "$PREV_TAG" != "$IMAGE_TAG" ]; then
    echo "==> rolling back to $PREV_TAG"
    IMAGE_TAG="$PREV_TAG" bring_up
  fi
  exit 1
fi
