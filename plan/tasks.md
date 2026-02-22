# BookStore API — Task Tracker

Last updated: 2026-02-22

---

## Legend

| Symbol | Meaning |
|--------|---------|
| ✅ | Done |
| 🔄 | In progress |
| ⬜ | Pending |

---

## Completed

### ✅ Initial API
Core REST API with TypeScript + Express 5, in-memory Maps, JWT auth, and vanilla HTML frontend.

- Auth — register, login, JWT (7-day), `GET /api/auth/me`
- Books — full CRUD; admin-only write; search, filter, pagination
- Cart — add, update quantity, remove, clear; price snapshots
- Orders — place (atomic stock deduct), list, get, cancel (restores stock)
- Swagger UI at `/docs`
- Amazon-style frontend (`public/index.html`)

Commit: `f1b6d1f`

---

### ✅ Admin Module — Wallet & Order Management
> Plan: [`plan/admin-module.md`](admin-module.md)

**New files**
- `src/controllers/admin.controller.ts`
- `src/routes/admin.routes.ts`
- `src/routes/account.routes.ts`
- `public/admin.html`

**Modified files**
- `src/types/index.ts` — `Account`, `Transaction` interfaces + Zod schemas
- `src/store/index.ts` — `accountsStore`, `transactionsStore`, `userTransactions`
- `src/controllers/orders.controller.ts` — wallet deduct on `placeOrder`; refund on `cancelOrder`
- `src/seed.ts` — seed admin `Account` (balance: $0)
- `src/app.ts` — mount `/api/admin` and `/api/account` routers

**API endpoints added**
| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET`  | `/api/admin/customers` | Admin | List all customers with balance & order count |
| `GET`  | `/api/admin/customers/:id` | Admin | Customer detail: balance, last 50 tx, orders |
| `POST` | `/api/admin/customers/:id/credit` | Admin | Add to wallet |
| `POST` | `/api/admin/customers/:id/debit` | Admin | Deduct from wallet |
| `GET`  | `/api/admin/orders` | Admin | All orders, optional `?status=` filter |
| `PATCH`| `/api/admin/orders/:id/status` | Admin | Advance order status |
| `POST` | `/api/admin/orders/:id/refund` | Admin | Cancel + credit wallet |
| `GET`  | `/api/account` | Bearer | Own balance + last 20 transactions |

---

### ✅ Discount / Coupon Feature
> Plan: see below

**New files / concepts**
- `Coupon` model: `id`, `code` (uppercase unique), `type` (`percentage` | `fixed`), `value`, `description`, `minOrderAmount`, `maxUses`, `usedCount`, `isActive`, `expiresAt`
- `couponsStore` + `couponCodeIndex` maps in store
- Shared helpers: `computeDiscount()`, `validateCoupon()` (exported from admin controller)

**Modified files**
- `src/types/index.ts` — `Coupon`, `CouponType`; `Cart.couponCode`; `Order.subtotal`, `Order.discountAmount`, `Order.couponCode`; `CreateCouponSchema`, `ApplyCouponSchema`
- `src/store/index.ts` — `couponsStore`, `couponCodeIndex`
- `src/controllers/admin.controller.ts` — `createCoupon`, `listCoupons`, `deactivateCoupon`, `activateCoupon`, `computeDiscount`, `validateCoupon`
- `src/routes/admin.routes.ts` — coupon CRUD routes
- `src/controllers/cart.controller.ts` — `enrichCart` with subtotal/discount/finalTotal; `applyCoupon`, `removeCoupon`
- `src/routes/cart.routes.ts` — `POST /coupon`, `DELETE /coupon`
- `src/controllers/orders.controller.ts` — re-validate & apply coupon at checkout; increment `usedCount`
- `src/seed.ts` — 3 sample coupons: `WELCOME10`, `SAVE5`, `HALFOFF`
- `public/index.html` — coupon input in cart panel; discount line in totals; order history shows savings
- `public/admin.html` — 🏷️ Coupons tab: table + create modal + activate/deactivate

**API endpoints added**
| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET`  | `/api/admin/coupons` | Admin | List all coupons |
| `POST` | `/api/admin/coupons` | Admin | Create a coupon |
| `PATCH`| `/api/admin/coupons/:code/deactivate` | Admin | Deactivate a coupon |
| `PATCH`| `/api/admin/coupons/:code/activate` | Admin | Re-activate a coupon |
| `POST` | `/api/cart/coupon` | Bearer | Apply a coupon code to cart |
| `DELETE`| `/api/cart/coupon` | Bearer | Remove applied coupon from cart |

**Sample coupons (seeded on startup)**
| Code | Type | Value | Min Order | Max Uses |
|------|------|-------|-----------|----------|
| `WELCOME10` | percentage | 10% | — | unlimited |
| `SAVE5` | fixed | $5 | $20 | unlimited |
| `HALFOFF` | percentage | 50% | — | 10 |

---

## Pending

### ⬜ Swagger / OpenAPI Docs Update
Update `src/swagger.ts` to document all admin, account, and coupon endpoints added since the initial commit. Estimated: **0.5 MD**

### ⬜ Automated Test Suite
Set up Jest + Supertest. Cover: auth flows, books CRUD, cart operations, coupon apply/remove/checkout, wallet deduction, refund, admin routes. Estimated: **3–4 MD**

### ⬜ Database Integration
Replace in-memory Maps with a persistent database. PostgreSQL + Prisma is the recommended path — schema maps 1:1 to existing TypeScript interfaces. Estimated: **4–5 MD**

### ⬜ Security Hardening
- `helmet` — HTTP security headers
- `express-rate-limit` — brute-force protection on auth endpoints
- CORS configuration for production origins
Estimated: **1 MD**

### ⬜ Request Logging
- `morgan` for HTTP access logs
- `winston` for structured application-level logging
Estimated: **0.5 MD**

### ⬜ Docker / Deployment Setup
`Dockerfile`, `docker-compose.yml` (app + future DB), health-check wiring, `.env` documentation. Estimated: **1–2 MD**

### ⬜ User Profile Management
`PATCH /api/auth/me` — let users update their name, email, and password. Estimated: **1 MD**

### ⬜ Admin: User Role Promotion
`PATCH /api/admin/customers/:id/role` — elevate a customer to admin. Estimated: **0.5 MD**

### ⬜ Pagination on Orders
Add `page` / `limit` query params to `GET /api/orders`. Estimated: **0.5 MD**

### ⬜ Password Reset Flow
Forgot-password → email token → reset endpoint. Requires an email transport (nodemailer/SendGrid). Estimated: **1–2 MD**

### ⬜ Refresh Token / Logout
Token revocation store + `POST /api/auth/logout` blacklist. Estimated: **1–2 MD**

### ⬜ Book Reviews & Ratings
Customers who have purchased a book can leave a rating + review; average rating shown on book. Estimated: **2–3 MD**

### ⬜ Wishlist
`POST /api/wishlist/items`, `GET /api/wishlist` — saved-for-later list. Estimated: **1–2 MD**

### ⬜ Email Notifications
Order confirmation, shipping update, refund notification via nodemailer / SendGrid. Estimated: **2–3 MD**

### ⬜ Book Cover Images
Upload/link cover art; serve via Express static or S3 presigned URLs. Estimated: **1–2 MD**

### ⬜ Low-Stock Inventory Alerts
Admin dashboard badge or email when a book's stock drops below a configurable threshold. Estimated: **1 MD**

---

## Effort Summary

| Category | Tasks | Est. MD |
|----------|-------|---------|
| ✅ Done | Core API, Admin module, Coupons | — |
| Docs | Swagger update | 0.5 |
| Quality | Tests | 3–4 |
| Infrastructure | DB, Security, Logging, Docker | 7–9 |
| Features | Profile, Reviews, Wishlist, Coupons++, Emails, Images, Alerts | 11–16 |
| **Total remaining** | | **~22–30 MD** |
