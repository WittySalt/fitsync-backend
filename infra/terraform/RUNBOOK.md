# FitSync — Infrastructure as Code (Terraform)

Declarative provisioning of the FitSync AWS environment. This is the **cloud-resource
layer** of our IaC story; the **runtime layer** (container orchestration) lives in
`backend/docker-compose.prod.yml`, `backend/scripts/ec2-deploy.sh`, and `.gitlab-ci.yml`.

> **Location:** this lives at `backend/infra/terraform/` (inside the backend repo, so
> the GitLab pipeline can run it). **State:** remote in S3
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
| `registry.tf` | 6× ECR repos (one per service) + lifecycle policy | free (empty), instant |
| `storage.tf` | S3 media bucket, S3 frontend bucket, CloudFront* | ~free; CloudFront slow |
| `auth.tf` | Cognito user pool + SPA client + hosted domain | free tier, instant |
| `ci_iam.tf` | EC2 instance role/profile, CI deploy policy | free, instant |
| `data.tf` | RDS PostgreSQL* | **costs money**, ~10 min |
| `compute.tf` | EC2 app host + EIP* | **costs money** |

`*` gated behind feature flags (`enable_cloudfront`, `enable_rds`, `enable_ec2`),
default **false**, so the first apply is free and instant.

## Two-environment model

Two environments are **defined** (`staging.tfvars`, `prod.tfvars`), but only
**staging is run for now**. The existing live production app stays on its
hand-built environment, which Terraform deliberately does **not** manage until
a deliberate post-defense cut-over.

| Environment | tfvars | State (workspace) | Status |
|-------------|--------|-------------------|--------|
| **staging** | `staging.tfvars` → `fitsync-staging-*` | `staging` | ✅ run now (free footprint) |
| **prod** | `prod.tfvars` → `fitsync-prod-*` | `prod` | ⏸️ reserved — apply at cut-over |
| existing live prod | (not managed) | — | 🔒 untouched, serves the demo |

Resource names are env-prefixed, so nothing here collides with the existing
unprefixed prod resources (`fitsync-db`, `fitsync-media`, `fitsync-frontend-prod`,
`fitsync-<svc>` ECR repos).

## Prerequisites

- Terraform ≥ 1.5 (`terraform version`)
- AWS credentials configured (`aws sts get-caller-identity` works)
- Region: `ap-southeast-1`

## Run staging (the only thing to do now)

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

## CI/CD pipeline (GitLab)

Terraform runs **only on `main` (CD)** — never on merge requests — the same rule the
container build (`docker_image`) already follows. MR pipelines stay app-focused
(tests + SAST). `terraform plan` validates internally, so there is no separate
validate job.

- **CD** (`main`, `iac` stage): `tf_plan` — assumes the AWS role, `terraform plan`
  against remote state, publishes `tfplan` as an artifact.
- **CD** (`main`, `deploy` stage): `deploy_staging` — **manual**, `environment: staging`,
  consumes `tfplan` and runs `terraform apply`. Staging delivered by Terraform.
- **CD** (`main`, `deploy` stage): `deploy_prod` — **unchanged** existing SSH deploy,
  `environment: production`.

```
MR   ─► (app CI only: verify + SAST — no terraform)
main ─► tf_plan ─►  [manual] deploy_staging (terraform apply)
                    [manual] deploy_prod     (ssh, existing)
```

### Remote-state bootstrap (done)

State bucket `s3://fitsync-tfstate-437659978621` (versioned, private,
`use_lockfile`) is created and the local staging state has been migrated into it,
so CI and local share one state.

### ⚠️ Remaining prerequisite — CI role permissions

The pipeline assumes `$AWS_ROLE_ARN` (GitLab OIDC). For `tf_plan` / `deploy_staging`
to provision, **that role needs create permissions** for VPC, ECR, S3, Cognito, IAM
(the existing role is deploy-scoped: ECR push + SSM). Attach a broader policy
(scoped IaC policy, or `PowerUserAccess` + IAM, for the capstone) to the GitLab CI
role before enabling `deploy_staging`. Also add CI/CD variable `TF_VAR_db_password`
if you later set `enable_rds=true`.

## Cut-over plan (AFTER the defense — do NOT run before)

The reserved `prod` interface (`prod.tfvars` → `fitsync-prod-*`) is how the live
app eventually moves onto IaC. Two viable strategies:

- **Greenfield + cut-over**: `terraform workspace new prod` →
  `terraform apply -var-file=prod.tfvars` to stand up a clean prod, deploy the 6
  services, migrate RDS data, then switch CloudFront/DNS traffic and retire the
  old box. Cleanest end state.
- **Import in place**: bring the existing hand-built resources under Terraform
  with `terraform import` (no rebuild), then `terraform plan` until it shows
  "No changes". Lower disruption, but resource names won't match the prefixed
  scheme — expect a one-off config reconciliation.

Add `lifecycle { prevent_destroy = true }` to imported/prod RDS + EC2 first.

## Bringing the EXISTING prod under management (import, no rebuild)

The live prod resources were created by hand. To codify them **without recreating**
anything, import them into a separate `prod` state and confirm zero drift:

```bash
# example — repeat per existing resource with its real AWS id
terraform import 'aws_ecr_repository.service["api-gateway"]' fitsync-api-gateway
terraform import aws_cognito_user_pool.main ap-southeast-1_myVyuOmPD
terraform plan    # goal: "No changes" => code matches live infra
```

Add `lifecycle { prevent_destroy = true }` to imported RDS/EC2 before applying.

## Presentation talking points

1. **Two-layer IaC**: cloud resources (Terraform) + runtime orchestration
   (docker-compose + GitLab CI) — both version-controlled and reproducible.
2. **Evidence**: `terraform apply` output + console screenshots + a clean
   `terraform destroy` proving full reproducibility.
3. **Multi-environment**: `staging` provisioned greenfield via `apply`; `prod`
   brought under management via `import` with `prevent_destroy` guards.
4. **Roadmap / concern**: migrate state to an S3 + DynamoDB remote backend; split
   public/private subnets + NAT; move secrets to SSM Parameter Store / Secrets Manager.

## Staging bring-up (deploy→test→promote target)

Stand up the full staging app+DB so `stress_test` / `owasp_zap` can run against it,
then tear it down to stop the bill.

**Prerequisites**
1. EC2 key pair: `aws ec2 create-key-pair --key-name fitsync-staging --query KeyMaterial --output text > fitsync-staging.pem` (private key → CI var `STAGING_EC2_SSH_KEY`). `ec2_key_name` in `staging.tfvars` already references it.
2. CI/CD variables: `STAGING_EC2_SSH_KEY`, `STAGING_DB_PASSWORD`, `STAGING_JWT_SECRET` (plus the existing `AWS_ROLE_ARN` / `AWS_ACCOUNT_ID` / `AWS_REGION`).
3. RDS master password at apply time: `export TF_VAR_db_password=...`.

**Order (all manual jobs on a `main` pipeline, or local terraform):**
1. `tf_plan` → **`deploy_staging`** (terraform apply: VPC/EC2/RDS/ECR/Cognito).
2. Create the per-service DB roles in the new staging RDS (the app connects as `*_svc` users). Connect as master and create `auth_svc` / `user_svc` / `workout_svc` / `nutrition_svc` / `analytics_svc` with `STAGING_DB_PASSWORD`, plus their databases — Flyway then runs on first boot.
3. **`deploy_staging_app`** (SSH-deploys the app; prints `http://<staging-eip>:8088`).
4. Set `STRESS_TARGET_URL` / `DAST_TARGET_URL` = `http://<staging-eip>:8088` (+ `STRESS_USER_EMAIL/PASSWORD` of a seeded staging user), then run **`stress_test`** / **`owasp_zap`** (verify-staging stage).
5. Frontend (optional for API stress/DAST): FE MR adds `frontend_infra_staging` + `deploy_staging` (s3 sync). Only needed to exercise the SPA.
6. **`terraform destroy -var-file=staging.tfvars`** when done.
