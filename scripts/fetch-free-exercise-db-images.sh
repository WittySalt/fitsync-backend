#!/usr/bin/env bash
# Fetch free-exercise-db exercise images into the local ignored cache used by docker compose.
set -euo pipefail

cd "$(dirname "$0")/.."

REPO_URL="${FREE_EXERCISE_DB_REPO_URL:-https://github.com/yuhonas/free-exercise-db.git}"
REF="${FREE_EXERCISE_DB_REF:-main}"
CACHE_DIR="${FREE_EXERCISE_DB_CACHE_DIR:-.local/free-exercise-db}"

if [[ -e "${CACHE_DIR}" && ! -d "${CACHE_DIR}/.git" ]]; then
  echo "Refusing to overwrite non-git path: ${CACHE_DIR}" >&2
  exit 1
fi

mkdir -p "$(dirname "${CACHE_DIR}")"

if [[ -d "${CACHE_DIR}/.git" ]]; then
  git -C "${CACHE_DIR}" fetch --depth 1 origin "${REF}"
  git -C "${CACHE_DIR}" checkout --detach FETCH_HEAD
else
  git clone --depth 1 --filter=blob:none --sparse --branch "${REF}" "${REPO_URL}" "${CACHE_DIR}"
fi

git -C "${CACHE_DIR}" sparse-checkout set exercises

echo "free-exercise-db exercises available at ${CACHE_DIR}/exercises"
echo "docker compose mounts this path read-only into /opt/fitsync/free-exercise-db/exercises"
