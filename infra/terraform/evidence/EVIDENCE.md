# IaC Evidence — Terraform `staging` environment

**Date:** 2026-06-25 · **AWS account:** 437659978621 · **Region:** ap-southeast-1
**Tooling:** Terraform v1.15.7 · **Workspace:** `staging`
**Command:** `terraform apply -var-file=staging.tfvars`

> Proof that the FitSync `staging` environment is provisioned entirely from code,
> isolated from the hand-built production environment (all names `fitsync-staging-*`).

## 1. Apply result

```
Apply complete! Resources: 36 added, 0 changed, 0 destroyed.
```

Full log: [`01-apply.log`](01-apply.log)

## 2. Terraform outputs

| Output | Value |
|--------|-------|
| VPC | `vpc-0c54e2698b3c53f8d` |
| Public subnets | `subnet-00434d7ed3d315335`, `subnet-0016c9fc3bfd5bc91` |
| Cognito user pool | `ap-southeast-1_WDFo3z7HN` |
| Cognito SPA client | `95497c0ppmc6i5qv5npklnm6p` |
| Cognito domain | `fitsync-staging-437659978621.auth.ap-southeast-1.amazoncognito.com` |
| Media bucket | `fitsync-staging-media-437659978621` |
| Frontend bucket | `fitsync-staging-frontend-437659978621` |
| CI deploy policy | `arn:aws:iam::437659978621:policy/fitsync-staging-ci-deploy` |
| ECR (×6) | `437659978621.dkr.ecr.ap-southeast-1.amazonaws.com/fitsync-staging-<service>` |

Full output: [`02-output.log`](02-output.log)

## 3. Independent AWS API verification

Queried with the AWS CLI — confirms the resources Terraform reported actually exist:

```
ECR repos : fitsync-staging-{api-gateway,auth,user,workout,nutrition,analytics}-service
S3 buckets: fitsync-staging-frontend-437659978621, fitsync-staging-media-437659978621
Cognito   : fitsync-staging-users  (ap-southeast-1_WDFo3z7HN)
VPC       : fitsync-staging-vpc    (vpc-0c54e2698b3c53f8d)
```

## 4. Reproducibility

The environment is fully disposable and re-creatable from code:

```bash
terraform destroy -var-file=staging.tfvars   # tear down ($0 footprint)
terraform apply   -var-file=staging.tfvars   # re-create, identical
```

This `apply → destroy → apply` property is the core IaC guarantee.

## 5. Resource inventory (36)

- **Network:** 1 VPC, 2 subnets, 1 IGW, 1 route table, 2 associations, 2 security groups
- **Registry:** 6 ECR repositories + 6 lifecycle policies
- **Storage:** 2 S3 buckets + public-access blocks + bucket policies + website config
- **Auth:** Cognito user pool + SPA client + hosted domain
- **IAM:** EC2 instance role + policy attachments + instance profile + CI deploy policy

Paid/compute resources (RDS, EC2, CloudFront) are gated OFF behind feature flags
and reserved for the post-defense cut-over (see [`../RUNBOOK.md`](../RUNBOOK.md)).
