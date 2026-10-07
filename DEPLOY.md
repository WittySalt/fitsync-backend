# FitSync Backend — AWS Deployment Runbook

Single-host (EC2 + docker-compose) deployment that lights up the **gated**
`docker-image` and `deploy-prod` jobs in [`.github/workflows/cd.yml`](.github/workflows/cd.yml)
(full variable/secret reference: [`.github/CICD-VARIABLES.md`](.github/CICD-VARIABLES.md)).
Region: **ap-southeast-1 (Singapore)**. Everything below is one-time setup; after
it, every push to `main` builds 7 images and deploys them to staging; prod is deployed
from a manual run with `deploy_prod` ticked (see the promotion rule in `cd.yml`).

> The pipeline stays gated (green, jobs skipped) until the **`AWS_ROLE_ARN`**
> repository variable is set. Set it last, on purpose.

> **Since 2026-10:** both staging and prod are built by Terraform, and the CD role comes
> from `infra/terraform/bootstrap/`. Follow [`infra/terraform/RUNBOOK.md`](infra/terraform/RUNBOOK.md)
> "Rebuild" for the order. The hand-built steps below (§1-§6) remain as reference for what
> each resource is; §7 onward (variables, media, Bedrock, TLS) still applies as written.

## What runs where

- **7 images** in ECR: `fitsync-{api-gateway,auth-service,user-service,workout-service,nutrition-service,analytics-service,recommendation-service}`
- **EC2** (t3.medium, Amazon Linux 2023): runs all 7 + `redis` and `kafka` containers, via `/opt/fitsync/docker-compose.prod.yml`
- **RDS PostgreSQL**: one DB `fitsync`, 6 schemas (`auth`, `user_domain`, `workout`, `nutrition`, `analytics`, `recommendation`) auto-created by Flyway
- CI authenticates to AWS via **GitHub OIDC → IAM role** (no static keys). The EC2 host pulls from ECR via its **instance role**.

Set these shell vars first to paste the commands as-is:
```bash
export AWS_REGION=ap-southeast-1
export ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
```

---

## 1. ECR (image registry)

The `docker-image` job auto-creates each repo on first push, so this is optional —
but you can pre-create them:
```bash
for s in api-gateway auth-service user-service workout-service nutrition-service analytics-service recommendation-service; do
  aws ecr create-repository --repository-name "fitsync-$s" \
    --image-scanning-configuration scanOnPush=true --region "$AWS_REGION" || true
done
```

## 2. IAM — GitHub OIDC role (lets CI assume AWS)

**2a. Register GitHub Actions as an OIDC provider** (once per account):
```bash
aws iam create-open-id-connect-provider \
  --url https://token.actions.githubusercontent.com \
  --client-id-list sts.amazonaws.com
```

**2b. Trust policy** — only workflows of *your* repository may assume it.
Save as `trust.json` (replace `<ACCOUNT_ID>`):
```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": { "Federated": "arn:aws:iam::<ACCOUNT_ID>:oidc-provider/token.actions.githubusercontent.com" },
    "Action": "sts:AssumeRoleWithWebIdentity",
    "Condition": {
      "StringEquals": { "token.actions.githubusercontent.com:aud": "sts.amazonaws.com" },
      "StringLike": { "token.actions.githubusercontent.com:sub": "repo:WittySalt/fitsync-backend:*" }
    }
  }]
}
```
```bash
aws iam create-role --role-name fitsync-github-ci --assume-role-policy-document file://trust.json
```

**2c. Permissions** — push to ECR + create repos. Save as `ci-policy.json`:
```json
{
  "Version": "2012-10-17",
  "Statement": [
    { "Effect": "Allow", "Action": "ecr:GetAuthorizationToken", "Resource": "*" },
    { "Effect": "Allow",
      "Action": ["ecr:CreateRepository","ecr:DescribeRepositories","ecr:DescribeImages",
                 "ecr:BatchCheckLayerAvailability","ecr:InitiateLayerUpload","ecr:UploadLayerPart",
                 "ecr:CompleteLayerUpload","ecr:PutImage","ecr:BatchGetImage","ecr:GetDownloadUrlForLayer"],
      "Resource": "arn:aws:ecr:ap-southeast-1:<ACCOUNT_ID>:repository/fitsync-*" }
  ]
}
```
```bash
aws iam put-role-policy --role-name fitsync-github-ci \
  --policy-name ecr-push --policy-document file://ci-policy.json
# -> note the role ARN; it becomes the AWS_ROLE_ARN repository variable
aws iam get-role --role-name fitsync-github-ci --query Role.Arn --output text
```

## 3. IAM — EC2 instance role (lets the host pull from ECR)

```bash
cat > ec2-trust.json <<'JSON'
{ "Version":"2012-10-17","Statement":[{"Effect":"Allow",
  "Principal":{"Service":"ec2.amazonaws.com"},"Action":"sts:AssumeRole"}]}
JSON
aws iam create-role --role-name fitsync-ec2 --assume-role-policy-document file://ec2-trust.json

cat > ec2-policy.json <<JSON
{ "Version":"2012-10-17","Statement":[
  {"Effect":"Allow","Action":"ecr:GetAuthorizationToken","Resource":"*"},
  {"Effect":"Allow",
   "Action":["ecr:BatchGetImage","ecr:GetDownloadUrlForLayer","ecr:BatchCheckLayerAvailability"],
   "Resource":"arn:aws:ecr:${AWS_REGION}:${ACCOUNT_ID}:repository/fitsync-*"}
] }
JSON
aws iam put-role-policy --role-name fitsync-ec2 --policy-name ecr-pull --policy-document file://ec2-policy.json

# instance profile (what actually attaches to the EC2)
aws iam create-instance-profile --instance-profile-name fitsync-ec2
aws iam add-role-to-instance-profile --instance-profile-name fitsync-ec2 --role-name fitsync-ec2
```
> **Future AI:** when `recommendation`/`analytics` call Bedrock, add one statement
> here: `{"Effect":"Allow","Action":"bedrock:InvokeModel","Resource":"*"}`. No EC2
> resize needed — LLM calls are outbound HTTPS.

## 4. Networking (security groups)

```bash
export VPC_ID=$(aws ec2 describe-vpcs --filters Name=isDefault,Values=true \
  --query 'Vpcs[0].VpcId' --output text)

# EC2 SG: SSH (CI deploy) + HTTP/HTTPS (nginx)
export EC2_SG=$(aws ec2 create-security-group --group-name fitsync-ec2 \
  --description "FitSync EC2" --vpc-id "$VPC_ID" --query GroupId --output text)
aws ec2 authorize-security-group-ingress --group-id "$EC2_SG" --protocol tcp --port 22 --cidr 0.0.0.0/0
aws ec2 authorize-security-group-ingress --group-id "$EC2_SG" --protocol tcp --port 80 --cidr 0.0.0.0/0
aws ec2 authorize-security-group-ingress --group-id "$EC2_SG" --protocol tcp --port 443 --cidr 0.0.0.0/0

# RDS SG: Postgres only from the EC2 SG
export RDS_SG=$(aws ec2 create-security-group --group-name fitsync-rds \
  --description "FitSync RDS" --vpc-id "$VPC_ID" --query GroupId --output text)
aws ec2 authorize-security-group-ingress --group-id "$RDS_SG" --protocol tcp --port 5432 --source-group "$EC2_SG"
```
> SSH 22 is `0.0.0.0/0` because GitHub-hosted runners have dynamic IPs. It's
> key-only (no password). Tighten to GitHub's published Actions IP ranges
> (`https://api.github.com/meta`) if you want.

## 5. RDS PostgreSQL

```bash
aws rds create-db-instance \
  --db-instance-identifier fitsync-prod \
  --engine postgres --engine-version 16 \
  --db-instance-class db.t3.micro \
  --allocated-storage 20 --storage-type gp3 \
  --db-name fitsync \
  --master-username fitsync \
  --master-user-password '<STRONG_DB_PASSWORD>' \
  --vpc-security-group-ids "$RDS_SG" \
  --no-publicly-accessible --backup-retention-period 7 \
  --region "$AWS_REGION"

# wait, then grab the endpoint:
aws rds wait db-instance-available --db-instance-identifier fitsync-prod
aws rds describe-db-instances --db-instance-identifier fitsync-prod \
  --query 'DBInstances[0].Endpoint.Address' --output text
```
- `DB_URL` = `jdbc:postgresql://<that-endpoint>:5432/fitsync`
- **Per-service least-privilege users (not the master).** Each service connects as
  its own DB user (`auth_svc`/`user_svc`/`workout_svc`/`nutrition_svc`/`analytics_svc`/`recommendation_svc`),
  scoped to its schema, so a compromise of one service can't reach another's tables.
  One-time setup, run as the master once after the instance is up:
  ```sql
  CREATE SCHEMA IF NOT EXISTS auth;        -- + user_domain, workout, nutrition, analytics, recommendation
  CREATE USER auth_svc WITH PASSWORD '<AUTH_DB_PASSWORD>';   -- one per service
  GRANT ALL ON SCHEMA auth TO auth_svc;    ALTER ROLE auth_svc SET search_path = auth;
  ```
  Flyway then runs as each service user and creates that schema's tables on first boot.
  Note schema names: user-service uses **`user_domain`**, the rest match the service name.

## 6. EC2 instance

```bash
# ed25519 key pair — keep the private key for the PROD_EC2_SSH_KEY secret
aws ec2 create-key-pair --key-name fitsync --key-type ed25519 \
  --query KeyMaterial --output text > fitsync_ed25519
chmod 600 fitsync_ed25519

# latest Amazon Linux 2023 AMI
export AMI=$(aws ssm get-parameter \
  --name /aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64 \
  --query Parameter.Value --output text)

aws ec2 run-instances \
  --image-id "$AMI" --instance-type t3.medium \
  --key-name fitsync \
  --iam-instance-profile Name=fitsync-ec2 \
  --security-group-ids "$EC2_SG" \
  --block-device-mappings '[{"DeviceName":"/dev/xvda","Ebs":{"VolumeSize":30,"VolumeType":"gp3"}}]' \
  --tag-specifications 'ResourceType=instance,Tags=[{Key=Name,Value=fitsync-prod}]'
```

**Elastic IP (critical for stop/start)** — without it the public IP changes every
time you stop the instance to save money, breaking `PROD_EC2_HOST` and TLS:
```bash
export EIP_ALLOC=$(aws ec2 allocate-address --query AllocationId --output text)
export INSTANCE_ID=$(aws ec2 describe-instances \
  --filters Name=tag:Name,Values=fitsync-prod Name=instance-state-name,Values=running \
  --query 'Reservations[0].Instances[0].InstanceId' --output text)
aws ec2 associate-address --instance-id "$INSTANCE_ID" --allocation-id "$EIP_ALLOC"
aws ec2 describe-addresses --allocation-ids "$EIP_ALLOC" --query 'Addresses[0].PublicIp' --output text
```
- `PROD_EC2_HOST` = that Elastic IP (or a DNS A record pointing at it).

## 7. GitHub Actions variables & secrets (the bridge — Settings → Secrets and variables → Actions)

Set on the **fitsync-backend** repository. Rows marked **Secret = yes** go under
*Secrets*; the rest under *Variables*. See [`.github/CICD-VARIABLES.md`](.github/CICD-VARIABLES.md)
for which job reads each one and `gh` one-liners.

| Name | Value | Secret | Notes |
|---|---|---|---|
| `AWS_ROLE_ARN` | arn of `fitsync-github-ci` (step 2c) | no | **set LAST — this un-gates the pipeline** |
| `AWS_REGION` | `ap-southeast-1` | no | |
| `AWS_ACCOUNT_ID` | your 12-digit account id | no | |
| `PROD_EC2_HOST` | the Elastic IP (`ec2_public_ip` output) | no | SSH target; traffic enters through the ALB |
| `PROD_EC2_SSH_KEY` | contents of `fitsync_ed25519` | yes | the **private** key, full file |
| `PROD_DOMAIN` | your domain (optional, for nginx/TLS) | no | leave unset until you add DNS |

No database endpoint, database password or JWT key is a GitHub secret. Terraform
generates them into Secrets Manager (`fitsync-<env>/app`, `secrets.tf`), RDS manages
its own master password, and `scripts/ec2-deploy.sh` reads both with the EC2 instance
role at deploy time.

Media / images for **prod** (see §8.5). Staging needs none of these: CD reads its own
bucket and URL from Terraform. Both environments upload with the EC2 instance role, so
there are no media keys:

| Name | Value | Secret | Notes |
|---|---|---|---|
| `FITSYNC_MEDIA_ENDPOINT` | `https://s3.ap-southeast-1.amazonaws.com` | no | S3 API the SDK writes through (path-style) |
| `FITSYNC_MEDIA_REGION` | `ap-southeast-1` | no | defaults to this if unset |
| `FITSYNC_MEDIA_BUCKET` | `fitsync-media` | no | defaults to this if unset |
| `FITSYNC_MEDIA_PUBLIC_BASE_URL` | `https://fitsync-media.s3.ap-southeast-1.amazonaws.com` | no | defaults to this; browser-reachable **https** base **baked into stored URLs**, so it must be right before workout-service first starts |

Coach AI tier (optional; unset leaves it off and the coach uses its rule text):

| Name | Value | Secret | Notes |
|---|---|---|---|
| `RECOMMENDATION_AI_ENABLED` | `true` | no | defaults to `false` |
| `RECOMMENDATION_AI_PROVIDER` | `anthropic` | no | compose default when unset |
| `RECOMMENDATION_AI_MODEL` | e.g. `claude-haiku-4-5-20251001` | no | compose default when unset |
| `RECOMMENDATION_AI_BASE_URL` | e.g. `http://ollama:11434` | no | needed only for the `ollama` provider; the compose default `localhost` points at the container itself |
| `ANTHROPIC_API_KEY` | Anthropic API key | yes | needed only for the `anthropic` provider |

> The workout-service image dir is fixed to `/opt/fitsync/free-exercise-db/exercises`
> by `cd.yml` (see §8.5 d).
>
> Leave all of these unset and the services still boot, but they fall back to the
> local-dev MinIO defaults (`localhost:9000`) — there is no MinIO in this prod
> stack, so **every image silently stays broken.** That is the default state.

Optional: `COGNITO_ENABLED` (`true`/`false`, default `false`). `JWT_EXPIRATION` (`PT2H`)
and `SPRING_CACHE_TYPE` (`simple`) are fixed in the rendered `.env` — edit `cd.yml` to change them.

## 8. First deploy

1. Set all variables/secrets above **except** `AWS_ROLE_ARN`. Confirm the `main` CD run is green (AWS jobs skipped).
2. Set `AWS_ROLE_ARN`. Push to `main` (or Actions → CD → **Run workflow**) → `docker-image` builds + pushes all 6 images to ECR.
3. Run the workflow by hand with `deploy_prod` ticked (it needs `PROD_EC2_HOST` set);
   `deploy-prod` runs once staging succeeded in that run.
   - First run auto-installs Docker + compose + aws-cli on the host, renders `/opt/fitsync/.env` from the variables, pulls from ECR, `compose up --wait`, health-gates the gateway, writes the rollback tag.
4. Check through the ALB or CloudFront: `curl http://<alb_dns_name>/actuator/health` (staging) or
   `https://<prod-cloudfront>/actuator/health` → `{"status":"UP"}`. Port 8088 is open to the ALB only.

## 8.5 Media / images (S3) — exercise images + avatars

**Why this is separate.** There is **no MinIO in the prod stack** — MinIO is only the
local-dev S3 emulator in `docker-compose.yml`. In prod the same `media-support` S3
client (used by both **workout-service** for the exercise catalog and **user-service**
for avatar upload) must point at a **real S3 bucket**. If you skip this, the services
boot fine but fall back to `localhost:9000` and every image is broken.

### How images get into S3 (the flow)

```
free-exercise-db images on EC2 disk          workout-service                S3 bucket               browser
/opt/fitsync/free-exercise-db/exercises  ──►  catalog auto-import   ──put──► fitsync-media   ──GET──► <img src>
        (fetch script, one-time)              reads each file,               exercises/<ref>/<n>.jpg  (https,
                                              uploads bytes, writes          avatars/<userId>/...     public-read)
                                              PUBLIC_BASE_URL+key to DB
```

Two independent URLs (don't confuse them): `FITSYNC_MEDIA_ENDPOINT` is where bytes are
**written**; `FITSYNC_MEDIA_PUBLIC_BASE_URL` is **baked into the DB row** for the browser
to **read**. The browser fetches objects directly from S3 — the backend never proxies —
so the bucket must serve those objects over **public-read https**. The import only writes
a DB image row when it can read the local source file, so the images **must be on the EC2
host at import time** — pre-seeding the bucket alone is not enough.

### Setup

**a. Bucket (public-read over https):**
```bash
aws s3api create-bucket --bucket fitsync-media --region "$AWS_REGION" \
  --create-bucket-configuration LocationConstraint="$AWS_REGION"
# allow public GET (objects only), keep writes private
aws s3api put-public-access-block --bucket fitsync-media \
  --public-access-block-configuration BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=false,RestrictPublicBuckets=false
cat > media-bucket-policy.json <<JSON
{ "Version":"2012-10-17","Statement":[{
  "Sid":"PublicReadObjects","Effect":"Allow","Principal":"*",
  "Action":"s3:GetObject","Resource":"arn:aws:s3:::fitsync-media/*" }]}
JSON
aws s3api put-bucket-policy --bucket fitsync-media --policy file://media-bucket-policy.json
```
> Prefer no public bucket? Put CloudFront in front and set `FITSYNC_MEDIA_PUBLIC_BASE_URL`
> to the distribution domain instead. Public-read https is the minimal working option.

**b. Write access comes from the EC2 instance role.** Terraform grants it `s3:PutObject`
on the environment's media bucket (`storage.tf`), CD sets
`FITSYNC_MEDIA_AWS_DEFAULT_CREDENTIALS=true`, and media-support then ignores the
access/secret keys. The keys remain only for local MinIO. The old `fitsync-media-writer`
IAM user and its static key can be deleted once prod runs on the role.

**c. Set the media variables** from the table in §7 (prod only).

**d. Put the source images on the EC2 host** (one-time, ~90MB; not committed to the repo):
```bash
ssh -i fitsync_ed25519 ec2-user@<EIP>
sudo dnf install -y git
sudo mkdir -p /opt/fitsync && sudo chown -R ec2-user:ec2-user /opt/fitsync
# shallow sparse checkout of just the exercises/ dir from free-exercise-db
git clone --depth 1 --filter=blob:none --sparse https://github.com/yuhonas/free-exercise-db.git /tmp/fedb
cd /tmp/fedb && git sparse-checkout set exercises
mkdir -p /opt/fitsync/free-exercise-db
cp -r /tmp/fedb/exercises /opt/fitsync/free-exercise-db/exercises
```
(Same effect as `scripts/fetch-free-exercise-db-images.sh`, run on the host.)

**e. Deploy / re-import.** Redeploy via the pipeline (§8) so the new media env reaches
the containers. The catalog auto-import only runs **when the catalog is empty**, so on a
host that already imported metadata (no images) you must **force a re-import** once — it
is idempotent and backfills the image rows now that the source dir is populated:
```bash
ssh -i fitsync_ed25519 ec2-user@<EIP>
docker exec fitsync-workout-service \
  wget -qO- --post-data='' http://localhost:8082/api/v1/internal/workouts/exercises/import-catalog
```
Verify one object resolves publicly:
```bash
curl -sI https://fitsync-media.s3.ap-southeast-1.amazonaws.com/exercises/Barbell_Bench_Press_-_Medium_Grip/0.jpg | head -1
# -> HTTP/1.1 200 OK
```
Avatars need no seeding — they upload on demand once `user-service` has the media env.

## 8.6 Coach AI on Amazon Bedrock (optional)

The coach's AI tier can call Amazon Bedrock with the EC2 instance role, so no model API key
is stored anywhere. Without it the coach still works and uses its rule text.

1. **Model access (one time, console).** Bedrock → Model catalog → open the Anthropic model and
   submit the use case form. Amazon Nova models need no form. In `ap-southeast-1`, Claude Haiku 4.5
   is reached through the `global.` inference profile and Nova through `apac.`.
2. **Permissions.** Terraform gives the instance role `bedrock:InvokeModel` on the models in
   `var.bedrock_models` (`ci_iam.tf`) and sets the IMDS hop limit to 2 (`compute.tf`) so the
   containers can reach the role. For a host built by hand, do both yourself:
   ```bash
   aws ec2 modify-instance-metadata-options --instance-id <id>      --http-tokens required --http-put-response-hop-limit 2
   ```
3. **Variables** (GitHub Actions, see §7): `RECOMMENDATION_AI_ENABLED=true`,
   `RECOMMENDATION_AI_PROVIDER=bedrock`, and `RECOMMENDATION_AI_MODEL` set to one of:

   | Model ID | Price per 1M tokens (in / out) |
   |---|---|
   | `global.anthropic.claude-haiku-4-5-20251001-v1:0` | $1 / $5 |
   | `apac.amazon.nova-lite-v1:0` | $0.06 / $0.24 |
   | `apac.amazon.nova-micro-v1:0` | $0.035 / $0.14 |

Check from the host after a deploy:
```bash
sudo docker logs fitsync-recommendation-service 2>&1 | grep -i bedrock
```
An `AccessDeniedException` means the role or model list is wrong; `ResourceNotFoundException`
asking for use case details means step 1 is not done (it can take 15 minutes to apply).

## 9. TLS / nginx (optional follow-up)

`deploy-prod` brings up the 7 services + redis + kafka but **not nginx** (a fresh host has
no certs). To add HTTPS:
```bash
ssh -i fitsync_ed25519 ec2-user@<EIP>
sudo dnf install -y certbot
sudo certbot certonly --standalone -d <your-domain>   # needs PROD_DOMAIN DNS A -> EIP
cd /opt/fitsync && sudo docker compose -f docker-compose.prod.yml up -d nginx
```
Then set `PROD_DOMAIN` so the rendered nginx config uses your domain and its certificate.

## 10. Cost control (demo)

t3.medium is on-demand (~hourly). Stop when idle, start for demos:
```bash
aws ec2 stop-instances  --instance-ids "$INSTANCE_ID"
aws ec2 start-instances --instance-ids "$INSTANCE_ID"
```
The Elastic IP keeps the address stable across stop/start. RDS also supports
stop (up to 7 days at a time). Destroy everything after the capstone to stop billing.

## 11. Event bus (Kafka) — enabling cross-service projections

Cross-service projections (analytics streak/PR rollups, and the **nutrition daily
budget targets** that come from a user's calorie/protein goals) flow over Kafka via
a transactional-outbox → listener pipeline. It ships **off by default** so the base
deploy is unchanged: the `kafka` service sits behind the `events` compose profile and
every `KAFKA_*` flag defaults to `false`. With it off, goal targets never reach
nutrition, so the budget shows no targets (the frontend renders that as an honest
"set a target" empty state).

To turn it on for a single-host (EC2) deploy:

1. In the deploy `.env`, flip the switches (all 4 groups are required — outbox relay
   on the producer side **and** the consumer listeners):
   ```bash
   KAFKA_ENABLED=true
   OUTBOX_RELAY_ENABLED=true
   KAFKA_BOOTSTRAP_SERVERS=kafka:29092            # in-stack broker's INTERNAL listener
   # consumer listeners (user/nutrition/analytics):
   KAFKA_USER_REGISTERED_LISTENER_ENABLED=true
   KAFKA_GOAL_UPDATED_LISTENER_ENABLED=true
   KAFKA_USER_PREFERENCES_UPDATED_LISTENER_ENABLED=true
   KAFKA_BODY_METRICS_RECORDED_LISTENER_ENABLED=true
   KAFKA_MEAL_LOGGED_LISTENER_ENABLED=true
   KAFKA_MEAL_UPDATED_LISTENER_ENABLED=true
   KAFKA_MEAL_DELETED_LISTENER_ENABLED=true
   KAFKA_WORKOUT_LOGGED_LISTENER_ENABLED=true
   KAFKA_WORKOUT_UPDATED_LISTENER_ENABLED=true
   KAFKA_WORKOUT_DELETED_LISTENER_ENABLED=true
   ```
2. Bring the stack up **including the `events` profile** (so the broker actually starts):
   ```bash
   docker compose -f docker-compose.prod.yml --env-file .env --profile events up -d
   ```
   Enablement is **env-var only** — there is deliberately no `kafka` Spring profile
   (same lesson as Cognito: a per-service `application-kafka.yml` could be absent from
   the CI jar, making the profile a silent no-op; the env flags above always apply).
3. Verify: complete an onboarding for a fresh account, then
   `GET /api/v1/nutrition/summary/daily` should return non-null `targetCalories` /
   `targetProtein` (the budget now has targets). `docker exec fitsync-kafka
   /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list` shows the
   auto-created topics; check each consumer service's logs for assigned partitions.

**Memory:** the broker adds a JVM to the host, so the compose now caps every container
— each Spring service `mem_limit 768m` + `-XX:MaxRAMPercentage=70`, Kafka `768m` +
`-Xmx512m`, redis `256m`, nginx `128m` (≈5.8 GB total). This fits a **t3.large (8 GB)**
with headroom; a t3.medium (4 GB) is **not** enough with events on. Failed records
retry 3× (2 s back-off) then log-and-skip; poison/deserialization payloads are
classified non-retryable and skipped immediately (no retry storm).
