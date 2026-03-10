# Test Plan — Coupon Account-Identity Restriction

**Feature:** A coupon can be restricted to a specific account (user) by ID.
When a coupon has `allowedUserId` set, only the account whose ID matches
may apply or redeem it. All other accounts receive a rejection.

Example: coupon `NEWIT` with `allowedUserId = "user-uuid-of-A00001"` can
only be applied by that specific user.

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

### 1.1 Account-restricted coupon — matching userId → valid
- Setup: coupon with `allowedUserId = "user-A"`, caller userId `"user-A"`
- Call: `validateCoupon(code, subtotal, userRegisteredAt, "user-A")`
- Expect: coupon returned, no exception

### 1.2 Account-restricted coupon — different userId → throws
- Setup: coupon with `allowedUserId = "user-A"`, caller userId `"user-B"`
- Call: `validateCoupon(code, subtotal, userRegisteredAt, "user-B")`
- Expect: `IllegalArgumentException("Coupon is not valid for this account")`

### 1.3 Account-restricted coupon — null userId → throws
- Setup: coupon with `allowedUserId = "user-A"`, caller userId `null`
- Expect: `IllegalArgumentException`

### 1.4 No allowedUserId restriction — any userId → valid
- Setup: coupon with `allowedUserId = null`
- Call: `validateCoupon(code, subtotal, userRegisteredAt, "any-user")`
- Expect: coupon returned

### 1.5 No allowedUserId restriction — null userId → valid (other checks still apply)
- Setup: coupon with `allowedUserId = null`, no new-user restriction
- Call: `validateCoupon(code, subtotal, null, null)`
- Expect: coupon returned (userId ignored when no restriction set)

### 1.6 tryValidateCoupon — wrong userId → returns null (silent)
- Setup: coupon with `allowedUserId = "user-A"`, caller `"user-B"`
- Call: `tryValidateCoupon(code, subtotal, userRegisteredAt, "user-B")`
- Expect: `null` (no exception propagated)

### 1.7 tryValidateCoupon — matching userId → returns coupon
- Setup: coupon with `allowedUserId = "user-A"`, caller `"user-A"`
- Call: `tryValidateCoupon(code, subtotal, userRegisteredAt, "user-A")`
- Expect: coupon returned

### 1.8 allowedUserId combined with other constraints — all pass → valid
- Setup: coupon with `allowedUserId = "user-A"`, `minOrderAmount = 50`, active, not expired
- Subtotal = 100, caller = `"user-A"`
- Expect: coupon returned

### 1.9 allowedUserId check is last — other constraint fails first
- Setup: coupon with `allowedUserId = "user-A"`, `isActive = false`
- Caller = `"user-A"`
- Expect: `IllegalArgumentException("Coupon is not active")` — active check fires before identity check

---

## 2. CartService Unit Tests

### 2.1 applyCoupon — caller is the allowed user → coupon applied
- Setup: user with id `"user-A"`, coupon with `allowedUserId = "user-A"`, valid subtotal
- Call: `applyCoupon("user-A", req)`
- Expect: cart saved with `couponCode` set, enriched response shows discount

### 2.2 applyCoupon — caller is a different user → 400 BAD_REQUEST
- Setup: user `"user-B"`, coupon with `allowedUserId = "user-A"`
- Call: `applyCoupon("user-B", req)`
- Expect: `ResponseStatusException` 400 with message about account restriction

### 2.3 applyCoupon — unrestricted coupon, any user → applied
- Setup: coupon with `allowedUserId = null`
- Call: `applyCoupon("any-user", req)`
- Expect: coupon applied successfully

### 2.4 enrichCart — restricted coupon was applied but cart now belongs to different user context
- (edge case: not normally reachable via API, but defensive test)
- Setup: cart with restricted coupon code for `"user-A"`, enriched as `"user-B"`
- Expect: coupon silently cleared (tryValidateCoupon returns null)

---

## 3. OrderService Unit Tests

### 3.1 placeOrder — caller is the allowed user, coupon applied → discount applied
- Setup: coupon with `allowedUserId = "user-A"`, cart belongs to `"user-A"` with coupon
- Call: `placeOrder("user-A")`
- Expect: order with `discountAmount > 0`, `couponCode` set, `usedCount` incremented

### 3.2 placeOrder — coupon applied by allowed user but at checkout coupon restricted to someone else (data anomaly) → silently dropped
- Setup: coupon's `allowedUserId = "user-B"`, cart for `"user-A"` somehow has that coupon code
- Call: `placeOrder("user-A")`
- Expect: order created with `discountAmount = 0`, `couponCode = null`

### 3.3 placeOrder — no coupon, unrestricted user → order normal
- Setup: coupon with no `allowedUserId`, cart has no coupon
- Expect: order created without discount, wallet charged full subtotal

---

## 4. AdminService Unit Tests

### 4.1 createCoupon — with allowedUserId → persisted on coupon
- Setup: `CreateCouponRequest` with `allowedUserId = "user-uuid-123"`
- Call: `createCoupon(req)`
- Expect: saved coupon has `getAllowedUserId() == "user-uuid-123"`

### 4.2 createCoupon — without allowedUserId (null) → no restriction
- Setup: `CreateCouponRequest` with `allowedUserId = null`
- Call: `createCoupon(req)`
- Expect: saved coupon has `getAllowedUserId() == null`

### 4.3 createCoupon — blank allowedUserId trimmed to null
- Setup: `CreateCouponRequest` with `allowedUserId = "  "`
- Expect: `getAllowedUserId()` returns `null` (trimmed blank → null)

---

## 5. Integration Tests

### 5.1 POST /api/admin/coupons — create account-restricted coupon
- Auth: admin JWT
- Body: `{ code: "NEWIT", type: "fixed", value: 5, description: "...", allowedUserId: "<target-user-id>" }`
- Expect: 201, response body includes `allowedUserId: "<target-user-id>"`

### 5.2 POST /api/cart/coupon — allowed user applies restricted coupon → 200
- Setup: register target user, get their UUID; admin creates coupon `NEWIT` with that UUID as `allowedUserId`; user adds item to cart
- Auth: target user JWT
- Body: `{ code: "NEWIT" }`
- Expect: 200, response includes `couponCode: "NEWIT"`, `discountAmount > 0`

### 5.3 POST /api/cart/coupon — different user tries restricted coupon → 400
- Setup: same coupon `NEWIT` restricted to user-A; request made by user-B
- Auth: user-B JWT
- Expect: 400, error message indicates coupon is not valid for this account

### 5.4 POST /api/cart/coupon — unrestricted coupon, any user → 200
- Setup: coupon with no `allowedUserId`
- Expect: 200, any authenticated user can apply it

### 5.5 POST /api/orders — allowed user redeems restricted coupon → discount in order
- Setup: coupon restricted to user-A, user-A applies it to cart
- Call: `POST /api/orders`
- Expect: 201, `discountAmount > 0`, `couponCode` set, `usedCount` incremented to 1,
  wallet deducted at discounted total

### 5.6 POST /api/orders — wrong user, coupon silently dropped at checkout
- Setup: coupon restricted to user-A (somehow present on user-B's cart)
- Call: `POST /api/orders` as user-B
- Expect: 201, `discountAmount = 0`, `couponCode = null`, full price charged

### 5.7 GET /api/admin/coupons — allowedUserId shown in list
- Auth: admin JWT
- Setup: one coupon with `allowedUserId` set, one without
- Expect: 200, `allowedUserId` field present on each coupon object (null when unrestricted)

---

## Test Data Conventions

- Use `UUID`-based unique emails and coupon codes per test (no hardcoding)
- Seed wallet balance (via admin credit) before placing orders
- Look up the target user's UUID from `/api/admin/customers` or register response

---

## Notes on Implementation

- `CouponHelper.validateCoupon()` and `tryValidateCoupon()` take a 4th argument `String userId`.
  All callers (`CartService`, `OrderService`) pass the authenticated user's ID directly —
  no additional repository lookup is required.
- `null` coupon `allowedUserId` means **no restriction** — any account may use the coupon.
- Matching is exact (`.equals()`), not case-insensitive, since user IDs are UUIDs.
- Existing tests for `CouponHelper`, `CartService`, `OrderService` that used the old 3-arg
  overloads must be updated to the 4-arg signatures (pass `null` or appropriate userId).
