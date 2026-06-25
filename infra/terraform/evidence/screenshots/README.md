# IaC screenshots — archive

Visual evidence for the "Infrastructure-as-Code" slide. Captured 2026-06-25 against
AWS account 437659978621 (ap-southeast-1), `staging` Terraform environment.

Drop the image files here with these names:

| File | What it shows | Key proof |
|------|---------------|-----------|
| `01-ecr-staging.png` | ECR → Private repositories, filtered `fitsync-staging` | 6 repos `fitsync-staging-{api-gateway,auth,user,workout,nutrition,analytics}-service`, created `Jun 25 2026 20:51:41`; total "(12)" = 6 staging + 6 existing prod |
| `02-s3-staging.png` | S3 → Buckets | `fitsync-staging-frontend-437659978621` + `fitsync-staging-media-437659978621` (Jun 25 20:51:42) next to existing `fitsync-frontend-prod` (Jun 20) / `fitsync-media` (Jun 21) |
| `03-cognito-staging.png` | Cognito → User pools | `fitsync-staging-users` (`ap-southeast-1_WDFo3z7HN`, "9 minutes ago") next to existing `ap-southeast-1_myVyuOmPD` ("2 weeks ago") |
| `04-terraform-apply.png` | PowerShell terminal | `terraform apply` → creation complete + outputs (ECR/S3/Cognito URLs) |
| `05-terraform-destroy-start.png` | PowerShell terminal | `terraform destroy -var-file="staging.tfvars"` refreshing state |
| `06-terraform-destroy-plan.png` | PowerShell terminal | `Plan: 0 to add, 0 to change, 36 to destroy` + `Enter a value: yes` |
| `07-terraform-destroy-complete.png` | PowerShell terminal | `Destroy complete! Resources: 36 destroyed` |

> **04–07 archived ✅** (terminal). **01–03 (AWS console)** still to add — save the
> inline ECR/S3/Cognito shots from chat into this folder if you want them.

## How to populate

- **01–03 (AWS console):** the browser-captured shots are shown inline in the
  Claude chat — right-click → save into this folder with the names above.
- **04–05 (terminal):** take them yourself while running the commands; Windows
  saves Win+Shift+S / PrintScreen shots to `C:\Users\WRX\Pictures\Screenshots\`.
  Tell Claude the filenames and it can copy them in here.

The text-equivalent proof (resource IDs, timestamps, apply log) is in
[`../EVIDENCE.md`](../EVIDENCE.md) and `../01-apply.log` / `../02-output.log`.
