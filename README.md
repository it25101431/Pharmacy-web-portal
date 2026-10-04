# SmartCare Pharmacy Web Portal

SE2030 group project (Group 2026-Y2-S1-MLB-B11G2-08) - a Java web application for a retail pharmacy.

**Stack:** Java 17 · Spring Boot 3.3 (MVC, Data JPA, Security, Validation) · Thymeleaf · MySQL 8 · Bootstrap 5 (responsive) · Maven

## 1. Run it

1. Install JDK 17+, Maven 3.9+ and MySQL 8 (or use IntelliJ / Eclipse / VS Code with Maven support).
2. Set your MySQL password as the `DB_PASSWORD` environment variable (it is **not** stored in the code).
   In IntelliJ: *Run → Edit Configurations → SmartCareApplication → Environment variables* → `DB_PASSWORD=your_password`.
   (Use `DB_USERNAME` too if your MySQL user is not `root`.) The database `smartcare` is created automatically.
   (Alternatively run `database/schema.sql` yourself.)
   **Upgrading an existing database?** Run `database/migrate_add_refunded.sql` once (adds the `REFUNDED` payment status).
3. From this folder:
   ```
   mvn spring-boot:run
   ```
4. Open <http://localhost:8080>. Demo data (users + medicines) is created on first start.

| Role | Username | Password |
|------|----------|----------|
| Admin | `admin` | `Password@123` |
| Pharmacist | `pharmacist` | `Password@123` |
| Customer | `customer` | `Password@123` |
| Supplier | `supplier` | `Password@123` |
| Store Manager | `manager` | `Password@123` |
| Delivery Staff | `delivery` | `Password@123` |

Change these passwords before any real use. Uploaded prescriptions are stored in `./uploads`.

## 2. Features by persona (maps to the 24 user stories in the Scrum document)

| Persona | Features (URL prefix) |
|---|---|
| **Customer** `/customer` | Search/filter medicines, cart, checkout (card / mobile / COD - simulated payment), upload prescription (JPG/PNG/PDF), order tracking with status timeline + delivery OTP |
| **Pharmacist** `/pharmacist` | Medicine CRUD (soft delete) with optional product photo, receive stock (batch + expiry), prescription review queue (approve / reject with reason), pack orders, expiry & low-stock alerts |
| **Admin** `/admin` | Create/edit/deactivate staff accounts, approve/reject registrations, assign roles, assign delivery staff to packed orders, sales & inventory reports with date range + CSV export |
| **Supplier** `/supplier` | View purchase orders, mark In stock / Out of stock, confirm shipment delivery (adds stock), see payment status |
| **Delivery staff** `/delivery` | View assigned deliveries, update status (picked up / out for delivery), confirm with customer OTP, report issues (admin + customer notified) |
| **Store Manager** `/manager` | Sales dashboard (chart, KPIs), low-stock alerts with one-click purchase order, purchase-order payment status, supplier performance report; mobile-friendly UI |
| **Everyone** | Registration, login/logout, role-based access control, in-app notifications (`/notifications`) |

### Order lifecycle
`PLACED` (prescription pending) → `VERIFIED` → `PACKED` → `DISPATCHED` → `DELIVERED` (or `REJECTED` if the prescription is rejected - stock is restored, and a card/mobile payment is marked `REFUNDED`).
Orders without prescription-only items go straight to `VERIFIED`. Stock is deducted at checkout inside one transaction.

### Look and feel
Pharmacy-yellow and navy design system in `static/css/app.css`, using the **Atkinson Hyperlegible** typeface (bundled in `static/fonts`, SIL Open Font License) chosen for readability by elderly and low-vision customers. Medicines without an uploaded photo show an original illustration matched to their dosage form (`static/img/packs`). No external images are loaded, so the UI works offline.

## 3. Architecture

```
controller/  Spring MVC controllers, one per role (thin - HTTP only)
service/     Business rules: OrderService, SupplyService, NotificationService, CartService, FileStorageService
repository/  Spring Data JPA repositories
model/       JPA entities + enums (User, Medicine, Prescription, CustomerOrder, OrderItem, PurchaseOrder, Notification)
config/      Security (RBAC), UserDetailsService, global error/flash handling, demo-data seeding
templates/   Thymeleaf views (Bootstrap 5)
```

**Design patterns used (for the rubric):** Layered architecture / MVC, Repository (Spring Data), Service layer with transactions, Dependency Injection (constructor injection), Session-scoped cart (per-user Singleton-in-scope), Observer-style notifications via `NotificationService`. Be ready to point at the exact classes in your viva.

## 4. Database
Tables: `users`, `medicine`, `prescription`, `orders`, `order_item`, `purchase_orders`, `notification` (see `database/schema.sql`).
Integrity: foreign keys, `CHECK (stock >= 0)`, unique usernames, optimistic locking (`@Version`) on medicines to prevent overselling.
Note: your submitted ER diagram (Assignment 01 EER) is richer (Batch, Category, Payment, Delivery entities...). This implementation keeps the schema smaller; update either the diagram or the schema so they agree before you submit.

## 5. Security & ethics (for the Design Document)
- BCrypt password hashing; CSRF protection on every form; RBAC per URL prefix; only `ACTIVE` accounts can log in.
- No secrets in the repository: the database password is supplied through the `DB_PASSWORD` environment variable.
- Stock integrity: editing a medicine never changes its stock; stock only moves through checkout, "Receive stock" and supplier deliveries.
- Prescription files: random file names, type/size validation, and only the pharmacist role or the owning customer can open them.
- Input validation on all forms (Bean Validation + manual checks); business-rule errors shown as flash messages.
- Privacy: minimal personal data, no payment card data is collected or stored (payment is simulated).
- Accessibility: responsive layout, labelled inputs, simple flows for elderly users.

## 6. Known limitations / ideas
- Payment gateway is simulated (refunds are recorded as a status, not actually transferred); no email/SMS (notifications are in-app only); password-reset is not implemented.
- One prescription can currently be linked to several orders; add a "used" flag if you need one-time use.
- Add unit/integration tests (`spring-boot-starter-test` is already in the POM).
