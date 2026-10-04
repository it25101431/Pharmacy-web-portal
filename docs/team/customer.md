# Customer module - Sachintha H.B.S. (IT25102216)

**Branch:** `feature/customer-ordering`

## Your files (7)
- `src/main/java/com/smartcare/controller/CustomerController.java`
- `src/main/java/com/smartcare/service/CartService.java`
- `src/main/resources/templates/customer/cart.html`
- `src/main/resources/templates/customer/medicines.html`
- `src/main/resources/templates/customer/order-detail.html`
- `src/main/resources/templates/customer/orders.html`
- `src/main/resources/templates/customer/prescriptions.html`

These files belong only to you, so merging into `main` never conflicts with teammates.

## Push steps
```bash
git clone <REPO_URL>            # lead must have pushed the base to main first
cd smartcare
git checkout -b feature/customer-ordering
# unzip this zip INTO the repo root (paths already match), choose "overwrite"
git add .
git commit -m "feat(customer): add customer module"
git push -u origin feature/customer-ordering
```
Then open a Pull Request on GitHub: `feature/customer-ordering` -> `main`, ask the lead to review and merge.

## Rules
- Only edit your own files. Need a change in a shared file (model, repository, config, OrderService, SupplyService, fragments.html, app.css)? Message the lead - one person changes it.
- `git pull origin main` before you start work each day.
- Commit small and often, with messages like `feat(...)`, `fix(...)`.
