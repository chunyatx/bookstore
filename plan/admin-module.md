# Admin Module: Cash Accounts + Order Management

## Context

Add an admin module to the existing bookstore API. Admins need to manage customer wallet
balances (top-up, deduct, view transaction history) and control order lifecycle (update
status, refund). Customers pay from their wallet at checkout. A new `/admin` frontend
page provides the UI.

---

## New Files

| File | Purpose |
|------|---------|
| `src/controllers/admin.controller.ts` | All admin business logic |
| `src/routes/admin.routes.ts` | Admin API route definitions |
| `public/admin.html` | Standalone admin panel frontend |

## Modified Files

| File | Change |
|------|--------|
| `src/types/index.ts` | Add `Account`, `Transaction` interfaces + Zod schemas |
| `src/store/index.ts` | Add `accountsStore`, `transactionsStore`, `userTransactions` |
| `src/controllers/orders.controller.ts` | Deduct wallet on `placeOrder`; refund on `cancelOrder` |
| `src/seed.ts` | Create `Account` (balance: $0) for seeded admin user |
| `src/app.ts` | Mount `/api/admin` router |
| `public/index.html` | Add "Admin Panel" nav button (visible to admin role only) |

---

## New Types (`src/types/index.ts`)

```typescript
export interface Account {
  userId: string;
  balance: number;   // current wallet balance in USD
  updatedAt: Date;
}

export type TransactionType = "credit" | "debit" | "order_payment" | "refund";

export interface Transaction {
  id: string;
  userId: string;
  type: TransactionType;
  amount: number;        // always positive; type describes direction
  description: string;
  orderId?: string;      // set for order_payment and refund transactions
  balanceAfter: number;  // snapshot of balance after this transaction
  createdAt: Date;
}
```

New Zod validation schemas:

```typescript
export const AdjustBalanceSchema = z.object({
  amount: z.number().positive(),
  description: z.string().min(1),
});

export const UpdateOrderStatusSchema = z.object({
  status: z.enum(["confirmed", "shipped"]),  // admin can only advance forward
});
```

---

## New Store Maps (`src/store/index.ts`)

```typescript
export const accountsStore     = new Map<string, Account>();      // userId  → Account
export const transactionsStore = new Map<string, Transaction>();  // txId    → Transaction
export const userTransactions  = new Map<string, string[]>();     // userId  → txId[]
```

Two private helpers in `admin.controller.ts` (not exported):

```typescript
function getOrCreateAccount(userId: string): Account
// Returns existing account or initialises one with balance: 0

function recordTransaction(data: Omit<Transaction, "id" | "createdAt">): Transaction
// Assigns uuid, writes to transactionsStore and userTransactions index
```

---

## API Endpoints

### Admin routes (`/api/admin`)

All endpoints require `authenticate` + `requireRole("admin")`.

| Method | Path | Body / Query | Description |
|--------|------|-------------|-------------|
| `GET` | `/customers` | — | List all customers: id, name, email, balance, orderCount |
| `GET` | `/customers/:userId` | — | Customer detail: balance, last 50 transactions, orders |
| `POST` | `/customers/:userId/credit` | `{ amount, description }` | Add to wallet balance |
| `POST` | `/customers/:userId/debit` | `{ amount, description }` | Deduct from wallet (400 if insufficient funds) |
| `GET` | `/orders` | `?status=` | All orders, optional status filter, sorted newest first |
| `PATCH` | `/orders/:orderId/status` | `{ status: "confirmed"\|"shipped" }` | Advance order status |
| `POST` | `/orders/:orderId/refund` | — | Cancel order + credit wallet with order total |

### Customer-facing route (new)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/account` | Bearer | Own wallet balance + last 20 transactions |

### Checkout behaviour change (`orders.controller.ts → placeOrder`)

1. After stock validation, load customer's account via `getOrCreateAccount`
2. If `account.balance < totalAmount` → respond 400 "Insufficient wallet balance"
3. Deduct `totalAmount` from `account.balance`
4. Call `recordTransaction({ type: "order_payment", amount: totalAmount, orderId, ... })`
5. Save updated account to `accountsStore`

### Cancel behaviour change (`orders.controller.ts → cancelOrder`)

After setting `status = "cancelled"` and restoring stock:
1. Credit `order.totalAmount` back to customer's account
2. Call `recordTransaction({ type: "refund", amount: order.totalAmount, orderId, ... })`

---

## Admin Frontend (`public/admin.html`)

Standalone page. On load: reads `bs_token` and `bs_user` from `localStorage`.
Redirects to `/` if not logged in or role ≠ `"admin"`.

### Customers Tab

- Table columns: **Name**, **Email**, **Balance**, **Orders**, **Actions**
- Actions per row:
  - **Top Up** — opens modal (amount + note) → `POST /api/admin/customers/:id/credit`
  - **Deduct** — opens modal (amount + note) → `POST /api/admin/customers/:id/debit`
- Expandable row → shows transaction log and order list for that customer

### Orders Tab

- Status filter dropdown: All / Pending / Confirmed / Shipped / Cancelled
- Table columns: **Order ID** (short), **Customer**, **Date**, **Total**, **Items**, **Status**, **Actions**
- Status badges with colour coding (matches existing frontend palette)
- Actions per row:
  - **Confirm** — visible when `status === "pending"` → `PATCH .../status { status: "confirmed" }`
  - **Ship** — visible when `status === "confirmed"` → `PATCH .../status { status: "shipped" }`
  - **Refund** — visible when `status === "pending" | "confirmed"` → `POST .../refund`

### `public/index.html` change

Add an "Admin Panel" `<button>` in the navbar, rendered only when
`currentUser?.role === "admin"`, linking to `/admin`.

---

## Seed Changes (`src/seed.ts`)

On startup, after creating the admin user:
```typescript
accountsStore.set(admin.id, { userId: admin.id, balance: 0, updatedAt: new Date() });
```

---

## Verification Steps

1. `npm run build` — TypeScript compiles without errors
2. Start server → seed logs confirm account created
3. Open browser → login as admin → "Admin Panel" button appears in navbar
4. Navigate to `/admin` → Customers tab shows registered users with $0 balance
5. Top up a customer ($50) → balance updates in table
6. Login as that customer → add book to cart → checkout → wallet debited by order total
7. Return to admin panel → Orders tab → confirm order → status shows "Confirmed"
8. Admin → Refund that order → status "Cancelled", customer balance restored
9. `GET /api/account` (as customer) returns correct balance and 2 transactions
   (order_payment + refund)
