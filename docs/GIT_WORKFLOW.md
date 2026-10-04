# Git workflow for the SmartCare group (6 members)

## Order of work
1. **Team lead (Nasik)** creates the GitHub repo, unzips `01-nasik-lead-base-pharmacist.zip`, and pushes it to `main`. This holds the shared base (pom, config, models, repositories, shared services, shared templates, DB scripts) plus the Pharmacist module.
2. Add the 5 members as collaborators. Turn on branch protection for `main` (require a Pull Request).
3. Each member clones, creates their own branch, unzips their zip into the repo root, commits, pushes, and opens a PR.
4. Lead reviews and merges the 5 PRs (any order - files never overlap).
5. After all merges everyone runs `git pull origin main`, then `mvn spring-boot:run` to check the whole app.

## Lead commands (first push)
```bash
unzip 01-nasik-lead-base-pharmacist.zip -d smartcare && cd smartcare
git init -b main
git add .
git commit -m "chore: initial shared base + pharmacist module"
git remote add origin <REPO_URL>
git push -u origin main
```

## Ownership
| Member | Module | Branch |
|---|---|---|
| Nasik (lead) | Pharmacist + shared base | main (first push) |
| Croos | Admin, auth (login/register), user deletion | feature/admin-user-management |
| Sachintha | Customer ordering, cart, prescriptions | feature/customer-ordering |
| Abdullah | Supplier offers / purchase orders | feature/supplier-management |
| Galketiwala | Delivery + delivery notes | feature/delivery-management |
| Theekshana | Store manager dashboard / reports | feature/store-manager-reports |

## Shared files (lead only)
`pom.xml`, `config/*`, `model/*`, `repository/*`, `OrderService`, `SupplyService`, `NotificationService`, `FileStorageService`, `FileController`, `NotificationController`, `fragments.html`, `error.html`, `notifications.html`, `static/*`, `database/*`, tests for OrderService.

## Note
Until all branches are merged the app is incomplete (e.g. without the Admin branch there is no login page). Run the full app only after merging.
