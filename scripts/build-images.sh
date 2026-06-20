#!/usr/bin/env bash
# Build a runtime Docker image for each runnable FitSync service from the
# universal backend/Dockerfile. Run AFTER `mvn -DskipTests package`.
#
# Usage:
#   scripts/build-images.sh                                  # build all, tag :latest
#   IMAGE_TAG=$(git rev-parse --short HEAD) scripts/build-images.sh
#   ECR_REGISTRY=123456789012.dkr.ecr.ap-southeast-1.amazonaws.com \
#     PUSH=1 scripts/build-images.sh                         # also tag + push to ECR
#
# recommendation-service / notification-service are intentionally skipped — they
# are empty placeholder modules (pom.xml only, no source) and are not runnable.
set -euo pipefail

cd "$(dirname "$0")/.."   # backend root (Docker build context)

IMAGE_TAG="${IMAGE_TAG:-latest}"
ECR_REGISTRY="${ECR_REGISTRY:-}"
PUSH="${PUSH:-0}"

# service -> documented container port (must match application.yml SERVER_PORT default)
SERVICES=(
  "api-gateway:8088"
  "auth-service:8080"
  "user-service:8081"
  "workout-service:8082"
  "nutrition-service:8083"
  "analytics-service:8085"
)

for entry in "${SERVICES[@]}"; do
  svc="${entry%%:*}"
  port="${entry##*:}"
  local_tag="fitsync-${svc}:${IMAGE_TAG}"

  echo "==> building ${local_tag} (port ${port})"
  docker build -f Dockerfile \
    --build-arg SERVICE="${svc}" \
    --build-arg SERVER_PORT="${port}" \
    -t "${local_tag}" .

  if [[ -n "${ECR_REGISTRY}" ]]; then
    remote_tag="${ECR_REGISTRY}/fitsync-${svc}:${IMAGE_TAG}"
    docker tag "${local_tag}" "${remote_tag}"
    if [[ "${PUSH}" == "1" ]]; then
      echo "==> pushing ${remote_tag}"
      docker push "${remote_tag}"
    fi
  fi
done

echo "done."
