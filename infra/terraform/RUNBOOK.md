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
| `alb.tf` | Application Load Balancer across both public subnets, gateway target group, HTTP listener* | ~$20/month while up |
| `secrets.tf` | Secrets Manager `fitsync-<env>/app` (DB endpoint, generated service passwords, JWT key) + read access for the EC2 role | ~$0.4/month |
| `storage.tf` | S3 media bucket, S3 frontend bucket, CloudFront* | ~free; CloudFront slow |
| `auth.tf` | Cognito user pool + SPA client + hosted domain | free tier, instant |
| `ci_iam.tf` | EC2 instance role/profile, CI deploy policy | free, instant |
| `data.tf` | RDS PostgreSQL* (RDS-managed master password; Multi-AZ when `db_multi_az`) | **costs money**, ~10 min (Multi-AZ doubles it) |
| `compute.tf` | EC2 app host + EIP* + deploy key pair | **costs money** |
| `bootstrap/` | GitHub OIDC provider, `fitsync-github-cd` role, workload boundary, the 7 shared ECR repos (own state, applied once) | free, instant |

`*` gated behind feature flags (`enable_rds`, `enable_ec2` default **false**; `enable_alb`
defaults to true, `db_multi_az` to false and is on in `prod.tfvars`).

## Two-environment model

Both environments are built by Terraform, each in its own workspace. The hand-built
prod of June 2026 was torn down on 2026-06-27 and its snapshots deleted; prod starts
from an empty database.

| Environment | tfvars | State (workspace) | Notes |
|-------------|--------|-------------------|-------|
| **staging** | `staging.tfvars` → `fitsync-staging-*` | `staging` | own Cognito pool, ALB open on :80 for stress/DAST, single-AZ RDS, throwaway |
| **prod** | `prod.tfvars` → `fitsync-prod-*` | `prod` | empty Multi-AZ RDS (Flyway builds it), deletion protection + final snapshot, ALB reachable from CloudFront only |

Both environments pull from the same ECR repositories (`fitsync-<service>`, in
`bootstrap/`): staging runs a commit's images first, and prod deploys the same tag.

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

Enable one flag at a time. No password is typed: RDS manages its master password and
Terraform generates the rest into Secrets Manager.

```bash
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
main ─► docker-image ─┬─ (STAGING_ENABLED=true) tf-plan ─► [gated] deploy-staging ─► bootstrap-db ─► deploy-staging-app
                      │                                                                                   │ success
                      └──────────────────────────────────────────────────────────► [gated] deploy-prod ◄─┘
```

Promotion rule: on a push, prod deploys only after staging succeeded with the same image
tag. With `STAGING_ENABLED` not `true`, staging jobs are skipped (a push never re-creates
a destroyed staging) and a push stops after building images. To deploy prod without
staging, run the workflow by hand with `skip_staging` ticked and a reason: it still needs
the `production` approval, and the run summary records who skipped staging and why.
Add yourself as a required reviewer on the `production` Environment so every prod
deploy, break-glass included, waits for a click.

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
   and secrets set (`tf-plan` → `deploy-staging` → `deploy-staging-app`), or locally:
   ```bash
   terraform workspace select staging
   TF_VAR_ec2_public_key="$(cat fitsync-staging-deploy.pub)" \
     terraform apply -var-file=staging.tfvars
   ```
   Then point the frontend's staging config at the new pool and client, and its
   CloudFront `/api` origin at `alb_dns_name` (`terraform output`).
4. **Prod (costs money while up).** Applied locally, not by CD:
   ```bash
   terraform workspace select prod
   TF_VAR_ec2_public_key="$(cat fitsync-prod-deploy.pub)" \
     terraform apply -var-file=prod.tfvars
   ```
   The database starts empty. Set `PROD_EC2_HOST` (`ec2_public_ip` output) and push
   to `main`: `deploy-prod` copies `scripts/bootstrap-db.sql` to the host, and
   `ec2-deploy.sh` reads `fitsync-prod/app` and the RDS master secret, creates the six
   service roles and schemas, renders the DB settings and JWT key into `.env`, and
   starts the stack; Flyway builds the tables and seeds on first boot. Finally point
   the prod CloudFront `/api` origin at `alb_dns_name`.
   Demo data: sign in once with each demo account (auth-service creates the user on first
   sign-in), then run `scripts/seed-demo-fitsync.sql` against the RDS as the master from
   the EC2 (credentials in the RDS-managed secret). `scripts/demo-reseed.ps1` only
   drives the local stack.
5. **Tear down.** Staging: `terraform destroy -var-file=staging.tfvars`. Prod has
   deletion protection; turning it off is a deliberate edit, and destroy leaves the
   `fitsync-prod-db-final` snapshot behind.

## Presentation talking points

1. **Two-layer IaC**: cloud resources (Terraform) + runtime orchestration
   (docker-compose + GitHub Actions) — both version-controlled and reproducible.
2. **Evidence**: `terraform apply` output + console screenshots + a clean
   `terraform destroy` proving full reproducibility.
3. **Multi-environment**: `staging` and `prod` are both built from code in their own
   workspaces; prod carries deletion protection and a final snapshot.
4. **Roadmap / concern**: a second app host behind the ALB needs Kafka and Redis moved
   off the host and a lock on the scheduled jobs and the outbox relay first; split
   public/private subnets + NAT.

## Staging bring-up (deploy→test→promote target)

Stand up the full staging app+DB so `stress-test` / `owasp-zap` can run against it,
then tear it down to stop the bill.

**Prerequisites**
1. Deploy key: see "Rebuild" step 2 (public half → `STAGING_EC2_PUBLIC_KEY` variable, private half → `STAGING_EC2_SSH_KEY` secret).
2. Variables `STAGING_ENABLED=true`, `STAGING_EC2_PUBLIC_KEY`, `STAGING_CORS_ALLOWED_ORIGINS` (plus `AWS_ROLE_ARN` / `AWS_ACCOUNT_ID` / `AWS_REGION`); secret `STAGING_EC2_SSH_KEY`. No database or JWT secret: they live in Secrets Manager.

**Order (jobs of the CD workflow on `main`, or local terraform):**
1. `tf-plan` → **`deploy-staging`** (terraform apply: VPC, ALB, EC2, RDS, Cognito, Secrets Manager).
2. **`deploy-staging-app`** (SSH-deploys the app; `ec2-deploy.sh` bootstraps the six service roles from `fitsync-staging/app` before starting the stack, then the job checks the gateway health on the host).
3. Set `STRESS_TARGET_URL` / `DAST_TARGET_URL` = `http://<alb_dns_name>` (+ `STRESS_USER_EMAIL/PASSWORD` of a seeded staging user), then run **`stress-test`** / **`owasp-zap`** (Actions → CD → **Run workflow** with the stress / DAST boxes ticked).
4. Frontend: point the staging SPA at the new pool, client and ALB (frontend PR).
5. **`terraform destroy -var-file=staging.tfvars`** when done.
