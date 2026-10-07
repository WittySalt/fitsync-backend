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
| `AWS_ACCOUNT_ID` | docker, deploy | for the ECR registry host |
| `PROD_EC2_HOST` | deploy-prod | prod EC2 public host/IP; also gates deploy-prod |
| `PROD_DOMAIN` | deploy-prod | nginx server name (optional) |
| `COGNITO_ENABLED` | deploy-prod | `true`/`false` (optional) |
| `FITSYNC_MEDIA_ENDPOINT` `FITSYNC_MEDIA_REGION` `FITSYNC_MEDIA_BUCKET` `FITSYNC_MEDIA_PUBLIC_BASE_URL` | deploy | S3 media config |
| `RECOMMENDATION_AI_ENABLED` `RECOMMENDATION_AI_PROVIDER` `RECOMMENDATION_AI_MODEL` `RECOMMENDATION_AI_BASE_URL` | deploy | coach AI tier (optional). Unset = off, the coach falls back to rule text |
| `STRESS_TARGET_URL` `STRESS_PROFILE` | stress-test | k6 target + profile |
| `DAST_TARGET_URL` | owasp-zap | ZAP target base URL |
| `SONAR_HOST_URL` `SONAR_PROJECT_KEY` `SONAR_ORGANIZATION` `SONAR_QUALITYGATE_WAIT` | verify (optional) | only if using Sonar |

## Secrets (sensitive)

| Secret | Used by |
|---|---|
| `TF_VAR_DB_PASSWORD` | deploy-staging, bootstrap-staging-db (RDS master pw) |
| `STAGING_DB_PASSWORD` `STAGING_JWT_SECRET` `STAGING_EC2_SSH_KEY` | staging deploy |
| `DB_URL` `AUTH_DB_PASSWORD` `USER_DB_PASSWORD` `WORKOUT_DB_PASSWORD` `NUTRITION_DB_PASSWORD` `ANALYTICS_DB_PASSWORD` `RECOMMENDATION_DB_PASSWORD` `JWT_SECRET` | deploy-prod |
| `PROD_EC2_SSH_KEY` | deploy-prod |
| `FITSYNC_MEDIA_ACCESS_KEY` `FITSYNC_MEDIA_SECRET_KEY` | deploy |
| `ANTHROPIC_API_KEY` | deploy (only when `RECOMMENDATION_AI_PROVIDER=anthropic`) |
| `STRESS_USER_EMAIL` `STRESS_USER_PASSWORD` | stress-test |
| `SONAR_TOKEN` `NVD_API_KEY` | optional (CI) |

## Manual gates (Environments)

`deploy-staging`, `deploy-staging-app` and `deploy-prod` use the `staging` /
`production` Environments. To require a click-to-approve gate, add
**required reviewers** to those Environments under Settings → Environments.

## Quick set with `gh`

```bash
# variables
gh variable set AWS_REGION    --repo WittySalt/fitsync-backend --body "ap-southeast-1"
gh variable set AWS_ROLE_ARN  --repo WittySalt/fitsync-backend --body "arn:aws:iam::<acct>:role/<role>"
# secrets (prompts / reads file)
gh secret   set JWT_SECRET    --repo WittySalt/fitsync-backend
gh secret   set PROD_EC2_SSH_KEY --repo WittySalt/fitsync-backend < path/to/id_ed25519
```
