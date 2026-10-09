#!/bin/bash
# Runs on an EC2 host for scripts/demo-reseed-cloud.ps1, which uploads it with the files it needs
# into one private temp dir and removes that dir again afterwards. Not meant to be run by hand.
#
#   bash demo-cloud-remote.sh preflight <env> <app-secret-id> <now>
#   bash demo-cloud-remote.sh apply     <env> <app-secret-id> <now>
#
# Next to this script: targets.txt (one "persona email" per line) and, for apply,
# seed-demo-fitsync.sql. The database is reached as the RDS master user, whose password comes from
# Secrets Manager into a 0600 env file in the temp dir. Nothing here prints a secret, and the whole
# temp dir is removed on exit.
set -euo pipefail

MODE="${1:-}"; ENV_NAME="${2:-}"; SECRET_ID="${3:-}"; NOW="${4:-}"
case "$MODE" in preflight|apply) ;; *) echo "usage: $0 preflight|apply <env> <app-secret-id> <now>" >&2; exit 2 ;; esac
[[ "$ENV_NAME" =~ ^(staging|prod)$ ]] || { echo "bad env" >&2; exit 2; }
[[ "$SECRET_ID" =~ ^[A-Za-z0-9/_+=.@-]+$ ]] || { echo "bad secret id" >&2; exit 2; }
[[ "$NOW" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}$ ]] || { echo "bad now" >&2; exit 2; }

DIR="$(cd "$(dirname "$0")" && pwd)"
[[ "$DIR" == /tmp/fitsync-demo-* ]] || { echo "refusing to run outside /tmp/fitsync-demo-*" >&2; exit 2; }
trap 'rm -rf "$DIR"' EXIT
umask 077

REGION=ap-southeast-1
SERVICES=(fitsync-analytics-service fitsync-recommendation-service)
HEALTH_TIMEOUT=300

sj() { aws secretsmanager get-secret-value --region "$REGION" --secret-id "$1" --query SecretString --output text; }
jf() { python3 -c 'import json,sys; print(json.load(sys.stdin)[sys.argv[1]])' "$1"; }

APP="$(sj "$SECRET_ID")"
MASTER="$(sj "$(jf db_master_secret_arn <<<"$APP")")"
DB_HOST="$(jf db_host <<<"$APP")"
DB_PORT="$(jf db_port <<<"$APP")"
DB_NAME="$(jf db_name <<<"$APP")"
DB_USER="$(jf username <<<"$MASTER")"
printf 'PGPASSWORD=%s\n' "$(jf password <<<"$MASTER")" > "$DIR/pg.env"
unset MASTER APP

# psql in a throwaway postgres:16 container; the temp dir is mounted read-only at /w.
pg() {
    sudo docker run --rm -i --env-file "$DIR/pg.env" -v "$DIR:/w:ro" postgres:16 \
        psql -X -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -P pager=off -v ON_ERROR_STOP=1 "$@"
}

# The checks and counts, one query file used with -v email=... per persona.
cat > "$DIR/preflight.sql" <<'SQL'
select coalesce(u.found, false),
       coalesce(u.role, '-'),
       coalesce(u.pending, false),
       (select count(*) from nutrition.meal_logs m             where m.user_id = u.id),
       (select count(*) from nutrition.water_logs w            where w.user_id = u.id),
       (select count(*) from nutrition.recipe_cooks c          where c.user_id = u.id),
       (select count(*) from workout.workout_sessions s        where s.user_id = u.id),
       (select count(*) from workout.workout_plans p           where p.user_id = u.id),
       (select count(*) from user_domain.body_metric_entries b where b.user_id = u.id),
       (select count(*) from user_domain.user_goals g          where g.user_id = u.id)
  from (select 1) one
  left join (select a.id, true as found, a.role::text as role,
                    a.account_deletion_requested_at is not null as pending
               from auth.app_users a where a.email = :'email') u on true;
SQL
# The system catalogues the seed reads from: USDA foods, the system recipes it cooks, the exercise catalogue.
cat > "$DIR/catalogues.sql" <<'SQL'
select (select count(*) from nutrition.food_catalog_items
         where scope = 'GLOBAL' and source_dataset = 'USDA_SR'),
       (select count(distinct name) from nutrition.recipes
         where scope = 'SYSTEM' and published and deleted_at is null
           and name in ('Salmon Avocado Salad', 'Spanish Chicken', 'Sweet potato salad')),
       (select count(*) from workout.exercises where not custom);
SQL

TARGETS=()
while read -r persona email || [[ -n "${persona:-}" ]]; do
    [[ -z "${persona:-}" ]] && continue
    [[ "$persona" =~ ^(steady|drifting|fresh)$ ]] || { echo "bad persona in targets" >&2; exit 2; }
    [[ "$email" =~ ^[^[:space:]@\'\"]+@[^[:space:]@\'\"]+$ ]] || { echo "bad email in targets" >&2; exit 2; }
    TARGETS+=("$persona $email")
done < <(tr -d '\r' < "$DIR/targets.txt")
[[ ${#TARGETS[@]} -gt 0 ]] || { echo "no targets" >&2; exit 2; }

counts() { # persona email -> prints the current rows, returns 1 when the account must not be seeded
    local persona="$1" email="$2" row found role pending meals water cooks sessions plans body goals
    # errexit is off inside "if ! check_all", so a failed query is caught here explicitly.
    row="$(pg -At -F '|' -v email="$email" -f /w/preflight.sql)" || { echo "    ERROR: the account query failed."; return 1; }
    IFS='|' read -r found role pending meals water cooks sessions plans body goals <<<"$row"
    printf '  %-8s %s\n' "$persona" "$email"
    if [[ "$found" != "t" ]]; then
        echo "    BLOCKED: no account in auth.app_users. Sign in once on $ENV_NAME with this address first."
        return 1
    fi
    if [[ "$pending" == "t" ]]; then
        echo "    BLOCKED: the account has a pending deletion. Cancel it by signing in first."
        return 1
    fi
    if [[ "${role^^}" == "ADMIN" ]]; then
        echo "    BLOCKED: role is ADMIN. The admin account is never seeded."
        return 1
    fi
    echo "    role $role; rows now: meals $meals, water $water, recipe cooks $cooks, sessions $sessions, plans $plans, body metrics $body, goals $goals"
    return 0
}

check_all() {
    local blocked=0 t foods recipes exercises
    echo "Accounts on $ENV_NAME:"
    for t in "${TARGETS[@]}"; do
        counts ${t} || blocked=1
    done
    local cat
    cat="$(pg -At -F '|' -f /w/catalogues.sql)" || { echo "ERROR: the catalogue query failed."; return 1; }
    IFS='|' read -r foods recipes exercises <<<"$cat"
    echo "System catalogues: USDA foods $foods, demo recipes $recipes of 3, catalogue exercises $exercises"
    if [[ "$foods" -eq 0 ]]; then echo "  BLOCKED: the USDA food catalogue is empty (nutrition V900/V901 seeds)."; blocked=1; fi
    if [[ "$recipes" -ne 3 ]]; then echo "  BLOCKED: the system recipe library is missing demo recipes. Import it first."; blocked=1; fi
    if [[ "$exercises" -eq 0 ]]; then echo "  NOTE: no exercise catalogue; the seed falls back to its own demo exercises."; fi
    echo "Services:"
    local s state
    for s in "${SERVICES[@]}"; do
        state="$(sudo docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$s" 2>/dev/null || echo missing)"
        echo "  $s: $state"
        if [[ "$state" == missing ]]; then blocked=1; fi
    done
    return $blocked
}

if ! check_all; then
    echo "Preflight failed. Nothing was changed."
    exit 3
fi

if [[ "$MODE" == preflight ]]; then
    echo "Preflight passed. Seed run time (SGT) would be $NOW."
    exit 0
fi

[[ -f "$DIR/seed-demo-fitsync.sql" ]] || { echo "seed file missing" >&2; exit 2; }
for t in "${TARGETS[@]}"; do
    read -r persona email <<<"$t"
    echo "Seeding $persona ($email) at $NOW"
    pg -q -v email="$email" -v persona="$persona" -v now="$NOW" -f /w/seed-demo-fitsync.sql
done

echo "Restarting ${SERVICES[*]}"
sudo docker restart "${SERVICES[@]}" >/dev/null
deadline=$((SECONDS + HEALTH_TIMEOUT))
while :; do
    pending=()
    for s in "${SERVICES[@]}"; do
        state="$(sudo docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$s")"
        [[ "$state" == healthy ]] || pending+=("$s:$state")
    done
    [[ ${#pending[@]} -eq 0 ]] && break
    if (( SECONDS >= deadline )); then
        echo "Not healthy after ${HEALTH_TIMEOUT}s: ${pending[*]}" >&2
        exit 4
    fi
    sleep 5
done
echo "Both services healthy."
echo "Rows after the seed:"
for t in "${TARGETS[@]}"; do counts ${t}; done
