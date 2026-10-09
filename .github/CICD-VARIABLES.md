# GitHub Actions — CI/CD variables & secrets

- **CI** (`ci.yml`, `dependency-check.yml`) needs **no** variables or secrets — it is
  green out of the box. SonarCloud is optional (set `SONAR_TOKEN` to enable).
- **CD** (`cd.yml`) is **dormant** until the `AWS_ROLE_ARN` *repository variable* is
  set. While it is empty, every CD job is skipped. AWS auth uses **GitHub OIDC**
  (no static access keys) — the IAM role must trust `token.actions.githubusercontent.com`
  with subject `repo:WittySalt/fitsync-backend:*`.

Set values under **Settings → Secrets and variables → Actions**, or with the `gh`
commands below.

## Repository variables (non-sensitive)

| Variable | Used by | Notes |
|---|---|---|
| `AWS_ROLE_ARN` | all CD | **Master gate.** IAM role ARN with the GitHub OIDC trust. Leave empty to keep CD dormant. |
| `AWS_REGION` | all CD | e.g. `ap-southeast-1` |
| `STAGING_ENABLED` | staging jobs, deploy-prod | `true` while staging exists. Anything else skips every staging job, so a run never re-creates a destroyed staging; prod then needs a run with `skip_staging` + reason |
| `AWS_ACCOUNT_ID` | docker, deploy | for the ECR registry host |
| `PROD_EC2_INSTANCE_ID` | deploy-prod | prod EC2 instance id (`ec2_instance_id` output of the prod workspace). CD reaches the host with ssh over SSM Session Manager (no SSH port open; traffic enters through the ALB); also gates deploy-prod. Replaces `PROD_EC2_HOST` |
| `PROD_DOMAIN` | deploy-prod | nginx server name (optional) |
| `COGNITO_ENABLED` | deploy-prod | `true`/`false` (optional) |
| `FITSYNC_MEDIA_ENDPOINT` `FITSYNC_MEDIA_REGION` `FITSYNC_MEDIA_BUCKET` `FITSYNC_MEDIA_PUBLIC_BASE_URL` | deploy | S3 media config |
| `RECOMMENDATION_AI_ENABLED` `RECOMMENDATION_AI_PROVIDER` `RECOMMENDATION_AI_MODEL` `RECOMMENDATION_AI_BASE_URL` | deploy | coach AI tier (optional). Unset = off, the coach falls back to rule text |
| `NOTIFY_EMAIL_ENABLED` | deploy-staging-app, deploy-prod | **Environment-level** (`staging` / `production`). `true` makes notification-service send through SES; unset or anything else = emails are only logged. Turn on only after the domain identity verifies and `notify_email_sender` is applied in that workspace |
| `NOTIFY_EMAIL_SENDER` | deploy-staging-app, deploy-prod | **Environment-level.** The bare From address: `noreply-staging@fitsyncapp.click` on `staging`, `noreply@fitsyncapp.click` on `production`. Must equal `notify_email_sender` in that workspace's tfvars, because the EC2 role may send only from that address (`ses:FromAddress`). CD adds the display name (`FitSync Staging` / `FitSync`) and staging's `[Staging]` subject prefix itself |
| `APP_BASE_URL` | deploy-staging-app, deploy-prod | **Environment-level**, optional. Where links in emails point. Unset: staging uses the first `https://` origin of `STAGING_CORS_ALLOWED_ORIGINS`, else `https://d1z92tpb353lfi.cloudfront.net`; prod uses `https://d26vi6hikexzev.cloudfront.net` |
| `STAGING_EC2_PUBLIC_KEY` | tf-plan, deploy-staging | public half of the staging deploy key (the private half is `STAGING_EC2_SSH_KEY`) |
| `STAGING_CORS_ALLOWED_ORIGINS` | deploy-staging-app | the staging SPA origin, e.g. `https://<staging-cloudfront>.cloudfront.net` |
| `STRESS_TARGET_URL` `STRESS_PROFILE` | stress-test | k6 target + profile |
| `DAST_TARGET_URL` | owasp-zap | ZAP target base URL |
| `SONAR_HOST_URL` `SONAR_PROJECT_KEY` `SONAR_ORGANIZATION` `SONAR_QUALITYGATE_WAIT` | verify (optional) | only if using Sonar |

## Secrets (sensitive)

| Secret | Used by |
|---|---|
| `STAGING_EC2_SSH_KEY` | deploy-staging-app |
| `PROD_EC2_SSH_KEY` | deploy-prod |
| `ANTHROPIC_API_KEY` | deploy (only when `RECOMMENDATION_AI_PROVIDER=anthropic`) |
| `STRESS_USER_EMAIL` `STRESS_USER_PASSWORD` | stress-test |
| `SONAR_TOKEN` `NVD_API_KEY` | optional (CI) |

No database endpoint, database password or JWT key is a GitHub secret. Terraform
generates them into Secrets Manager (`fitsync-<env>/app`, `secrets.tf`), RDS manages
its own master password, and `scripts/ec2-deploy.sh` reads both with the EC2 instance
role at deploy time.

## Manual gates (Environments)

`deploy-staging`, `deploy-staging-app` and `deploy-prod` use the `staging` /
`production` Environments, which also scope the OIDC trust of the CD role.
GitHub offers required reviewers on Environments only for public repositories or paid
plans, so this private repository gets its prod gate from the workflow instead: a push
never deploys prod, a person has to start a manual run. See the promotion rule at the top of `cd.yml`.

## Quick set with `gh`

```bash
# variables
gh variable set AWS_REGION    --repo WittySalt/fitsync-backend --body "ap-southeast-1"
gh variable set AWS_ROLE_ARN  --repo WittySalt/fitsync-backend --body "arn:aws:iam::<acct>:role/<role>"
# environment-level variables (email notifications differ per environment)
gh variable set NOTIFY_EMAIL_SENDER --repo WittySalt/fitsync-backend --env staging    --body "noreply-staging@fitsyncapp.click"
gh variable set NOTIFY_EMAIL_SENDER --repo WittySalt/fitsync-backend --env production --body "noreply@fitsyncapp.click"
# the prod host for ssh over SSM (run in infra/terraform with the prod workspace selected)
gh variable set PROD_EC2_INSTANCE_ID --repo WittySalt/fitsync-backend --body "$(terraform output -raw ec2_instance_id)"
# secrets (prompts / reads file)
gh secret   set PROD_EC2_SSH_KEY --repo WittySalt/fitsync-backend < path/to/id_ed25519
```
