# Pharmacist module - Nasik F.M. (IT25101431) - TEAM LEAD

**Branch:** `feature/pharmacist-inventory`

## Your files (6)
- `src/main/java/com/smartcare/controller/PharmacistController.java`
- `src/main/resources/templates/pharmacist/alerts.html`
- `src/main/resources/templates/pharmacist/medicine-form.html`
- `src/main/resources/templates/pharmacist/medicines.html`
- `src/main/resources/templates/pharmacist/orders.html`
- `src/main/resources/templates/pharmacist/prescriptions.html`

These files belong only to you, so merging into `main` never conflicts with teammates.

You also own the shared base (everything not listed above); unzip the whole zip as the first push to `main`.

## Push steps
```bash
git clone <REPO_URL>            # lead must have pushed the base to main first
cd smartcare
git checkout -b feature/pharmacist-inventory
# unzip this zip INTO the repo root (paths already match), choose "overwrite"
git add .
git commit -m "feat(pharmacist): add pharmacist module"
git push -u origin feature/pharmacist-inventory
```
Then open a Pull Request on GitHub: `feature/pharmacist-inventory` -> `main`, ask the lead to review and merge.

## Rules
- Only edit your own files. Need a change in a shared file (model, repository, config, OrderService, SupplyService, fragments.html, app.css)? Message the lead - one person changes it.
- `git pull origin main` before you start work each day.
- Commit small and often, with messages like `feat(...)`, `fix(...)`.
