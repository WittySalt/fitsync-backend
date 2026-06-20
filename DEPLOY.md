# FitSync Backend — AWS Deployment Runbook

Single-host (EC2 + docker-compose) deployment that lights up the **gated**
`docker_image` and `deploy_prod` jobs in [`.gitlab-ci.yml`](.gitlab-ci.yml).
Region: **ap-southeast-1 (Singapore)**. Everything below is one-time setup; after
it, every push to `main` builds 6 images and a manual click deploys them.

> The pipeline stays gated (green, jobs skipped) until the **`AWS_ROLE_ARN`**
> CI/CD variable is set. Set it last, on purpose.

## What runs where

- **6 images** in ECR: `fitsync-{api-gateway,auth-service,user-service,workout-service,nutrition-service,analytics-service}`
- **EC2** (t3.medium, Amazon Linux 2023): runs all 6 + a `redis` container, via `/opt/fitsync/docker-compose.prod.yml`
- **RDS PostgreSQL**: one DB `fitsync`, 5 schemas (`auth`, `user_domain`, `workout`, `nutrition`, `analytics`) auto-created by Flyway
- CI authenticates to AWS via **GitLab OIDC → IAM role** (no static keys). The EC2 host pulls from ECR via its **instance role**.

Set these shell vars first to paste the commands as-is:
```bash
export AWS_REGION=ap-southeast-1
export ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
export PROJECT=fitsyncs/fitsync-backend      # your GitLab path
```

---

## 1. ECR (image registry)

The `docker_image` job auto-creates each repo on first push, so this is optional —
but you can pre-create them:
```bash
for s in api-gateway auth-service user-service workout-service nutrition-service analytics-service; do
  aws ecr create-repository --repository-name "fitsync-$s" \
    --image-scanning-configuration scanOnPush=true --region "$AWS_REGION" || true
done
```

## 2. IAM — GitLab OIDC role (lets CI assume AWS)

**2a. Register GitLab as an OIDC provider** (once per account):
```bash
aws iam create-open-id-connect-provider \
  --url https://gitlab.com \
  --client-id-list sts.amazonaws.com
```

**2b. Trust policy** — only `main` pipelines of *your* project may assume it.
Save as `trust.json` (replace `<ACCOUNT_ID>`):
```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": { "Federated": "arn:aws:iam::<ACCOUNT_ID>:oidc-provider/gitlab.com" },
    "Action": "sts:AssumeRoleWithWebIdentity",
    "Condition": {
      "StringEquals": { "gitlab.com:aud": "sts.amazonaws.com" },
      "StringLike": { "gitlab.com:sub": "project_path:fitsyncs/fitsync-backend:ref_type:branch:ref:main" }
    }
  }]
}
```
```bash
aws iam create-role --role-name fitsync-gitlab-ci --assume-role-policy-document file://trust.json
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
aws iam put-role-policy --role-name fitsync-gitlab-ci \
  --policy-name ecr-push --policy-document file://ci-policy.json
# -> note the role ARN; it becomes the AWS_ROLE_ARN CI/CD variable
aws iam get-role --role-name fitsync-gitlab-ci --query Role.Arn --output text
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
> SSH 22 is `0.0.0.0/0` because GitLab SaaS runners have dynamic IPs. It's
> key-only (no password). Tighten to GitLab's published IP ranges if you want.

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
  its own DB user (`auth_svc`/`user_svc`/`workout_svc`/`nutrition_svc`/`analytics_svc`),
  scoped to its schema, so a compromise of one service can't reach another's tables.
  One-time setup, run as the master once after the instance is up:
  ```sql
  CREATE SCHEMA IF NOT EXISTS auth;        -- + user_domain, workout, nutrition, analytics
  CREATE USER auth_svc WITH PASSWORD '<AUTH_DB_PASSWORD>';   -- one per service
  GRANT ALL ON SCHEMA auth TO auth_svc;    ALTER ROLE auth_svc SET search_path = auth;
  ```
  Flyway then runs as each service user and creates that schema's tables on first boot.
  Note schema names: user-service uses **`user_domain`**, the rest match the service name.

## 6. EC2 instance

```bash
# ed25519 key pair — keep the private key for the GitLab variable
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

## 7. GitLab CI/CD variables (the bridge — Settings → CI/CD → Variables)

Set on the **fitsync-backend** project. Mark secrets **Masked**, and everything
**Protected** (only protected branches/main can read them).

| Variable | Value | Masked | Notes |
|---|---|---|---|
| `AWS_ROLE_ARN` | arn of `fitsync-gitlab-ci` (step 2c) | no | **set LAST — this un-gates the pipeline** |
| `AWS_REGION` | `ap-southeast-1` | no | |
| `AWS_ACCOUNT_ID` | your 12-digit account id | yes | |
| `DB_URL` | `jdbc:postgresql://<rds>:5432/fitsync` | no | shared (one db) |
| `AUTH_DB_PASSWORD` | password for DB user `auth_svc` | yes | per-service least-privilege user |
| `USER_DB_PASSWORD` | password for DB user `user_svc` | yes | |
| `WORKOUT_DB_PASSWORD` | password for DB user `workout_svc` | yes | |
| `NUTRITION_DB_PASSWORD` | password for DB user `nutrition_svc` | yes | |
| `ANALYTICS_DB_PASSWORD` | password for DB user `analytics_svc` | yes | |
| `JWT_SECRET` | long random string | yes | `openssl rand -base64 48` |
| `PROD_EC2_HOST` | the Elastic IP | no | |
| `PROD_EC2_SSH_KEY` | contents of `fitsync_ed25519` | yes | the **private** key, full file |
| `PROD_DOMAIN` | your domain (optional, for TLS/smoke) | no | leave unset until you add DNS |

Optional: `JWT_EXPIRATION` (default `PT2H`), `SPRING_CACHE_TYPE`, `COGNITO_ENABLED`,
`PROD_IMAGE_TAG` (deploy a specific tag instead of latest commit), `RUN_PROD_SMOKE`.

## 8. First deploy

1. Set all variables above **except** `AWS_ROLE_ARN`. Confirm `main` pipeline is green (AWS jobs skipped).
2. Set `AWS_ROLE_ARN`. Push to `main` (or re-run) → `docker_image` builds + pushes all 6 images to ECR.
3. Pipeline → `deploy_prod` job → **Run** (manual, asks for confirmation).
   - First run auto-installs Docker + compose + aws-cli on the host, renders `/opt/fitsync/.env` from the variables, pulls from ECR, `compose up --wait`, health-gates the gateway, writes the rollback tag.
4. Check: `curl http://<EIP>:8088/actuator/health` → `{"status":"UP"}`.

## 9. TLS / nginx (optional follow-up)

`deploy_prod` brings up the 6 services + redis but **not nginx** (a fresh host has
no certs). To add HTTPS:
```bash
ssh -i fitsync_ed25519 ec2-user@<EIP>
sudo dnf install -y certbot
sudo certbot certonly --standalone -d <your-domain>   # needs PROD_DOMAIN DNS A -> EIP
cd /opt/fitsync && sudo docker compose -f docker-compose.prod.yml up -d nginx
```
Then set `PROD_DOMAIN` + `RUN_PROD_SMOKE=true` to enable the external smoke test.

## 10. Cost control (demo)

t3.medium is on-demand (~hourly). Stop when idle, start for demos:
```bash
aws ec2 stop-instances  --instance-ids "$INSTANCE_ID"
aws ec2 start-instances --instance-ids "$INSTANCE_ID"
```
The Elastic IP keeps the address stable across stop/start. RDS also supports
stop (up to 7 days at a time). Destroy everything after the capstone to stop billing.
