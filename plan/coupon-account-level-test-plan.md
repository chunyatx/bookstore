# Test Plan — Coupon Account-Level Restriction

**Feature:** Coupons can be restricted to accounts assigned a specific account level.
A coupon with `accountLevel = "A00001"` may only be applied by users whose account
has been assigned level `"A00001"` by an admin.

**Date:** 2026-03-10

---

## Scope

| Layer | File(s) under test |
|---|---|
| Unit — helper | `CouponHelperTest` |
| Unit — service | `CartServiceTest`, `OrderServiceTest`, `AdminServiceTest` |
| Integration | `CouponIntegrationTest` (new suite) |

---

## 1. CouponHelper Unit Tests

### 1.1 Level-restricted coupon — matching level → valid
- Setup: coupon with `accountLevel = "A00001"`, account level `"A00001"`
- Call: `validateCoupon(code, subtotal, userRegisteredAt, "A00001")`
- Expect: coupon returned, no exception

### 1.2 Level-restricted coupon — wrong level → throws
- Setup: coupon with `accountLevel = "A00001"`, account level `"B00001"`
- Call: `validateCoupon(code, subtotal, userRegisteredAt, "B00001")`
- Expect: `IllegalArgumentException` with message containing `"A00001"`

### 1.3 Level-restricted coupon — null account level → throws
- Setup: coupon with `accountLevel = "A00001"`, no account found (level = null)
- Call: `validateCoupon(code, subtotal, userRegisteredAt, null)`
- Expect: `IllegalArgumentException`

### 1.4 Level-restricted coupon — case-insensitive match → valid
- Setup: coupon with `accountLevel = "gold"`, account level `"GOLD"`
- Call: `validateCoupon(code, subtotal, userRegisteredAt, "GOLD")`
- Expect: coupon returned (case-insensitive comparison)

### 1.5 No level restriction — any account level → valid
- Setup: coupon with `accountLevel = null`
- Call: `validateCoupon(code, subtotal, userRegisteredAt, "anything")` and with `null`
- Expect: coupon returned in both cases

### 1.6 tryValidateCoupon — wrong level → returns null (silent)
- Setup: coupon with `accountLevel = "A00001"`, account level `"X99"`
- Call: `tryValidateCoupon(code, subtotal, userRegisteredAt, "X99")`
- Expect: `null` returned (no exception)

### 1.7 tryValidateCoupon — matching level → returns coupon
- Setup: coupon with `accountLevel = "A00001"`, account level `"A00001"`
- Call: `tryValidateCoupon(code, subtotal, userRegisteredAt, "A00001")`
- Expect: coupon returned

### 1.8 Level restriction combined with other constraints — all pass → valid
- Setup: coupon with `accountLevel = "VIP"`, `minOrderAmount = 50`, not expired
- Account level `"VIP"`, subtotal = 100
- Expect: coupon returned

### 1.9 Level restriction combined with other constraints — level fails but min order also fails
- Setup: coupon with `accountLevel = "VIP"`, `minOrderAmount = 50`
- Account level `"BASIC"`, subtotal = 10
- Expect: `IllegalArgumentException` (first failing check wins — order of checks matters)

---

## 2. CartService Unit Tests

### 2.1 applyCoupon — account has matching level → coupon applied
- Setup: user, account with `level = "A00001"`, coupon restricted to `"A00001"`, valid subtotal
- Call: `applyCoupon(userId, req)`
- Expect: cart saved with `couponCode` set, enriched response shows discount

### 2.2 applyCoupon — account level mismatch → 400 BAD_REQUEST
- Setup: user, account with `level = "B00001"`, coupon restricted to `"A00001"`
- Call: `applyCoupon(userId, req)`
- Expect: `ResponseStatusException` with status 400, message about account level

### 2.3 applyCoupon — account has no level, coupon restricted → 400 BAD_REQUEST
- Setup: user, account with `level = null`, coupon restricted to `"A00001"`
- Call: `applyCoupon(userId, req)`
- Expect: 400 error

### 2.4 applyCoupon — coupon unrestricted, account has a level → applied
- Setup: user, account with `level = "VIP"`, coupon with `accountLevel = null`
- Call: `applyCoupon(userId, req)`
- Expect: coupon applied successfully

### 2.5 enrichCart — level-restricted coupon becomes invalid after level removed → silently cleared
- Setup: cart with `couponCode` for level-restricted coupon; account level changed to non-matching
- Call: `enrichCart(cart)`
- Expect: coupon silently cleared, `couponCode = null`, `discountAmount = 0`

---

## 3. OrderService Unit Tests

### 3.1 placeOrder — level-restricted coupon, account has matching level → discount applied
- Setup: user, account with `level = "A00001"`, cart with valid coupon restricted to `"A00001"`
- Call: `placeOrder(userId)`
- Expect: order created with `discountAmount > 0`, `couponCode` set, `usedCount` incremented

### 3.2 placeOrder — level-restricted coupon, account level mismatch → coupon silently ignored
- Setup: user, account with `level = "B00001"`, cart with coupon restricted to `"A00001"`
- Call: `placeOrder(userId)`
- Expect: order created with `discountAmount = 0`, `couponCode = null`, full price charged

### 3.3 placeOrder — no coupon, account has a level → order unaffected
- Setup: user, account with `level = "A00001"`, cart with no coupon
- Call: `placeOrder(userId)`
- Expect: order created normally, no discount

---

## 4. AdminService Unit Tests

### 4.1 createCoupon — with accountLevel → persisted
- Setup: `CreateCouponRequest` with `accountLevel = "A00001"`
- Call: `createCoupon(req)`
- Expect: saved coupon has `getAccountLevel() == "A00001"`

### 4.2 createCoupon — without accountLevel (null) → no restriction
- Setup: `CreateCouponRequest` with `accountLevel = null`
- Call: `createCoupon(req)`
- Expect: saved coupon has `getAccountLevel() == null`

### 4.3 setCustomerLevel — valid userId → account level updated
- Setup: existing user and account with `level = null`
- Call: `setCustomerLevel(userId, "A00001")`
- Expect: account `level` field set to `"A00001"`, response map contains `userId` and `level`

### 4.4 setCustomerLevel — unknown userId → 404
- Setup: userId not in repository
- Call: `setCustomerLevel(unknownId, "A00001")`
- Expect: `ResponseStatusException` with status 404

### 4.5 setCustomerLevel — blank level → clears level (null)
- Setup: account with existing `level = "A00001"`
- Call: `setCustomerLevel(userId, "")` or `setCustomerLevel(userId, null)`
- Expect: account `level` set to `null`

### 4.6 listCustomers — includes accountLevel in response map
- Setup: two customers, one with level `"A00001"`, one with `null`
- Call: `listCustomers()`
- Expect: response maps include `"accountLevel"` key with correct values

---

## 5. Integration Tests

### 5.1 POST /api/admin/coupons — create level-restricted coupon
- Auth: admin JWT
- Body: `{ code: "NEWIT", type: "fixed", value: 5, description: "...", accountLevel: "A00001" }`
- Expect: 201, response body includes `accountLevel: "A00001"`

### 5.2 POST /api/cart/coupon — user with matching level applies restricted coupon → 200
- Setup: admin creates coupon `NEWIT` restricted to `"A00001"`, admin sets user's level to `"A00001"`, user adds item to cart
- Auth: user JWT
- Body: `{ code: "NEWIT" }`
- Expect: 200, response includes `couponCode: "NEWIT"`, `discountAmount > 0`

### 5.3 POST /api/cart/coupon — user with wrong level → 400
- Setup: coupon restricted to `"A00001"`, user's account level is `"B00001"`
- Auth: user JWT
- Body: `{ code: "NEWIT" }`
- Expect: 400, error message references account level restriction

### 5.4 POST /api/cart/coupon — user with no level → 400
- Setup: coupon restricted to `"A00001"`, user has no account level assigned
- Expect: 400

### 5.5 POST /api/cart/coupon — unrestricted coupon, user has a level → 200
- Setup: coupon with no `accountLevel`, user with any level
- Expect: 200, coupon applied

### 5.6 PATCH /api/admin/customers/:id/level — set level → 200
- Auth: admin JWT
- Body: `{ level: "A00001" }`
- Expect: 200, response `{ userId, level: "A00001" }`

### 5.7 PATCH /api/admin/customers/:id/level — clear level (blank) → 200
- Auth: admin JWT
- Body: `{ level: "" }`
- Expect: 200, subsequent GET /api/admin/customers shows `accountLevel: null`

### 5.8 PATCH /api/admin/customers/:id/level — unknown user → 404
- Auth: admin JWT
- Expect: 404

### 5.9 PATCH /api/admin/customers/:id/level — customer auth (non-admin) → 403
- Auth: customer JWT
- Expect: 403

### 5.10 POST /api/orders — place order with level-matched coupon → discount reflected
- Setup: coupon restricted to `"A00001"`, user level `"A00001"`, coupon applied to cart
- Call: `POST /api/orders`
- Expect: 201, `discountAmount > 0`, `couponCode` set, wallet deducted at discounted total

### 5.11 POST /api/orders — level changes between apply and checkout → coupon silently dropped
- Setup: coupon restricted to `"A00001"`, user level `"A00001"`, coupon applied to cart,
  then admin changes user level to `"B00001"` before order is placed
- Call: `POST /api/orders`
- Expect: 201, `discountAmount = 0`, `couponCode = null`, full price charged

### 5.12 GET /api/admin/customers — accountLevel shown in list
- Auth: admin JWT
- Setup: one customer has level `"A00001"`, another has none
- Expect: 200, each customer object includes `accountLevel` field

---

## Test Data Conventions

- Use `UUID`-based unique emails and coupon codes per test (no hardcoding)
- Account level values: `"A00001"`, `"B00001"`, `"VIP"`, `"GOLD"` for variety
- Seed wallet balance before placing orders

---

## Non-Goals

- Frontend E2E tests (out of scope for unit/integration plan)
- Load / performance tests for level lookup

---

## Notes on Implementation

- `CouponHelper.validateCoupon()` and `tryValidateCoupon()` now take a 4th argument
  `String accountLevel`. All callers (`CartService`, `OrderService`) pass `account.getLevel()`.
- Account level comparison is **case-insensitive** (`equalsIgnoreCase`).
- A `null` coupon `accountLevel` means **no restriction** — all accounts may use it.
- A `null` account `level` (unassigned) will **fail** any level-restricted coupon check.
- Existing tests for `CouponHelper`, `CartService`, `OrderService` that use the 3-arg
  overloads must be updated to the 4-arg signatures (pass `null` as `accountLevel`).
