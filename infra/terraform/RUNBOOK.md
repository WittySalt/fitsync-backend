# FitSync — Infrastructure as Code (Terraform)

Declarative provisioning of the FitSync AWS environment. This is the **cloud-resource
layer** of our IaC story; the **runtime layer** (container orchestration) lives in
`docker-compose.prod.yml`, `scripts/ec2-deploy.sh`, and `.github/workflows/cd.yml`.

> **Location:** this lives at `infra/terraform/` (inside the backend repo, so
> the CD workflow can run it). **State:** remote in S3
> (`s3://fitsync-tfstate-437659978621`, native `use_lockfile` locking) — local and CI
> share one state. `terraform init` requires AWS credentials.

```
Terraform  ──►  AWS resources (VPC, ECR, S3, Cognito, IAM, RDS, EC2, ALB, ACM, CloudTrail)
                                   │
docker-compose.prod.yml + ec2-deploy.sh  ──►  containers on the EC2 host
```

## What gets created

| File | Resources | Cost / speed |
|------|-----------|--------------|
| `network.tf` | VPC, public subnets (app host, ALB) with the IGW route, private subnets with no internet route (RDS), security groups (no SSH port) | free, instant |
| `alb.tf` | Application Load Balancer across both public subnets, gateway target group, HTTP listener* | ~$20/month while up |
| `origin_tls.tf` | ACM certificate, alias record and HTTPS listener for the ALB when `origin_hostname` is set (TLS from CloudFront) | free, a few minutes for validation |
| `secrets.tf` | Secrets Manager `fitsync-<env>/app` (DB endpoint, generated service passwords, JWT key) + read access for the EC2 role | ~$0.4/month |
| `storage.tf` | S3 media bucket; the EC2 role may upload, and delete under `avatars/` | ~free |
| `auth.tf` | Cognito user pool + SPA client + hosted domain (staging); `AdminDeleteUser` on the environment's pool for the account purge | free tier, instant |
| `cognito_prod.tf` | adopts the hand-built prod pool, its client and domain by `import` (prod only, never recreated) | free, instant |
| `media_prod.tf` | adopts the hand-built prod media bucket `fitsync-media` and its policy, public access block, encryption and ownership settings by `import` (prod only, never recreated or emptied) | free, instant |
| `ci_iam.tf` | EC2 instance role/profile, CI deploy policy, SSM ssh operator policy | free, instant |
| `email.tf` | `ses:SendEmail` for the EC2 role pinned to `notify_email_sender` (+ an address identity only when `notify_email_create_identity`) | free (SES bills per email) |
| `data.tf` | RDS PostgreSQL* (RDS-managed master password; Multi-AZ when `db_multi_az`) | **costs money**, ~10 min (Multi-AZ doubles it) |
| `compute.tf` | EC2 app host with an encrypted root volume + EIP* + deploy key pair | **costs money** |
| `bootstrap/` | GitHub OIDC provider, `fitsync-github-cd` role (backend), `fitsync-frontend-github-deploy` role (frontend), workload boundary, the 8 shared ECR repos, the SES sending domain + its hosted zone and DNS records when `notify_email_domain` is set, default EBS encryption, the CloudTrail trail and its log bucket (own state, applied by hand) | free, instant (the hosted zone is $0.50/month, the trail's S3 storage cents) |

`*` gated behind feature flags (`enable_rds`, `enable_ec2` default **false**; `enable_alb`
defaults to true, `db_multi_az` to false and is on in `prod.tfvars`).

## Two-environment model

Both environments are built by Terraform, each in its own workspace. The hand-built
prod of June 2026 was torn down on 2026-06-27 and its snapshots deleted; prod starts
from an empty database.

| Environment | tfvars | State (workspace) | Notes |
|-------------|--------|-------------------|-------|
| **staging** | `staging.tfvars` → `fitsync-staging-*` | `staging` | own Cognito pool, ALB reachable from CloudFront only (stress/DAST go through `https://staging.fitsyncapp.click`), single-AZ RDS, throwaway |
| **prod** | `prod.tfvars` → `fitsync-prod-*` | `prod` | empty Multi-AZ RDS (Flyway builds it), deletion protection + final snapshot, ALB reachable from CloudFront only |

Both environments pull from the same ECR repositories (`fitsync-<service>`, in
`bootstrap/`): staging runs a commit's images first, and prod deploys the same tag.

The prod Cognito pool `ap-southeast-1_myVyuOmPD` (holds real users) and the prod media bucket
`fitsync-media` (holds the uploaded images) were built by hand and are adopted by import in the
prod workspace, never recreated ("Prod Cognito under Terraform", "Prod media bucket under
Terraform"). The frontend bucket and CloudFront distribution of prod belong to the frontend
repository's Terraform.

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
  The plan is not uploaded: a saved plan can hold generated secrets in plain text.
- `deploy-staging` — `environment: staging`, runs `terraform apply` on a fresh plan.
- `deploy-staging-app` — SSH deploy of this commit's images to staging (over SSM once the
  proposed cd.yml change in "Hardening switch-over" is merged).
- `deploy-prod` — SSH deploy to prod, `environment: production`, manual runs only.

```
PR   ─► (app CI only: verify + SAST — no terraform)
push to main ─► CI (tests, SAST)
manual run ─► ci-gate (CI passed for this commit?) ─► docker-image ─┬─ (STAGING_ENABLED=true) tf-plan ─► deploy-staging ─► deploy-staging-app
                                                                    │                                                           │ success
                                                                    └─────────────────── (manual run, deploy_prod) deploy-prod ◄┘
```

Promotion rule: CD never starts by itself. A person runs it on main (Actions → CD → Run
workflow) once CI has passed, and `ci-gate` refuses any commit without a successful CI
run. A run deploys staging; prod deploys only with `deploy_prod` ticked, and only after
staging succeeded in that same run with the same image tag. With `STAGING_ENABLED` not
`true`, staging jobs are skipped, so a run never re-creates a destroyed staging. To deploy prod without
staging, tick `skip_staging` and give a reason; the run summary records who skipped
staging and why.

GitHub offers required reviewers on Environments only for public repositories or paid
plans, so this private repository gets its prod gate from the workflow instead: nothing
deploys unless a person starts a run, and prod needs `deploy_prod` ticked in it.

### Remote-state bootstrap (done)

State bucket `s3://fitsync-tfstate-437659978621` (versioned, private,
`use_lockfile`) is created and the local staging state has been migrated into it,
so CI and local share one state.

### CD role (bootstrap/)

`bootstrap/` creates, once per account, the GitHub OIDC provider and the role the
backend CD assumes (`AWS_ROLE_ARN` = its `github_cd_role_arn` output). It has its own
state and no workspaces.

- Trust: only `main` and the `staging` / `production` environments of the backend
  repo, so feature branches and forks cannot assume it. (Until phase 2 of the frontend
  switch-over below is applied it also still trusts the frontend repo.)
- Permissions: `PowerUserAccess` (no IAM) plus an inline policy that may manage only
  `fitsync-staging-*` / `fitsync-prod-*` roles, policies and instance profiles.
- Every role it creates must carry `fitsync-workload-boundary` (all actions except
  IAM, Organizations and Account), so CD can never mint a role stronger than itself.
  `ci_iam.tf` sets that boundary on the EC2 role.

### Frontend CD role (bootstrap/frontend_cd.tf)

The frontend CD only syncs the built SPA to the two frontend buckets and invalidates
the two distributions; it runs no Terraform. It assumes its own role,
`fitsync-frontend-github-deploy` (`frontend_cd_role_arn` output), instead of the
PowerUser `fitsync-github-cd`.

- Trust: only `repo:WittySalt/fitsync-frontend:environment:staging` and
  `...:environment:production`. Only `deploy-staging` and `deploy-production` assume a
  role and both run in an environment; `ci-gate` and `build` have no `id-token`
  permission, so `main`'s ref subject is not trusted.
- Permissions, one inline policy: `s3:ListBucket` on `frontend_buckets`;
  `s3:PutObject`, `s3:DeleteObject`, `s3:GetObject` on their objects;
  `cloudfront:CreateInvalidation`, `cloudfront:GetInvalidation` on
  `frontend_distribution_ids`. The buckets use SSE-S3, so no KMS permission is needed.
- No permissions boundary: it is created by an admin here, not by CD, and holds no IAM.

#### Switch-over from the shared role (two phases, in this order)

The frontend's `AWS_ROLE_ARN` pointed at `fitsync-github-cd`, and a GitLab-era role
`fitsync-frontend-deploy` (made by hand, trusts the `gitlab.com` OIDC provider, last used
2026-06-26) was still in the account. The variable `frontend_cd_phase` splits the change
so the frontend CD never loses access:

1. **Phase 1** (the default) with an admin profile:
   ```bash
   cd infra/terraform/bootstrap
   terraform init
   terraform plan -out=frontend-cd-1.tfplan
   # expect: 3 to import, 2 to add, 0 to change, 0 to destroy
   #   add:    aws_iam_role.frontend_cd, aws_iam_role_policy.frontend_cd_deploy
   #   import: aws_iam_role.legacy_frontend_deploy[0] and its two inline policies
   terraform apply frontend-cd-1.tfplan
   terraform output -raw frontend_cd_role_arn
   ```
   The imports only adopt the old role into state; nothing in AWS changes on it.
2. **Point the frontend at the new role** (the person with repo admin runs this):
   ```bash
   gh variable set AWS_ROLE_ARN --repo WittySalt/fitsync-frontend      --body "arn:aws:iam::437659978621:role/fitsync-frontend-github-deploy"
   ```
3. **Prove it.** In the frontend repo run Actions → CD → Run workflow on `main` with
   `deploy_prod` unticked. `deploy-staging` must pass its sync, invalidation and smoke
   test. If it fails with AccessDenied, set `AWS_ROLE_ARN` back to
   `arn:aws:iam::437659978621:role/fitsync-github-cd` (still trusted in phase 1) and fix
   the policy before going on. The first prod deploy with `deploy_prod` ticked can come
   after phase 2; the prod statements are the same shape on the prod bucket and
   distribution.
4. **Phase 2**, only after step 3 passed:
   ```bash
   terraform plan -var frontend_cd_phase=2 -out=frontend-cd-2.tfplan
   # expect: 0 to add, 1 to change, 3 to destroy
   #   change:  aws_iam_role.github_cd (trust loses the three fitsync-frontend subjects)
   #   destroy: aws_iam_role.legacy_frontend_deploy[0] and its two inline policies
   terraform apply frontend-cd-2.tfplan
   ```
   Keep passing `-var frontend_cd_phase=2` on every later bootstrap apply until the
   cleanup below is merged; a plain apply would add the frontend back to
   `fitsync-github-cd`'s trust and try to import a role that no longer exists.

The `gitlab.com` OIDC provider stays: the backend's GitLab-era role `fitsync-gitlab-ci`
still trusts it. Retiring both is a separate change.

**Cleanup after phase 2** (follow-up PR): delete the "Retiring the GitLab-era role"
section of `frontend_cd.tf`, the `frontend_cd_phase` variable and the
`cd_trusted_repositories` phase-1 branch in `main.tf`; the plan must then show no
changes.

## Rebuild (bootstrap → staging → prod)

Run from `infra/terraform` with an admin AWS profile. Every step that costs money is
marked.

1. **Bootstrap (free).** `cd bootstrap && terraform init && terraform apply`, then set
   `AWS_ROLE_ARN`, `AWS_ACCOUNT_ID`, `AWS_REGION` as repository variables in both repos
   (backend: `github_cd_role_arn`; frontend: `frontend_cd_role_arn`).
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
   The database starts empty, already in the private subnets. Set `PROD_EC2_INSTANCE_ID`
   (`ec2_instance_id` output; `PROD_EC2_HOST` = `ec2_public_ip` until the cd.yml change in
   "Hardening switch-over" is merged) and push
   to `main`: `deploy-prod` copies `scripts/bootstrap-db.sql` to the host, and
   `ec2-deploy.sh` reads `fitsync-prod/app` and the RDS master secret, creates the seven
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

## Adding a service: the ECR repository (bootstrap, by hand)

The ECR repositories live in `bootstrap/registry.tf`, which CD never applies. When a service
is added to its `services` list (notification-service was the latest), apply it once with an
admin profile before the first CD run that ships the service:

```bash
cd infra/terraform/bootstrap
terraform init
terraform plan     # expect: 1 aws_ecr_repository + 1 aws_ecr_lifecycle_policy to add
terraform apply
```

If this is skipped, the `docker-image` job still creates the repository on first push, but
without the lifecycle policy, and a later bootstrap apply then fails because the repository
already exists. In that case import it first:
`terraform import 'aws_ecr_repository.service["notification-service"]' fitsync-notification-service`.

## Email notifications (SES)

notification-service sends email through Amazon SES with the EC2 instance role. Until the
steps below are done it only writes each email to its log, which is the default everywhere.

Mail goes out from the domain `fitsyncapp.click`. The domain is one SES identity for the whole
account and region, shared by staging and prod, so it lives in `bootstrap/email.tf` and is
applied by hand. Each workspace only names its own From address and gets permission to send
from it:

| Environment | `notify_email_sender` / `NOTIFY_EMAIL_SENDER` | From header | Subject |
|---|---|---|---|
| prod | `noreply@fitsyncapp.click` | `FitSync <noreply@fitsyncapp.click>` | as composed |
| staging | `noreply-staging@fitsyncapp.click` | `FitSync Staging <noreply-staging@fitsyncapp.click>` | `[Staging] ` + subject |

The display name and the subject prefix are rendered by `cd.yml` (`FITSYNC_NOTIFY_SENDER_NAME`,
`FITSYNC_NOTIFY_SUBJECT_PREFIX`); nothing needs setting for them.

The domain was registered in this account (437659978621), so Route 53 Registrar created its
public hosted zone here (`Z03373831R8G5N1TEF6LB`) and the registration already points at that
zone's name servers. Bootstrap adopts the zone with an `import` block instead of creating a
new one, writes every SES record into it, and protects it with `prevent_destroy`. The order:

1. **Apply bootstrap** with an admin profile of 437659978621. `notify_email_domain` defaults
   to `fitsyncapp.click`, so no `-var` is needed:

   ```bash
   cd infra/terraform/bootstrap
   terraform init
   terraform plan -out=bootstrap-email.tfplan   # expect 1 to import (the zone), 0 to change, 0 to destroy
   terraform apply bootstrap-email.tfplan
   ```

   The adds are the identity, MAIL FROM, three DKIM CNAMEs, MX, SPF and DMARC (plus anything
   else bootstrap has not applied yet, such as the notification-service ECR repository). To
   receive DMARC aggregate reports add `-var notify_email_dmarc_rua=<mailbox>`; without it the
   record is just `v=DMARC1; p=none;`. Keep passing the same `-var` values on every later
   bootstrap apply (or put them in an untracked `bootstrap/terraform.tfvars`).
2. **The name servers need no change.** The registrar already uses the zone's four name
   servers (`terraform output notify_email_domain_name_servers` lists them). Check with
   `nslookup -type=NS fitsyncapp.click` (or `dig NS fitsyncapp.click +short`).
3. **Wait for SES verification.** SES checks the DKIM CNAMEs itself, usually within minutes
   and at most 72 hours. No mailbox has to click anything.
   `aws sesv2 get-email-identity --email-identity fitsyncapp.click --query '[VerifiedForSendingStatus,DkimAttributes.Status,MailFromAttributes.MailFromDomainStatus]'`
   should read `true`, `SUCCESS`, `SUCCESS` (`terraform refresh` then
   `terraform output notify_email_domain_dkim_status` shows the same DKIM status).

Then set up the environments:

4. **Set the sender in each workspace.** Uncomment `notify_email_sender` in `staging.tfvars`
   and `prod.tfvars` (the two addresses above) and apply each workspace (CD's terraform step
   does it on the next run). `notify_email_create_identity` stays `false`: the workspace then
   only gives the EC2 role `ses:SendEmail` / `ses:SendRawEmail` with `ses:FromAddress` pinned
   to that address (`email.tf`). Set it to `true` only for a sender outside the domain, which
   makes the workspace create an address identity whose verification link someone must click.
   The CD role needs nothing new: `PowerUserAccess` covers SES and the boundary allows it.
5. **Sandbox: verify every recipient too.** A new account is in the SES sandbox: it can send
   only to verified addresses, at most 200 emails a day and 1 per second. For each demo
   account's email address run
   `aws sesv2 create-email-identity --email-identity <recipient>`, and the owner of that
   mailbox must click the link SES sends. Sends to anyone else fail with
   `MessageRejected: Email address is not verified`.
6. **Set the environment variables** in GitHub (Settings → Environments → `staging` /
   `production`, see `.github/CICD-VARIABLES.md`): `NOTIFY_EMAIL_SENDER` = the same address as
   that workspace's `notify_email_sender`, and `NOTIFY_EMAIL_ENABLED=true`. Optionally
   `APP_BASE_URL`, where email links point (prod defaults to
   `https://d26vi6hikexzev.cloudfront.net`; staging to the first https origin of
   `STAGING_CORS_ALLOWED_ORIGINS`). Then run CD; the variables reach the host's `.env`.
7. **Production access (later).** To mail real users, request it in the console (SES →
    Account dashboard → Request production access) with the use case, how addresses are
   collected and how bounces and complaints are handled. AWS answers within about a day;
   after that recipients no longer need verifying. Once DMARC reports show DKIM and SPF
   passing, the policy can move from `p=none` to `p=quarantine`.

Check from the host after a deploy:
`sudo docker logs fitsync-notification-service 2>&1 | grep -i ses`. `AccessDenied` means the
sender in the `.env` differs from `notify_email_sender`; `MessageRejected` means an
identity is not verified yet (the domain, or a sandbox recipient).

## Hardening switch-over: private DB subnets, SSH over SSM, prod Cognito

Four changes landed together in `network.tf`, `data.tf`, `ci_iam.tf`, `cognito_prod.tf` and
`media_prod.tf`. The database moves into private subnets that have no route to the internet,
the app host closes port 22 and is reached with ssh over SSM Session Manager, and the prod
workspace adopts the hand-built prod Cognito pool and the prod media bucket by import. None of
them replaces the database, the user pool, the bucket or the EC2 instance. Both tfvars files carry two transitional lines (`db_subnet_tier = "both"`
and `ssh_ingress_cidrs = ["0.0.0.0/0"]`) so that merging the code changes nothing that would
break a running environment; each line is deleted at its step below.

The plans of 2026-10-09 against the live state, with the transitional lines in place, were:

| Workspace | Plan | What it does |
|---|---|---|
| staging | 6 to add, 2 to change, 0 to destroy | two private subnets, their route table and two associations, the `fitsync-staging-ssm-operator` policy; the DB subnet group gains the private subnets; `fitsync-staging-ci-deploy` gains the SSM session statements |
| prod | 8 to import, 6 to add, 2 to change, 0 to destroy | the same for prod, plus the import of the Cognito pool, its app client and its domain, and of the media bucket with its policy, public access block, encryption and ownership settings, with no change to any of them |

With both transitional lines removed the plans add one in-place change, the app security group
losing its port 22 rule, and still destroy and replace nothing.

### The order

Do the steps in this order. CD must connect through SSM before port 22 closes, and the database
must sit only in private subnets before the group drops the public ones. Nothing here runs from
CD by itself, but a CD run applies `staging.tfvars` as committed, so commit each tfvars cleanup
as soon as it is applied.

1. Merge this branch. It changes nothing live.
2. Staging database: follow "Move RDS into the private subnets" below for staging (snapshot,
   transitional apply, move, verify, then delete the `db_subnet_tier` line and apply).
3. Operator access: set up ssh over SSM on your machine ("SSH over SSM" below) and check that
   `ssh fitsync-staging` works while port 22 is still open.
4. CD: merge the `.github/` change described in "cd.yml over SSM" below, and
   set the `PROD_EC2_INSTANCE_ID` repository variable. Run CD once for staging and check that
   `deploy-staging-app` passes. It now deploys through SSM.
5. Staging SSH port: delete the `ssh_ingress_cidrs` line from `staging.tfvars`, apply staging,
   commit. `Test-NetConnection <staging EIP> -Port 22` must now fail and `ssh fitsync-staging`
   must still work.
6. Prod database, Cognito and media: the same database steps for prod. The first prod apply
   also imports the Cognito pool, client and domain and the media bucket ("Prod Cognito under
   Terraform" and "Prod media bucket under Terraform" below).
7. Prod deploy over SSM: run CD with `deploy_prod` (after a staging run in the same run, or with
   `skip_staging` and a reason) and check that `deploy-prod` passes.
8. Prod SSH port: delete the `ssh_ingress_cidrs` line from `prod.tfvars`, apply prod, commit.
   Afterwards delete the old `PROD_EC2_HOST` variable.

Every local plan and apply passes the deploy key the same way as in "Rebuild", otherwise
Terraform plans to destroy the key pair. From PowerShell in `infra/terraform`:

```powershell
terraform workspace select staging      # or prod
$env:TF_VAR_ec2_public_key = (Get-Content "$HOME\.ssh\fitsync-staging-deploy.pub" -Raw).Trim()   # fitsync-prod-deploy.pub for prod
terraform plan -var-file="staging.tfvars"
terraform apply -var-file="staging.tfvars"
```

On this Windows machine `terraform init` may link providers from the plugin cache; if apply
then fails to start the provider, replace the links under `.terraform\providers` with real
copies, as before.

### Move RDS into the private subnets

**Why not a new subnet group.** For the AWS provider in use (v5.100), `db_subnet_group_name` is
an in-place attribute (`Optional`, `Computed`, not `ForceNew`, sent as `DBSubnetGroupName` on
`ModifyDBInstance`), so Terraform would not plan a replacement. RDS itself refuses the call,
though: an instance cannot move to another subnet group in the same VPC ("The specified DB
subnet group and DB instance are in the same VPC"). The existing group
`fitsync-<env>-db-subnets` is therefore re-pointed to the private subnets instead, and the
instance keeps its group, identifier, endpoint, storage and master secret. RDS also refuses to
remove a subnet that the primary or the standby still uses, so the instance is first moved off
the public subnets with Multi-AZ conversions and one forced failover. Snapshot-and-restore under
the same identifier was the other option; it was rejected because it loses every write after
the snapshot, takes longer, and changes the RDS-managed master secret.

**Downtime.** One forced failover per environment, typically 60 to 120 seconds during which
database connections fail and the services reconnect. Converting between Single-AZ and Multi-AZ
has no downtime but takes 10 to 30 minutes each and adds I/O load. Prod runs without a standby
twice for the length of one conversion. Staging runs as Multi-AZ for about half an hour, which
costs a few cents.

**Run it.** PowerShell in `infra/terraform`, with the workspace and `TF_VAR_ec2_public_key` set
as above and an admin profile. Set `$envName` to `staging` first; prod comes only after staging
is verified.

```powershell
$env:AWS_REGION = 'ap-southeast-1'
$envName = 'staging'                                    # later: 'prod'
$db  = "fitsync-$envName-db"
$grp = "fitsync-$envName-db-subnets"
$azs = @('ap-southeast-1a', 'ap-southeast-1b')          # local.azs, the order of both subnet lists

function Show-Db {
    aws rds describe-db-instances --db-instance-identifier $db `
        --query 'DBInstances[0].[DBInstanceStatus,MultiAZ,AvailabilityZone,SecondaryAvailabilityZone,length(PendingModifiedValues)]' --output text
}
# Waits until the instance is available, has no pending change and MultiAZ is $MultiAz.
function Wait-Db([string]$MultiAz) {
    do { Start-Sleep -Seconds 30; $s = Show-Db; Write-Host $s } until ($s -match "^available\s+$MultiAz\s+.*\s0$")
}
```

1. **Manual snapshot.** Kept until the move is verified in both environments.

   ```powershell
   $snap = "$db-pre-private-" + (Get-Date -Format 'yyyyMMdd-HHmm')
   aws rds create-db-snapshot --db-instance-identifier $db --db-snapshot-identifier $snap
   aws rds wait db-snapshot-available --db-snapshot-identifier $snap
   ```

2. **Baseline.** Record the state the verification compares against: the account and catalogue
   counts of `scripts\demo-reseed-cloud.ps1 -Env $envName` (a read-only dry run) and the
   container states on the host (`ssh fitsync-<env> "sudo docker ps --format '{{.Names}} {{.Status}}'"`).

3. **Transitional apply.** `terraform apply -var-file="$envName.tfvars"` with
   `db_subnet_tier = "both"` creates the private subnets and adds them to the group (the plan
   counts are in the table above). Then read the subnet ids:

   ```powershell
   $pub  = terraform output -json public_subnet_ids  | Out-String | ConvertFrom-Json
   $priv = terraform output -json private_subnet_ids | Out-String | ConvertFrom-Json
   ```

   Do not run another apply until step 9: an apply with `"both"` adds back the subnet that
   step 5 removes.

4. **Prod only: drop the standby.** `aws rds modify-db-instance --db-instance-identifier $db --no-multi-az --apply-immediately`, then `Wait-Db False`.

5. **Free the other AZ.** The primary's AZ is `$a`, the other one `$o`. Remove the public subnet
   of `$o`, which nothing uses now:

   ```powershell
   $a = [array]::IndexOf($azs, (aws rds describe-db-instances --db-instance-identifier $db --query 'DBInstances[0].AvailabilityZone' --output text))
   $o = 1 - $a
   aws rds modify-db-subnet-group --db-subnet-group-name $grp --subnet-ids $pub[$a] $priv[0] $priv[1]
   ```

6. **Standby into the private subnet of `$o`.** It is the only subnet left in that AZ.
   `aws rds modify-db-instance --db-instance-identifier $db --multi-az --apply-immediately`, then
   `Wait-Db True`.

7. **Fail over (the downtime).** The primary moves to `$priv[$o]`:
   `aws rds reboot-db-instance --db-instance-identifier $db --force-failover`, then
   `Wait-Db True`. `Show-Db` must now name the AZ `$azs[$o]` as the primary.

8. **Drop the old primary and its subnet.** `aws rds modify-db-instance --db-instance-identifier $db --no-multi-az --apply-immediately`, `Wait-Db False`, then
   `aws rds modify-db-subnet-group --db-subnet-group-name $grp --subnet-ids $priv[0] $priv[1]`.
   **Prod only:** restore the standby with `aws rds modify-db-instance --db-instance-identifier $db --multi-az --apply-immediately` and `Wait-Db True`; it lands in `$priv[$a]`.

9. **Align Terraform.** Delete the `db_subnet_tier` line from `$envName.tfvars`, run
   `terraform plan -var-file="$envName.tfvars"`, and expect no change on `aws_db_instance.main`
   or `aws_db_subnet_group.main` (the group already holds only the private subnets, and
   `multi_az` is back to the tfvars value). Apply if anything else is pending, and commit the
   tfvars change. No `terraform state` command is needed: the resources keep their ids.

**Verify** after step 8 and again after step 9.

- The network interfaces of the instance sit only in the private subnets:
  `aws ec2 describe-network-interfaces --filters "Name=description,Values=RDSNetworkInterface" "Name=vpc-id,Values=$(terraform output -raw vpc_id)" --query 'NetworkInterfaces[].[SubnetId,PrivateIpAddress]' --output text`
  lists only ids from `$priv`, with addresses in 10.20.10.0/24 or 10.20.11.0/24.
- The endpoint is unchanged (`terraform output -raw rds_endpoint`), and from the host it resolves
  to that private address: `ssh fitsync-<env> "getent hosts <endpoint without :5432>"`.
- Every container is running and healthy again:
  `ssh fitsync-<env> "sudo docker ps --format '{{.Names}} {{.Status}}'"` shows the same list as
  the baseline, and `ssh fitsync-<env> "curl -fsS http://localhost:8088/actuator/health"` answers
  `UP`. Restart a service that stayed unhealthy after the failover with `sudo docker restart <name>`.
- The rows are all there: `scripts\demo-reseed-cloud.ps1 -Env <env>` (dry run) prints the same
  account and catalogue counts as the baseline. Storage never moves in this procedure, so a
  difference points at writes made in between, not at lost data.
- Sign in on the site and open the dashboard.

**Rollback.**

- Before step 7 nothing has moved. Undo step 6 with `--no-multi-az` if it ran, put all four
  subnets back with `aws rds modify-db-subnet-group --db-subnet-group-name $grp --subnet-ids $pub[0] $pub[1] $priv[0] $priv[1]`,
  and on prod turn Multi-AZ back on.
- After step 7 the database runs in a private subnet. The app reaches it through the VPC's local
  route as before, so there is no network reason to go back. If you must, put all four subnets
  back in the group and run steps 4 to 8 again with the roles of `$pub` and `$priv` swapped,
  then set `db_subnet_tier = "public"`.
- Last resort for data: restore the manual snapshot to a new identifier
  (`aws rds restore-db-instance-from-db-snapshot --db-instance-identifier $db-restore --db-snapshot-identifier $snap --db-subnet-group-name $grp --vpc-security-group-ids <db sg>`).
  This loses every write after the snapshot and needs the identifiers swapped and the Terraform
  state re-imported, so treat it as disaster recovery, not as the rollback of this change.
- Delete the manual snapshots once both environments are verified (`aws rds delete-db-snapshot`).

### SSH over SSM

The app host has no inbound SSH port. ssh and scp open a Session Manager session with the
`AWS-StartSSHSession` document, which forwards to port 22 on the host from the inside, and then
log in with the same deploy key as before. The host already runs the SSM agent and its role
carries `AmazonSSMManagedInstanceCore`; it reaches the SSM endpoints through the internet
gateway of its public subnet.

**Who may start a session.** `ci_iam.tf` writes the rights down once in `ssm_ssh_start`:
`ssm:StartSession` on this environment's instance and the `AWS-StartSSHSession` document only,
with `ssm:SessionDocumentAccessCheck`, so a session without that document (a plain shell) is
refused. `ssm:TerminateSession`, `ssm:ResumeSession` and `ssmmessages:OpenDataChannel` are
allowed only on the caller's own sessions.

- **CD.** The statements are part of `fitsync-<env>-ci-deploy` (with CD's own session names,
  `GitHubActions-*`). That policy is attached to nothing today. The CD role
  `fitsync-github-cd` holds `PowerUserAccess`, which already allows SSM sessions, so CD needs
  no new grant; the policy records what CD needs and becomes its grant if `PowerUserAccess` is
  ever narrowed (attach it in `bootstrap/`, since this stack may not attach policies to the CD
  role).
- **Operators.** Each workspace creates the managed policy `fitsync-<env>-ssm-operator` (output
  `ssm_operator_policy_arn`). An admin profile such as `admin-cli` needs nothing more. For a
  least-privilege operator user, attach it by hand, because CD's role may not touch IAM users:
  `aws iam attach-user-policy --user-name <operator> --policy-arn <ssm_operator_policy_arn>`.

**Session logging.** Not enabled. Session Manager cannot record the content of SSH sessions
(they are encrypted end to end inside the tunnel), so S3 or CloudWatch logging would hold
nothing for these sessions while costing a bucket or a log group. CloudTrail already records
every `StartSession` and `TerminateSession` with the caller and the target instance, which is
the audit trail that matters here, and the account trail (`bootstrap/account_security.tf`) keeps
those records in S3 for 365 days.

**Local setup (Windows).**

1. AWS CLI v2 (`aws --version`).
2. The Session Manager plugin: download and run the installer from
   `https://s3.amazonaws.com/session-manager-downloads/plugin/latest/windows/SessionManagerPluginSetup.exe`,
   open a new terminal, and check `session-manager-plugin` prints its banner. (macOS:
   `brew install --cask session-manager-plugin`.)
3. The deploy keys stay where they are (`~/.ssh/fitsync-staging-deploy`, `~/.ssh/fitsync-prod-deploy`).
4. Add the hosts to `~/.ssh/config`. The instance ids are the `ec2_instance_id` outputs
   (2026-10-09: staging `i-08b745d9535de0b43`, prod `i-0d0a9c1e503ce3d94`; a rebuilt host has a
   new id).

   ```
   Host fitsync-staging
     HostName i-08b745d9535de0b43
     User ec2-user
     IdentityFile ~/.ssh/fitsync-staging-deploy
     IdentitiesOnly yes
     ProxyCommand aws ssm start-session --region ap-southeast-1 --target %h --document-name AWS-StartSSHSession --parameters portNumber=%p

   Host fitsync-prod
     HostName i-0d0a9c1e503ce3d94
     User ec2-user
     IdentityFile ~/.ssh/fitsync-prod-deploy
     IdentitiesOnly yes
     ProxyCommand aws ssm start-session --region ap-southeast-1 --target %h --document-name AWS-StartSSHSession --parameters portNumber=%p
   ```

   Add `--profile <name>` to the ProxyCommand when the session should not use the default
   credentials. If Windows OpenSSH cannot start `aws` directly, wrap it:
   `ProxyCommand C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe "aws ssm start-session --region ap-southeast-1 --target %h --document-name AWS-StartSSHSession --parameters portNumber=%p"`.
5. `ssh fitsync-staging` and `scp file fitsync-staging:/tmp/` then work as before. The host key
   is new to `known_hosts` under the instance id, so accept it once.

The cloud reseed helper (`scripts\demo-reseed-cloud.ps1`) uses the same tunnel; its
`scripts\demo-cloud.env` takes `STAGING_INSTANCE_ID` / `PROD_INSTANCE_ID` instead of the old
`*_HOST` addresses (see `scripts\README.md` "Cloud reseed"). psql still runs in a postgres:16
container on the host, which reaches the private RDS through the VPC.

### cd.yml over SSM

Applied in `.github/workflows/cd.yml` with the owner's approval. The change below switches both deploy
jobs from ssh/scp against the public IP to the same commands over SSM: it installs the Session
Manager plugin on the runner, takes the instance id from `terraform output ec2_instance_id` for
staging and from the new `PROD_EC2_INSTANCE_ID` repository variable for prod, waits until the
host's SSM agent is online, writes one `fitsync-host` entry into `~/.ssh/config`, and keeps every
scp. `ssh-keyscan` cannot run through a ProxyCommand, so the host key is accepted on first use
over the tunnel, which only reaches the instance named by the job's AWS role.

It works with port 22 still open, so it must be live (step 4 of "The order") before the
`ssh_ingress_cidrs` line is removed. The same diff updates `.github/CICD-VARIABLES.md` for the
new variable. Saved as a file, it applies with `git apply` to `main` as of this branch.

The same protected edit also switches the analytics cache to Redis in the cloud: in both rendered
`.env.deploy` blocks (`deploy-staging-app` and `deploy-prod`), `SPRING_CACHE_TYPE=simple` becomes
`SPRING_CACHE_TYPE=redis`. `docker-compose.prod.yml` already defaults to `redis` and caps the
container as a cache; until cd.yml changes, the rendered `.env` keeps forcing `simple`.

```diff
--- a/.github/workflows/cd.yml
+++ b/.github/workflows/cd.yml
@@ -227,6 +227,13 @@
       - uses: hashicorp/setup-terraform@v3
         with:
           terraform_version: ${{ env.TF_VERSION }}
+      - name: Install the Session Manager plugin
+        # ssh and scp reach the host through SSM Session Manager (port 22 is closed).
+        run: |
+          curl -fsSLo /tmp/session-manager-plugin.deb \
+            "https://s3.amazonaws.com/session-manager-downloads/plugin/latest/ubuntu_64bit/session-manager-plugin.deb"
+          sudo dpkg -i /tmp/session-manager-plugin.deb
+          session-manager-plugin --version
       - name: Render .env + deploy via ec2-deploy.sh
         env:
           AWS_ACCOUNT_ID: ${{ vars.AWS_ACCOUNT_ID }}
@@ -246,7 +253,7 @@
           set -euo pipefail
           cd infra/terraform
           terraform init && terraform workspace select staging
-          STAGING_HOST="$(terraform output -raw ec2_public_ip)"
+          STAGING_INSTANCE_ID="$(terraform output -raw ec2_instance_id)"
           # auth-service must trust staging's own pool, not the prod default in compose.
           COGNITO_POOL_ID="$(terraform output -raw cognito_user_pool_id)"
           COGNITO_CLIENT_ID="$(terraform output -raw cognito_spa_client_id)"
@@ -268,7 +275,28 @@
           # GitHub drops a secret's trailing newline; OpenSSH refuses a key without one.
           printf '%s\n' "$STAGING_EC2_SSH_KEY" | tr -d '\r' > ~/.ssh/id_ed25519
           chmod 600 ~/.ssh/id_ed25519
-          ssh-keyscan -H "$STAGING_HOST" >> ~/.ssh/known_hosts
+          # Port 22 is closed: ssh and scp tunnel through SSM Session Manager to the instance
+          # id, authenticated by this job's AWS role, and still log in with the deploy key.
+          # ssh-keyscan cannot use a ProxyCommand, so the host key is accepted on first use
+          # over that tunnel. A freshly built host needs a minute before its agent is online.
+          for i in $(seq 1 30); do
+            status="$(aws ssm describe-instance-information --filters "Key=InstanceIds,Values=$STAGING_INSTANCE_ID" \
+              --query 'InstanceInformationList[0].PingStatus' --output text)"
+            [ "$status" = "Online" ] && break
+            [ "$i" = 30 ] && { echo "SSM agent on $STAGING_INSTANCE_ID is not online ($status)" >&2; exit 1; }
+            sleep 10
+          done
+          cat > ~/.ssh/config <<SSHCFG
+          Host fitsync-host
+            HostName $STAGING_INSTANCE_ID
+            User ec2-user
+            IdentityFile ~/.ssh/id_ed25519
+            IdentitiesOnly yes
+            StrictHostKeyChecking accept-new
+            ServerAliveInterval 30
+            ProxyCommand aws ssm start-session --region $AWS_REGION --target %h --document-name AWS-StartSSHSession --parameters portNumber=%p
+          SSHCFG
+          chmod 600 ~/.ssh/config
           cat > .env.deploy <<EOF
           ECR_REGISTRY=$ECR_REGISTRY
           IMAGE_TAG=$IMAGE_TAG
@@ -328,19 +356,19 @@
           FITSYNC_MEDIA_PUBLIC_BASE_URL=${MEDIA_PUBLIC_BASE_URL}
           FREE_EXERCISE_DB_IMAGES_DIR=/opt/fitsync/free-exercise-db/exercises
           EOF
-          ssh -i ~/.ssh/id_ed25519 "ec2-user@$STAGING_HOST" "sudo mkdir -p /opt/fitsync/nginx && sudo chown -R ec2-user:ec2-user /opt/fitsync"
-          scp -i ~/.ssh/id_ed25519 docker-compose.prod.yml "ec2-user@$STAGING_HOST:/opt/fitsync/docker-compose.prod.yml"
-          scp -i ~/.ssh/id_ed25519 nginx/nginx.conf.template "ec2-user@$STAGING_HOST:/opt/fitsync/nginx/nginx.conf.template"
-          scp -i ~/.ssh/id_ed25519 scripts/ec2-deploy.sh "ec2-user@$STAGING_HOST:/opt/fitsync/ec2-deploy.sh"
-          scp -i ~/.ssh/id_ed25519 scripts/bootstrap-db.sql "ec2-user@$STAGING_HOST:/opt/fitsync/bootstrap-db.sql"
-          scp -i ~/.ssh/id_ed25519 .env.deploy "ec2-user@$STAGING_HOST:/opt/fitsync/.env"
+          ssh fitsync-host "sudo mkdir -p /opt/fitsync/nginx && sudo chown -R ec2-user:ec2-user /opt/fitsync"
+          scp docker-compose.prod.yml "fitsync-host:/opt/fitsync/docker-compose.prod.yml"
+          scp nginx/nginx.conf.template "fitsync-host:/opt/fitsync/nginx/nginx.conf.template"
+          scp scripts/ec2-deploy.sh "fitsync-host:/opt/fitsync/ec2-deploy.sh"
+          scp scripts/bootstrap-db.sql "fitsync-host:/opt/fitsync/bootstrap-db.sql"
+          scp .env.deploy "fitsync-host:/opt/fitsync/.env"
           # DB endpoint, service passwords and JWT key come from Secrets Manager on the host.
-          ssh -i ~/.ssh/id_ed25519 "ec2-user@$STAGING_HOST" \
+          ssh fitsync-host \
             "ECR_REGISTRY='$ECR_REGISTRY' IMAGE_TAG='$IMAGE_TAG' AWS_REGION='$AWS_REGION' APP_SECRET_ID='fitsync-staging/app' bash /opt/fitsync/ec2-deploy.sh"
           # The gateway port is open to the ALB only, so check it from the host itself.
-          ssh -i ~/.ssh/id_ed25519 "ec2-user@$STAGING_HOST" \
+          ssh fitsync-host \
             "curl -fsS -o /dev/null http://localhost:8088/actuator/health"
-          echo "Staging gateway healthy on $STAGING_HOST"
+          echo "Staging gateway healthy on $STAGING_INSTANCE_ID"
 
   # ── stress test against staging (manual via workflow_dispatch input) ────────
   stress-test:
@@ -410,7 +438,7 @@
   # commit's images, or a break-glass run (see the promotion rule at the top).
   deploy-prod:
     if: >-
-      ${{ !cancelled() && vars.AWS_ROLE_ARN != '' && vars.PROD_EC2_HOST != ''
+      ${{ !cancelled() && vars.AWS_ROLE_ARN != '' && vars.PROD_EC2_INSTANCE_ID != ''
       && github.event_name == 'workflow_dispatch'
       && needs.docker-image.result == 'success'
       && ((inputs.deploy_prod && needs.deploy-staging-app.result == 'success')
@@ -438,10 +466,17 @@
           role-to-assume: ${{ vars.AWS_ROLE_ARN }}
           aws-region: ${{ vars.AWS_REGION }}
       - uses: aws-actions/amazon-ecr-login@v2
+      - name: Install the Session Manager plugin
+        # ssh and scp reach the host through SSM Session Manager (port 22 is closed).
+        run: |
+          curl -fsSLo /tmp/session-manager-plugin.deb \
+            "https://s3.amazonaws.com/session-manager-downloads/plugin/latest/ubuntu_64bit/session-manager-plugin.deb"
+          sudo dpkg -i /tmp/session-manager-plugin.deb
+          session-manager-plugin --version
       - name: Render .env + deploy to prod via ec2-deploy.sh
         env:
           AWS_ACCOUNT_ID: ${{ vars.AWS_ACCOUNT_ID }}
-          PROD_EC2_HOST: ${{ vars.PROD_EC2_HOST }}
+          PROD_EC2_INSTANCE_ID: ${{ vars.PROD_EC2_INSTANCE_ID }}
           PROD_EC2_SSH_KEY: ${{ secrets.PROD_EC2_SSH_KEY }}
           COGNITO_ENABLED: ${{ vars.COGNITO_ENABLED }}
           PROD_DOMAIN: ${{ vars.PROD_DOMAIN }}
@@ -469,7 +504,28 @@
           # GitHub drops a secret's trailing newline; OpenSSH refuses a key without one.
           printf '%s\n' "$PROD_EC2_SSH_KEY" | tr -d '\r' > ~/.ssh/id_ed25519
           chmod 600 ~/.ssh/id_ed25519
-          ssh-keyscan -H "$PROD_EC2_HOST" >> ~/.ssh/known_hosts
+          # Port 22 is closed: ssh and scp tunnel through SSM Session Manager to the instance
+          # id, authenticated by this job's AWS role, and still log in with the deploy key.
+          # ssh-keyscan cannot use a ProxyCommand, so the host key is accepted on first use
+          # over that tunnel. A freshly built host needs a minute before its agent is online.
+          for i in $(seq 1 30); do
+            status="$(aws ssm describe-instance-information --filters "Key=InstanceIds,Values=$PROD_EC2_INSTANCE_ID" \
+              --query 'InstanceInformationList[0].PingStatus' --output text)"
+            [ "$status" = "Online" ] && break
+            [ "$i" = 30 ] && { echo "SSM agent on $PROD_EC2_INSTANCE_ID is not online ($status)" >&2; exit 1; }
+            sleep 10
+          done
+          cat > ~/.ssh/config <<SSHCFG
+          Host fitsync-host
+            HostName $PROD_EC2_INSTANCE_ID
+            User ec2-user
+            IdentityFile ~/.ssh/id_ed25519
+            IdentitiesOnly yes
+            StrictHostKeyChecking accept-new
+            ServerAliveInterval 30
+            ProxyCommand aws ssm start-session --region $AWS_REGION --target %h --document-name AWS-StartSSHSession --parameters portNumber=%p
+          SSHCFG
+          chmod 600 ~/.ssh/config
           cat > .env.deploy <<EOF
           ECR_REGISTRY=$ECR_REGISTRY
           IMAGE_TAG=$IMAGE_TAG
@@ -526,12 +582,12 @@
           FITSYNC_MEDIA_PUBLIC_BASE_URL=${FITSYNC_MEDIA_PUBLIC_BASE_URL:-https://fitsync-media.s3.ap-southeast-1.amazonaws.com}
           FREE_EXERCISE_DB_IMAGES_DIR=/opt/fitsync/free-exercise-db/exercises
           EOF
-          ssh -i ~/.ssh/id_ed25519 "ec2-user@$PROD_EC2_HOST" "sudo mkdir -p /opt/fitsync/nginx && sudo chown -R ec2-user:ec2-user /opt/fitsync"
-          scp -i ~/.ssh/id_ed25519 docker-compose.prod.yml "ec2-user@$PROD_EC2_HOST:/opt/fitsync/docker-compose.prod.yml"
-          scp -i ~/.ssh/id_ed25519 nginx/nginx.conf.template "ec2-user@$PROD_EC2_HOST:/opt/fitsync/nginx/nginx.conf.template"
-          scp -i ~/.ssh/id_ed25519 scripts/ec2-deploy.sh "ec2-user@$PROD_EC2_HOST:/opt/fitsync/ec2-deploy.sh"
-          scp -i ~/.ssh/id_ed25519 scripts/bootstrap-db.sql "ec2-user@$PROD_EC2_HOST:/opt/fitsync/bootstrap-db.sql"
-          scp -i ~/.ssh/id_ed25519 .env.deploy "ec2-user@$PROD_EC2_HOST:/opt/fitsync/.env"
+          ssh fitsync-host "sudo mkdir -p /opt/fitsync/nginx && sudo chown -R ec2-user:ec2-user /opt/fitsync"
+          scp docker-compose.prod.yml "fitsync-host:/opt/fitsync/docker-compose.prod.yml"
+          scp nginx/nginx.conf.template "fitsync-host:/opt/fitsync/nginx/nginx.conf.template"
+          scp scripts/ec2-deploy.sh "fitsync-host:/opt/fitsync/ec2-deploy.sh"
+          scp scripts/bootstrap-db.sql "fitsync-host:/opt/fitsync/bootstrap-db.sql"
+          scp .env.deploy "fitsync-host:/opt/fitsync/.env"
           # DB endpoint, service passwords and JWT key come from Secrets Manager on the host.
-          ssh -i ~/.ssh/id_ed25519 "ec2-user@$PROD_EC2_HOST" \
+          ssh fitsync-host \
             "ECR_REGISTRY='$ECR_REGISTRY' IMAGE_TAG='$IMAGE_TAG' AWS_REGION='$AWS_REGION' APP_SECRET_ID='fitsync-prod/app' bash /opt/fitsync/ec2-deploy.sh"
--- a/.github/CICD-VARIABLES.md
+++ b/.github/CICD-VARIABLES.md
@@ -18,7 +18,7 @@
 | `AWS_REGION` | all CD | e.g. `ap-southeast-1` |
 | `STAGING_ENABLED` | staging jobs, deploy-prod | `true` while staging exists. Anything else skips every staging job, so a run never re-creates a destroyed staging; prod then needs a run with `skip_staging` + reason |
 | `AWS_ACCOUNT_ID` | docker, deploy | for the ECR registry host |
-| `PROD_EC2_HOST` | deploy-prod | prod EC2 public IP (`ec2_public_ip` output, SSH only; traffic enters through the ALB); also gates deploy-prod |
+| `PROD_EC2_INSTANCE_ID` | deploy-prod | prod EC2 instance id (`ec2_instance_id` output of the prod workspace). CD reaches the host with ssh over SSM Session Manager (no SSH port open; traffic enters through the ALB); also gates deploy-prod. Replaces `PROD_EC2_HOST` |
 | `PROD_DOMAIN` | deploy-prod | nginx server name (optional) |
 | `COGNITO_ENABLED` | deploy-prod | `true`/`false` (optional) |
 | `FITSYNC_MEDIA_ENDPOINT` `FITSYNC_MEDIA_REGION` `FITSYNC_MEDIA_BUCKET` `FITSYNC_MEDIA_PUBLIC_BASE_URL` | deploy | S3 media config |
@@ -64,6 +64,8 @@
 # environment-level variables (email notifications differ per environment)
 gh variable set NOTIFY_EMAIL_SENDER --repo WittySalt/fitsync-backend --env staging    --body "noreply-staging@fitsyncapp.click"
 gh variable set NOTIFY_EMAIL_SENDER --repo WittySalt/fitsync-backend --env production --body "noreply@fitsyncapp.click"
+# the prod host for ssh over SSM (run in infra/terraform with the prod workspace selected)
+gh variable set PROD_EC2_INSTANCE_ID --repo WittySalt/fitsync-backend --body "$(terraform output -raw ec2_instance_id)"
 # secrets (prompts / reads file)
 gh secret   set PROD_EC2_SSH_KEY --repo WittySalt/fitsync-backend < path/to/id_ed25519
 ```
```

### Prod media bucket under Terraform

Prod serves exercise images and avatars from `fitsync-media`, created by hand in June 2026 and
holding 1749 objects (about 100 MB) on 2026-10-09. `media_prod.tf` adopts it with `import`
blocks that are empty unless `prod_media_bucket` is set, which only `prod.tfvars` does (it
replaces the old `existing_media_bucket` line). Staging keeps creating its own
`fitsync-staging-media-<account>` bucket in `storage.tf`, and `existing_media_bucket` still
works for a bucket that should only be used, not managed. Preconditions refuse
`prod_media_bucket` outside the prod workspace or together with `existing_media_bucket`.

The live settings, read with `aws s3api get-bucket-*` and `terraform plan -generate-config-out`,
and what the code does with each:

| Setting | Live value | In `media_prod.tf` |
|---|---|---|
| bucket policy | public `s3:GetObject` on `fitsync-media/*` (Sid `PublicReadObjects`) | imported as is |
| public access block | public ACLs blocked and ignored, public policies allowed | imported as is |
| default encryption | SSE-S3 (`AES256`), no bucket key | imported as is |
| object ownership | `BucketOwnerEnforced` (ACLs disabled) | imported as is |
| versioning | never enabled | not managed |
| lifecycle, CORS, website, logging | none | not managed |
| tags | none | ignored (the default tags would be the only change) |

The prod plan imports all five resources with no attribute change. `force_destroy` is never set
(it is ignored, so the import's empty value stays and reads as false), and the bucket, its
public access block and its policy carry `prevent_destroy`, so no plan can delete the bucket or
cut public read. The EC2 role's `s3:PutObject` grant (`storage.tf`) and the `media_bucket`
output keep the same bucket name. Never apply a plan that replaces or destroys
`aws_s3_bucket.media_prod`; to take the bucket out of Terraform again, use `terraform state rm`.

### Prod Cognito under Terraform

Prod signs in with the hand-built pool `ap-southeast-1_myVyuOmPD` ("User pool - 8lgadn", six
users on 2026-10-09). `cognito_prod.tf` adopts it, its only app client `FitSync`
(`4mvse0cgmkplgocfg827uolvad`) and its prefix domain `ap-southeast-1myvyuompd` with `import`
blocks. Import blocks cannot be conditional on the workspace, so they iterate (`for_each`,
Terraform 1.7 or later) over a set that is empty unless `prod_cognito` is set, and only
`prod.tfvars` sets it; staging imports nothing and keeps its own pool from `auth.tf`. Two
preconditions refuse `prod_cognito` outside the prod workspace or together with
`enable_cognito = true`. The pool also keeps `deletion_protection = "ACTIVE"` and all three
resources carry `prevent_destroy`, so neither a plan nor the console can delete them by accident.

The resource arguments were taken from `terraform plan -generate-config-out` against the live
pool, and the prod plan imports all three with no in-place change. The SPA (`auth.config.ts`,
`PROD_POOL`) and auth-service (compose default `AUTH_COGNITO_AUDIENCE`) both use the imported
client id, and the `cognito_*` outputs of the prod workspace now return the adopted pool, client
and domain.

Nothing is changed on the pool by this import, and these differences from the staging pool are
kept on purpose because changing them would change sign-in for real users:

- The client allows `ALLOW_ADMIN_USER_PASSWORD_AUTH`, `ALLOW_CUSTOM_AUTH` and `ALLOW_USER_AUTH`
  besides the three flows staging uses. The SPA needs only `USER_PASSWORD_AUTH` and refresh;
  removing the others is a candidate for a later, deliberate change.
- Account recovery also offers `verified_phone_number`; the pool has no phone sign-up, so it is
  unused.
- Callback and logout URLs are the localhost ones; the SPA signs in with `InitiateAuth`, not the
  hosted UI, so they are not on the sign-in path.
- The default tags (`Project`, `Env`, `ManagedBy`) are not written to the pool
  (`ignore_changes = [tags_all]`); tagging would be its only change. The client and domain take
  no tags.

Before the first prod apply, the plan must read "3 to import" with no change listed under the
three Cognito resources. After it, `terraform plan -var-file="prod.tfvars"` must show no change
for them, and signing in on the prod site must still work. Never apply a plan that replaces or
destroys the pool or its client (`prevent_destroy` makes such a plan fail anyway); fix the
arguments in `cognito_prod.tf` instead. If the pool must ever leave Terraform again, use
`terraform state rm`, which forgets it without touching it.

## Compliance controls: encrypted EBS, CloudTrail, retention, purge grants

These changes close infrastructure gaps listed in `Documents/architecture/compliance-gdpr-pdpa.md`
(rows 10, 12, 20, 21 and 24, and the Cognito and avatar parts of row 8).

| Change | Where | Live effect of the apply |
|---|---|---|
| Default EBS encryption for `ap-southeast-1` | `bootstrap/account_security.tf` (`aws_ebs_encryption_by_default`) | every new volume is encrypted with `aws/ebs`; existing volumes are untouched |
| Multi-region CloudTrail trail with log file validation | `bootstrap/account_security.tf` (`aws_cloudtrail.account`, bucket `fitsync-cloudtrail-<account>`) | management events land in a private SSE-S3 bucket that expires them after 365 days |
| Encrypted root volume on the app host | `compute.tf` (`root_block_device.encrypted = true`) | **replaces the EC2 instance** in each workspace, see the next section |
| `cognito-idp:AdminDeleteUser` on the environment's pool | `auth.tf` (`aws_iam_role_policy.ec2_cognito_purge`) | the account purge can delete the Cognito user; staging names its own pool, prod the adopted `ap-southeast-1_myVyuOmPD` |
| `s3:DeleteObject` and `s3:ListBucket` under `avatars/` | `storage.tf` (`data.aws_iam_policy_document.ec2_media`) | the purge can list `avatars/<userId>/` and delete the files there; exercise images stay undeletable for the host |
| HTTPS listener on the ALB | `origin_tls.tf`, `alb.tf`, `origin_hostname` in both tfvars | see "TLS from CloudFront to the ALB" |
| Log rotation and Kafka retention | `docker-compose.prod.yml` (`x-logging`, `KAFKA_LOG_RETENTION_HOURS`) | takes effect on the next deploy, see "Data retention" |

The plans of 2026-10-09 against the live state were:

| Stack | Plan | What it does |
|---|---|---|
| `bootstrap/` | 8 to add, 0 to change, 0 to destroy | default EBS encryption; the trail, its bucket, the bucket's public access block, ownership, encryption, lifecycle and policy |
| staging | 8 to add, 5 to change, 2 to destroy | replaces `aws_instance.app[0]` (`i-08b745d9535de0b43`) and `aws_lb_target_group_attachment.gateway[0]`; moves `aws_eip.app[0]` to the new instance in place; updates `fitsync-staging-ci-deploy` and `fitsync-staging-ssm-operator` (they name the instance ARN) and the media policy; adds the Cognito purge policy, the origin certificate, its validation record and validation, the `origin-staging` alias record and the HTTPS listener; widens the ALB ingress rule to ports 80 to 443 |
| prod | 8 to add, 6 to change, 2 to destroy | the same for prod (`i-0d0a9c1e503ce3d94`, `origin.fitsyncapp.click`), plus one change found on the live host: the app security group has an `SSH 22 from 0.0.0.0/0` rule that is not in the code, and the apply removes it |

Bootstrap is applied by hand. A CD run applies `staging.tfvars` as committed, so **the first CD
run with `STAGING_ENABLED=true` after this merges replaces the staging host by itself**;
`deploy-staging-app` then deploys onto the new host, because it reads the instance id from
`terraform output`. Run staging by hand first if you want to watch it.

### Rebuild the app host with an encrypted root volume

The encryption of a root volume cannot be changed in place, so `terraform apply` destroys the
running instance and creates a new one (the plan reads `aws_instance.app[0] must be replaced`
with `encrypted = false -> true # forces replacement`). The instance has no
`create_before_destroy`, so the old host goes first.

**What gets replaced, and what stays.**

| Resource | Effect |
|---|---|
| `aws_instance.app[0]` | destroyed, then created with a new instance id, the newest AL2023 image (today the same `ami-0595d03ac6eb6e3a7`) and an encrypted 30 GB gp3 root volume |
| `aws_lb_target_group_attachment.gateway[0]` | destroyed before the old host goes and created for the new instance id; the ALB re-registers the target and marks it healthy once the gateway answers `/actuator/health` |
| `aws_eip.app[0]` | updated in place: the same Elastic IP moves to the new instance, so the public address does not change |
| `aws_iam_policy.ci_deploy`, `aws_iam_policy.ssm_operator[0]` | updated in place to the new instance ARN |
| RDS, Secrets Manager, the ALB, its listeners, the security groups, the media bucket, Cognito | unchanged |

**First boot.** `user_data` stays in `ignore_changes`, which only silences updates: a replacement
is created with the script written in `compute.tf`, and the plan shows it. Prod's plan reads
`user_data = "c84902bc..." -> "6b184b92..."` because prod was first applied from a CRLF checkout;
the code now strips carriage returns, so every checkout produces the same LF script, whose hash
`6b184b92...` matches what CD applied to staging. On first boot the script installs Docker and
the compose plugin and creates `/opt/fitsync`. The deploy itself does not depend on it:
`scripts/ec2-deploy.sh` installs Docker, compose and the AWS CLI when they are missing, renders
`.env` from Secrets Manager and starts the stack. The service roles and schemas already exist in
RDS, and the deploy's database bootstrap is written to run again without harm.

**What is lost, and what re-syncs.** The root volume holds the Kafka log (`kafka-data`), the Redis
data (`redis-data`), container logs and the rendered `.env`. All of it is rebuilt or is not
needed:

- Kafka topics, consumer offsets and dead-letter topics (`<topic>.DLT`) are gone. The outbox
  tables (`outbox_events` in the `auth`, `user_domain`, `workout`, `nutrition` and `analytics`
  schemas) and the projections in PostgreSQL are the source of truth. An outbox row that is still
  `PENDING` or `FAILED` is published again by the relay on the new broker, and the consumers start
  from `earliest` on the empty topics. An event that was already `PUBLISHED` but not yet consumed
  is not re-sent, so drain the broker first (step 3 below).
- Redis holds caches, rate-limit counters and the revoked-session deny-list, all with a TTL. The
  caches refill from the database, and the counters start at zero. A device revoked in the last
  15 minutes keeps its access token until it expires, and its next refresh is refused by the
  database.
- `.env` is rendered again by the deploy. The host key changes, so ssh over SSM asks once to
  accept the new key (CD uses `accept-new`).

**Downtime.** The SPA stays up (S3 and CloudFront). `/api` answers 502 or 503 from the moment the
target deregisters (30 s delay) until the new host is deployed and healthy: about 2 minutes to
replace the instance and boot it, then 5 to 10 minutes for `ec2-deploy.sh` to pull the images and
start the eight JVMs. Plan for 15 minutes per environment.

**Order (staging first).** From PowerShell in `infra/terraform`, with an admin profile:

1. Bootstrap once: `cd bootstrap; terraform plan; terraform apply` (8 to add). New volumes are
   encrypted from here on, and the trail starts logging.
2. Staging plan: `terraform workspace select staging`, set `TF_VAR_ec2_public_key` from
   `fitsync-staging-deploy.pub` as in "Hardening switch-over", run
   `terraform plan -var-file="staging.tfvars"` and check it reads 8 to add, 5 to change,
   2 to destroy, with only the instance and the target group attachment replaced.
3. Drain the staging broker. On the host (`ssh fitsync-staging`), check that no consumer lags and
   that no dead-letter topic holds anything you need:
   ```bash
   docker exec fitsync-kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --all-groups | awk 'NR==1 || $6>0'
   docker exec fitsync-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list | grep '\.DLT$'
   ```
   The first command must print only its header.
4. Optional fallback: snapshot the old root volume so it can be read if something on it turns
   out to matter (`aws ec2 describe-instances --instance-ids <id> --query "Reservations[].Instances[].BlockDeviceMappings[].Ebs.VolumeId"`,
   then `aws ec2 create-snapshot --volume-id <vol> --description "fitsync-staging pre-encryption"`).
   The snapshot is unencrypted and holds personal data, so delete it within a week.
5. `terraform apply -var-file="staging.tfvars"`. Note the new id: `terraform output -raw ec2_instance_id`.
6. Run CD (Actions, CD, Run workflow) with staging enabled. `deploy-staging-app` waits for the SSM
   agent and deploys the same images. Check the site, sign in and log a meal, then on the host
   check that the outbox drains (`docker logs fitsync-user-service --since 10m | grep -i outbox`)
   and that `kafka-consumer-groups.sh --describe --all-groups` lists every consumer group again.
7. Update the operator files: `STAGING_INSTANCE_ID` in `scripts/demo-cloud.env`, `HostName` of
   `fitsync-staging` in `~/.ssh/config`, and the instance id in "SSH over SSM" above.
8. Prod, the same way: plan (8 to add, 6 to change, 2 to destroy), drain, optional snapshot,
   `terraform apply -var-file="prod.tfvars"`, then **set the `PROD_EC2_INSTANCE_ID` repository
   variable** before any CD run, because `deploy-prod` targets that id:
   `gh variable set PROD_EC2_INSTANCE_ID --repo WittySalt/fitsync-backend --body "$(terraform output -raw ec2_instance_id)"`.
   Run CD with `deploy_prod` ticked (after staging in the same run). Update `PROD_INSTANCE_ID` in
   `demo-cloud.env`, `fitsync-prod` in `~/.ssh/config` and "SSH over SSM".
9. Check both root volumes:
   `aws ec2 describe-volumes --filters Name=attachment.instance-id,Values=<new id> --query "Volumes[].Encrypted"`
   must print `true`.

**Rollback.** The old instance is terminated by the apply and cannot be brought back; the data
that matters lives in RDS, which is untouched. If the new host fails to deploy, fix the cause and
run CD again on the same image tag. If the encrypted host itself is the problem, revert
`encrypted = true` in `compute.tf` and apply again, which builds another, unencrypted host the same
way (another replacement and another new instance id). If something on the old volume is needed,
create a volume from the step 4 snapshot and attach it to the new host to read it.

### TLS from CloudFront to the ALB

Today CloudFront reaches the ALB over HTTP on port 80. With `origin_hostname` set, the workspace
gives the ALB a name in the `fitsyncapp.click` zone (bootstrap owns the zone, `origin_tls.tf` only
looks it up), requests an ACM certificate for that name in `ap-southeast-1` with DNS validation,
and adds an HTTPS listener on 443 with the `ELBSecurityPolicy-TLS13-1-2-2021-06` policy. ACM
public certificates are free and renew by themselves while the validation record stays.

| Workspace | `origin_hostname` |
|---|---|
| staging | `origin-staging.fitsyncapp.click` |
| prod | `origin.fitsyncapp.click` |

CloudFront validates the origin certificate against the name it sends. The `/api/*` behaviour
uses the managed origin request policy `Managed-AllViewerExceptHostHeader`
(`b689b0a8-53d0-40ab-baf2-68738e2966ac`; the comment in the frontend code calls it AllViewer), so
CloudFront sends the origin domain as Host and SNI, and the certificate for `origin...` matches.
The ALB's ingress rule now covers ports 80 to 443 in one rule: the CloudFront prefix list counts
as about 55 rules against the security group's limit of 60, so a second rule that references it
would not fit. The ALB answers only on its listener ports, 80 and 443.

**Order.**

1. Apply the workspace (part of the steps above). The certificate validates in a few minutes;
   `terraform output origin_hostname` prints the name.
2. Check that the name resolves to the ALB (`Resolve-DnsName origin.fitsyncapp.click`) and that
   the certificate is issued (`aws acm list-certificates --query "CertificateSummaryList[].[DomainName,Status]"`).
   A request from your machine times out, because the security group admits only CloudFront.
3. Change the frontend stack as below and apply it (CloudFront takes a few minutes to deploy).
   Check that `https://fitsyncapp.click/api/v1/auth/me` answers 401 JSON, not 502.
4. Close port 80: add `alb_http_listener_enabled = false` to the tfvars, plan (the HTTP listener
   is destroyed and the ingress rule becomes 443 only), apply, commit.

**Frontend change (`frontend/infra/terraform`, separate repository).** Add a variable and use it
for the API origin:

```hcl
# variables.tf
variable "api_origin_protocol_policy" {
  description = "How CloudFront reaches the /api/* origin: http-only (ALB DNS on :80) or https-only (the backend's origin_hostname on :443)"
  type        = string
  default     = "http-only"

  validation {
    condition     = contains(["http-only", "https-only"], var.api_origin_protocol_policy)
    error_message = "api_origin_protocol_policy must be http-only or https-only."
  }
}
```

```diff
# frontend.tf, dynamic "origin" for the API
       custom_origin_config {
         http_port              = var.api_origin_port
         https_port             = 443
-        origin_protocol_policy = "http-only"
+        origin_protocol_policy = var.api_origin_protocol_policy
         origin_ssl_protocols   = ["TLSv1.2"]
```

```diff
# prod.tfvars
-api_origin_domain = "fitsync-prod-alb-2084536414.ap-southeast-1.elb.amazonaws.com"
+api_origin_domain          = "origin.fitsyncapp.click"
+api_origin_protocol_policy = "https-only"

# staging.tfvars
-api_origin_domain = "fitsync-staging-alb-489104795.ap-southeast-1.elb.amazonaws.com"
+api_origin_domain          = "origin-staging.fitsyncapp.click"
+api_origin_protocol_policy = "https-only"
```

Also correct the comment on the `/api/*` behaviour's `origin_request_policy_id` to
`Managed-AllViewerExceptHostHeader`, and the `api_origin_domain` description ("the backend's
`origin_hostname` output, or `alb_dns_name` for HTTP"). The frontend CSP and the SPA's relative
`/api` paths do not change.

**Rollback.** Put the frontend tfvars back to the ALB DNS name with `http-only` and apply. The
HTTP listener stays until step 4, so the old path keeps working until then. After step 4, set
`alb_http_listener_enabled = true` again first.

## Data retention

| Data | Where | Kept for | Removed by |
|---|---|---|---|
| Application data (profile, body metrics, goals, meals, recipes, workouts, analytics, coach, notifications) | RDS PostgreSQL, one schema per service | the life of the account | the account purge, 30 days after a deletion request (auth-service grace period); each service deletes the user's rows when it consumes `AccountDeleted` |
| Soft-deleted rows (meals, recipes, cooked recipes, workout sessions, archived goals) | the same schemas, marked with `deleted_at` or `ARCHIVED` | until the account is purged; no per-row expiry yet | the account purge. Body-metric entries are hard-deleted at once |
| Cognito user | user pool of the environment | the life of the account | the purge, with `cognito-idp:AdminDeleteUser` (`auth.tf`) |
| Avatar images | media bucket, `avatars/<userId>/` | the life of the account; a replaced avatar's old file stays until then | the purge lists and deletes the prefix (`storage.tf` grants) |
| RDS automated backups | RDS | 7 days (`backup_retention_period = 7` in `data.tf`), point-in-time restore inside that window | RDS, on a rolling basis. A purged user survives in backups for up to 7 more days |
| Prod final snapshot | RDS snapshot `fitsync-prod-db-final` | until deleted by hand | only created when the prod database is destroyed |
| CloudTrail logs | `s3://fitsync-cloudtrail-<account>/AWSLogs/` | 365 days (`cloudtrail_retention_days`) | the bucket's lifecycle rule. The console's event history keeps 90 days on its own |
| Container logs | json-file logs on the app host's root volume | at most 5 files of 20 MB per container (`LOG_MAX_FILE`, `LOG_MAX_SIZE`), usually days | Docker rotation; a host rebuild drops them |
| Kafka topics | `kafka-data` volume on the app host | 7 days (`KAFKA_LOG_RETENTION_HOURS=168`, segments roll every 24 h) | Kafka's retention; a host rebuild drops them |
| Redis | `redis-data` volume, persistence off | minutes, every key has a TTL | expiry, eviction and restarts |

The compose settings apply on the next deploy. Docker applies `max-size` and `max-file` only when
a container is created, and `docker compose up` in `ec2-deploy.sh` recreates every container whose
settings changed, which the new logging block does for all of them. Nothing is needed on the host.

## Presentation talking points

1. **Two-layer IaC**: cloud resources (Terraform) + runtime orchestration
   (docker-compose + GitHub Actions) — both version-controlled and reproducible.
2. **Evidence**: `terraform apply` output + console screenshots + a clean
   `terraform destroy` proving full reproducibility.
3. **Multi-environment**: `staging` and `prod` are both built from code in their own
   workspaces; prod carries deletion protection and a final snapshot.
4. **Roadmap / concern**: a second app host behind the ALB needs Kafka and Redis moved
   off the host and a lock on the scheduled jobs and the outbox relay first. The database
   already sits in private subnets with no internet route, and the host has no SSH port
   (ssh over SSM); moving the host itself into private subnets would need a NAT gateway
   (about $45 a month) or VPC endpoints for ECR, S3, SSM and Bedrock.

## Staging bring-up (deploy→test→promote target)

Stand up the full staging app+DB so `stress-test` / `owasp-zap` can run against it,
then tear it down to stop the bill.

**Prerequisites**
1. Deploy key: see "Rebuild" step 2 (public half → `STAGING_EC2_PUBLIC_KEY` variable, private half → `STAGING_EC2_SSH_KEY` secret).
2. Variables `STAGING_ENABLED=true`, `STAGING_EC2_PUBLIC_KEY`, `STAGING_CORS_ALLOWED_ORIGINS` (plus `AWS_ROLE_ARN` / `AWS_ACCOUNT_ID` / `AWS_REGION`); secret `STAGING_EC2_SSH_KEY`. No database or JWT secret: they live in Secrets Manager.

**Order (jobs of the CD workflow on `main`, or local terraform):**
1. `tf-plan` → **`deploy-staging`** (terraform apply: VPC, ALB, EC2, RDS, Cognito, Secrets Manager).
2. **`deploy-staging-app`** (SSH-deploys the app; `ec2-deploy.sh` bootstraps the seven service roles from `fitsync-staging/app` before starting the stack, then the job checks the gateway health on the host).
3. Set `STRESS_TARGET_URL` / `DAST_TARGET_URL` = `https://staging.fitsyncapp.click` (+ `STRESS_USER_EMAIL/PASSWORD` of a seeded staging user), then run **`stress-test`** / **`owasp-zap`** (Actions → CD → **Run workflow** with the stress / DAST boxes ticked). The staging ALB accepts CloudFront only (`api_from_cloudfront_only`), so `http://<alb_dns_name>` times out from a runner. CloudFront forwards only `/api/*`; every other path, `/actuator/*` and `/v3/api-docs` included, answers with the SPA's `index.html` (HTTP 200, `text/html`). Actuator is VPC-internal only: the ALB health-checks the gateway on `/actuator/health` inside the VPC, and a person checks it from the host (`ssh fitsync-staging "curl -fsS http://localhost:8088/actuator/health"`), as `deploy-staging-app` does. The k6 script therefore proves the gateway with a JSON 401 from an API path and fails on any HTML answer (`stress/README.md`), and ZAP scans the SPA plus the API.
4. Frontend: point the staging SPA at the new pool, client and ALB (frontend PR).
5. **`terraform destroy -var-file=staging.tfvars`** when done.
