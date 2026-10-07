# FitSync — Infrastructure as Code (Terraform)

Declarative provisioning of the FitSync AWS environment. This is the **cloud-resource
layer** of our IaC story; the **runtime layer** (container orchestration) lives in
`backend/docker-compose.prod.yml`, `backend/scripts/ec2-deploy.sh`, and `.github/workflows/cd.yml`.

> **Location:** this lives at `backend/infra/terraform/` (inside the backend repo, so
> the CD workflow can run it). **State:** remote in S3
> (`s3://fitsync-tfstate-437659978621`, native `use_lockfile` locking) — local and CI
> share one state. `terraform init` requires AWS credentials.

```
Terraform  ──►  AWS resources (VPC, ECR, S3, CloudFront, Cognito, IAM, RDS, EC2)
                                   │
docker-compose.prod.yml + ec2-deploy.sh  ──►  containers on the EC2 host
```

## What gets created

| File | Resources | Cost / speed |
|------|-----------|--------------|
| `network.tf` | VPC, public subnets, IGW, routes, security groups | free, instant |
| `registry.tf` | 7× ECR repos (one per service) + lifecycle policy | free (empty), instant |
| `storage.tf` | S3 media bucket, S3 frontend bucket, CloudFront* | ~free; CloudFront slow |
| `auth.tf` | Cognito user pool + SPA client + hosted domain | free tier, instant |
| `ci_iam.tf` | EC2 instance role/profile, CI deploy policy | free, instant |
| `data.tf` | RDS PostgreSQL* | **costs money**, ~10 min |
| `compute.tf` | EC2 app host + EIP* + deploy key pair | **costs money** |
| `bootstrap/` | GitHub OIDC provider, `fitsync-github-cd` role, workload boundary (own state, applied once) | free, instant |

`*` gated behind feature flags (`enable_cloudfront`, `enable_rds`, `enable_ec2`),
default **false**, so the first apply is free and instant.

## Two-environment model

Both environments are built by Terraform, each in its own workspace. The hand-built
prod of June 2026 was torn down on 2026-06-27; prod is restored from its RDS snapshot.

| Environment | tfvars | State (workspace) | Notes |
|-------------|--------|-------------------|-------|
| **staging** | `staging.tfvars` → `fitsync-staging-*` | `staging` | own Cognito pool, gateway open on :8088 for stress/DAST, throwaway |
| **prod** | `prod.tfvars` → `fitsync-prod-*` | `prod` | RDS from snapshot, deletion protection + final snapshot, gateway reachable from CloudFront only |

Kept outside Terraform on purpose: the prod Cognito pool `ap-southeast-1_myVyuOmPD`
(holds real users), the media bucket `fitsync-media`, and the frontend bucket and
CloudFront distribution of prod.

## Prerequisites

- Terraform ≥ 1.5 (`terraform version`)
- AWS credentials configured (`aws sts get-caller-identity` works)
- Region: `ap-southeast-1`

## Run staging by hand

> **PowerShell note:** PowerShell mangles the bare `-var-file=staging.tfvars`
> token (terraform ends up seeing `.tfvars` as a plan file →
> *"Failed to load .tfvars as a plan file"*). **Quote the value**:
> `terraform apply -var-file="staging.tfvars"` (or use a space:
> `-var-file staging.tfvars`). Bash/zsh are unaffected.

### Phase 1 — free + instant resources

```bash
cd infra/terraform
terraform init
terraform workspace new staging      # first time; later: terraform workspace select staging
terraform validate
terraform plan  -var-file=staging.tfvars   # review: VPC, ECR×6, S3×2, Cognito, IAM
terraform apply -var-file=staging.tfvars   # type "yes"
terraform output                            # capture ECR URLs, Cognito IDs, bucket names
```

This brings up the whole network/registry/storage/auth/IAM footprint at **$0**.
Screenshot the `apply` summary and the AWS console — that is your IaC artifact.

### Phase 2 — the paid resources (only if you want to demo the full env)

Enable one flag at a time. RDS needs a master password (never commit it):

```bash
export TF_VAR_db_password='<a-strong-password>'
terraform apply -var-file=staging.tfvars -var="enable_rds=true"
terraform apply -var-file=staging.tfvars -var="enable_ec2=true"
terraform apply -var-file=staging.tfvars -var="enable_cloudfront=true"   # ~15-20 min
```

### Phase 3 — tear down to stop the bill

```bash
terraform destroy -var-file=staging.tfvars
```

The `apply → demo → destroy` cycle is itself the strongest IaC evidence:
the environment is fully reproducible and disposable from code.

## CI/CD pipeline (GitHub Actions)

Terraform runs **only on `main` (CD, `.github/workflows/cd.yml`)** — never on pull
requests — the same rule the container build (`docker-image`) already follows. PR
runs (`ci.yml`) stay app-focused (tests + SAST). `terraform plan` validates
internally, so there is no separate validate job.

- `tf-plan` — assumes the AWS role and prints `terraform plan` against remote state.
  The plan is not uploaded: a saved plan holds the DB password in plain text.
- `deploy-staging` — `environment: staging` (add required reviewers for a manual
  gate), runs `terraform apply` on a fresh plan. Staging delivered by Terraform.
- `deploy-prod` — the existing SSH deploy, `environment: production`.

```
PR   ─► (app CI only: verify + SAST — no terraform)
main ─► tf-plan ─►  [gated] deploy-staging (terraform apply)
                    [gated] deploy-prod    (ssh, existing)
```

### Remote-state bootstrap (done)

State bucket `s3://fitsync-tfstate-437659978621` (versioned, private,
`use_lockfile`) is created and the local staging state has been migrated into it,
so CI and local share one state.

### CD role (bootstrap/)

`bootstrap/` creates, once per account, the GitHub OIDC provider and the role both
repos assume (`AWS_ROLE_ARN` = its `github_cd_role_arn` output). It has its own state
and no workspaces.

- Trust: only `main` and the `staging` / `production` environments of the two repos,
  so feature branches and forks cannot assume it.
- Permissions: `PowerUserAccess` (no IAM) plus an inline policy that may manage only
  `fitsync-staging-*` / `fitsync-prod-*` roles, policies and instance profiles.
- Every role it creates must carry `fitsync-workload-boundary` (all actions except
  IAM, Organizations and Account), so CD can never mint a role stronger than itself.
  `ci_iam.tf` sets that boundary on the EC2 role.

## Rebuild (bootstrap → staging → prod)

Run from `infra/terraform` with an admin AWS profile. Every step that costs money is
marked.

1. **Bootstrap (free).** `cd bootstrap && terraform init && terraform apply`, then set
   `AWS_ROLE_ARN`, `AWS_ACCOUNT_ID`, `AWS_REGION` as repository variables in both repos.
2. **Deploy keys.** One key per environment:
   ```bash
   ssh-keygen -t ed25519 -N "" -C fitsync-staging-deploy -f fitsync-staging-deploy
   ssh-keygen -t ed25519 -N "" -C fitsync-prod-deploy    -f fitsync-prod-deploy
   ```
   Public halves → `STAGING_EC2_PUBLIC_KEY` variable / `TF_VAR_ec2_public_key` for prod;
   private halves → `STAGING_EC2_SSH_KEY` / `PROD_EC2_SSH_KEY` secrets. Never commit them.
3. **Staging (costs money while up).** Either push to `main` with the CD variables
   and secrets set (`tf-plan` → `deploy-staging` → `bootstrap-staging-db` →
   `deploy-staging-app`), or locally:
   ```bash
   terraform workspace select staging
   TF_VAR_db_password=... TF_VAR_ec2_public_key="$(cat fitsync-staging-deploy.pub)"      terraform apply -var-file=staging.tfvars
   ```
   Then point the frontend's staging config at the new pool, client and EC2 host
   (`terraform output`).
4. **Prod (costs money while up).** Applied locally, not by CD:
   ```bash
   terraform workspace select prod
   TF_VAR_db_password=... TF_VAR_ec2_public_key="$(cat fitsync-prod-deploy.pub)"      terraform apply -var-file=prod.tfvars
   ```
   The RDS restores from `fitsync-db-teardown-20260627-1945` (PostgreSQL 18, master
   user `fitsync_admin`, password reset to `TF_VAR_db_password`). Then, from the prod
   EC2, run `scripts/bootstrap-prod-db.sql` with the six `<SERVICE>_DB_PASSWORD` values:
   it resets the existing service passwords and creates `recommendation_svc`.
   Set `PROD_EC2_HOST` (`ec2_public_ip` output), `DB_URL`
   (`jdbc:postgresql://<rds_endpoint>/fitsync`) and the other prod secrets, then CD's
   `deploy-prod` deploys on the next push to `main`. Finally point the prod CloudFront
   `/api` origin at the new EC2 public DNS name.
5. **Tear down.** Staging: `terraform destroy -var-file=staging.tfvars`. Prod has
   deletion protection; turning it off is a deliberate edit, and destroy leaves the
   `fitsync-prod-db-final` snapshot behind.

## Presentation talking points

1. **Two-layer IaC**: cloud resources (Terraform) + runtime orchestration
   (docker-compose + GitHub Actions) — both version-controlled and reproducible.
2. **Evidence**: `terraform apply` output + console screenshots + a clean
   `terraform destroy` proving full reproducibility.
3. **Multi-environment**: `staging` and `prod` are both built from code in their own
   workspaces; prod restores its data from a snapshot and carries deletion protection.
4. **Roadmap / concern**: migrate state to an S3 + DynamoDB remote backend; split
   public/private subnets + NAT; move secrets to SSM Parameter Store / Secrets Manager.

## Staging bring-up (deploy→test→promote target)

Stand up the full staging app+DB so `stress-test` / `owasp-zap` can run against it,
then tear it down to stop the bill.

**Prerequisites**
1. Deploy key: see "Rebuild" step 2 (public half → `STAGING_EC2_PUBLIC_KEY` variable, private half → `STAGING_EC2_SSH_KEY` secret).
2. Actions secrets: `STAGING_EC2_SSH_KEY`, `STAGING_DB_PASSWORD`, `STAGING_JWT_SECRET`, `TF_VAR_DB_PASSWORD`; variables `STAGING_EC2_PUBLIC_KEY`, `STAGING_CORS_ALLOWED_ORIGINS` (plus `AWS_ROLE_ARN` / `AWS_ACCOUNT_ID` / `AWS_REGION`).
3. RDS master password at apply time: `export TF_VAR_db_password=...`.

**Order (jobs of the CD workflow on `main`, or local terraform):**
1. `tf-plan` → **`deploy-staging`** (terraform apply: VPC/EC2/RDS/ECR/Cognito).
2. Per-service DB roles in the new staging RDS (the app connects as `*_svc` users) — the `bootstrap-staging-db` job does this after `deploy-staging` (`scripts/bootstrap-staging-db.sql`). By hand: connect as master and create `auth_svc` / `user_svc` / `workout_svc` / `nutrition_svc` / `analytics_svc` / `recommendation_svc` with `STAGING_DB_PASSWORD`, plus their databases — Flyway then runs on first boot.
3. **`deploy-staging-app`** (SSH-deploys the app; prints `http://<staging-eip>:8088`).
4. Set `STRESS_TARGET_URL` / `DAST_TARGET_URL` = `http://<staging-eip>:8088` (+ `STRESS_USER_EMAIL/PASSWORD` of a seeded staging user), then run **`stress-test`** / **`owasp-zap`** (Actions → CD → **Run workflow** with the stress / DAST boxes ticked).
5. Frontend (optional for API stress/DAST): a frontend PR adds `frontend_infra_staging` + `deploy_staging` (s3 sync). Only needed to exercise the SPA.
6. **`terraform destroy -var-file=staging.tfvars`** when done.
