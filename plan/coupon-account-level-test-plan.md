# Test Plan — Coupon Account-Identity Restriction (Multiple Users)

**Feature:** A coupon can be restricted to one or more specific accounts by user ID.
When `allowedUserIds` is non-empty, only users whose ID is in the set may apply
or redeem the coupon. An empty set means no restriction — any account can use it.

Example: coupon `NEWIT` with `allowedUserIds = ["user-A-uuid", "user-B-uuid"]`
can only be applied by those two users.

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

### 1.1 allowedUserIds contains caller → valid
- Setup: coupon with `allowedUserIds = {"user-A", "user-B"}`, caller `"user-A"`
- Call: `validateCoupon(code, subtotal, userRegisteredAt, "user-A")`
- Expect: coupon returned

### 1.2 allowedUserIds does not contain caller → throws
- Setup: coupon with `allowedUserIds = {"user-A", "user-B"}`, caller `"user-C"`
- Call: `validateCoupon(code, subtotal, userRegisteredAt, "user-C")`
- Expect: `IllegalArgumentException("Coupon is not valid for this account")`

### 1.3 allowedUserIds set with single entry, caller matches → valid
- Setup: coupon with `allowedUserIds = {"user-A"}`, caller `"user-A"`
- Expect: coupon returned

### 1.4 allowedUserIds set with single entry, caller does not match → throws
- Setup: coupon with `allowedUserIds = {"user-A"}`, caller `"user-B"`
- Expect: `IllegalArgumentException`

### 1.5 allowedUserIds empty set → no restriction, any user valid
- Setup: coupon with `allowedUserIds = {}` (empty)
- Call: `validateCoupon(code, subtotal, userRegisteredAt, "any-user")`
- Expect: coupon returned

### 1.6 allowedUserIds empty, null userId → valid (other checks still apply)
- Setup: coupon with empty `allowedUserIds`, no new-user restriction
- Call: `validateCoupon(code, subtotal, null, null)`
- Expect: coupon returned

### 1.7 allowedUserIds non-empty, null userId → throws
- Setup: coupon with `allowedUserIds = {"user-A"}`, caller `null`
- Expect: `IllegalArgumentException`

### 1.8 tryValidateCoupon — caller not in set → returns null (silent)
- Setup: coupon with `allowedUserIds = {"user-A"}`, caller `"user-X"`
- Expect: `null`

### 1.9 tryValidateCoupon — caller in set → returns coupon
- Setup: coupon with `allowedUserIds = {"user-A", "user-B"}`, caller `"user-B"`
- Expect: coupon returned

### 1.10 allowedUserIds combined with other constraints — all pass
- Setup: coupon with `allowedUserIds = {"user-A"}`, `minOrderAmount = 50`, active, not expired
- Subtotal = 100, caller = `"user-A"`
- Expect: coupon returned

### 1.11 allowedUserIds check is last — active check fires first
- Setup: coupon with `allowedUserIds = {"user-A"}`, `isActive = false`
- Caller = `"user-A"` (valid identity)
- Expect: `IllegalArgumentException("Coupon is not active")` — identity is not checked first

---

## 2. CartService Unit Tests

### 2.1 applyCoupon — caller is in allowedUserIds → applied
- Setup: coupon with `allowedUserIds = {"user-A", "user-B"}`, caller `"user-B"`, valid subtotal
- Expect: cart saved with `couponCode` set, enriched response shows discount

### 2.2 applyCoupon — caller not in allowedUserIds → 400
- Setup: coupon with `allowedUserIds = {"user-A"}`, caller `"user-C"`
- Expect: `ResponseStatusException` 400

### 2.3 applyCoupon — coupon with empty allowedUserIds, any user → applied
- Expect: coupon applied for any authenticated user

### 2.4 enrichCart — caller not in allowedUserIds after cart was populated → silently cleared
- Setup: coupon's `allowedUserIds` no longer contains `cart.getUserId()` at enrich time
- Expect: coupon cleared, `discountAmount = 0`

---

## 3. OrderService Unit Tests

### 3.1 placeOrder — caller in allowedUserIds, coupon applied → discount applied
- Setup: coupon with `allowedUserIds = {"user-A", "user-B"}`, cart for `"user-A"`
- Expect: order with `discountAmount > 0`, `usedCount` incremented

### 3.2 placeOrder — caller not in allowedUserIds → coupon silently ignored
- Setup: coupon with `allowedUserIds = {"user-A"}`, cart for `"user-B"` with that coupon
- Expect: order with `discountAmount = 0`, `couponCode = null`

---

## 4. AdminService Unit Tests

### 4.1 createCoupon — with two allowedUserIds → both persisted
- Setup: `CreateCouponRequest` with `allowedUserIds = ["user-A", "user-B"]`
- Expect: saved coupon's `getAllowedUserIds()` equals `{"user-A", "user-B"}`

### 4.2 createCoupon — null allowedUserIds → empty set (no restriction)
- Setup: `CreateCouponRequest` with `allowedUserIds = null`
- Expect: `getAllowedUserIds()` is empty

### 4.3 createCoupon — empty list allowedUserIds → empty set
- Setup: `CreateCouponRequest` with `allowedUserIds = []`
- Expect: `getAllowedUserIds()` is empty

---

## 5. Integration Tests

### 5.1 POST /api/admin/coupons — create coupon with multiple allowedUserIds
- Auth: admin JWT
- Body: `{ ..., allowedUserIds: ["<user-A-id>", "<user-B-id>"] }`
- Expect: 201, response includes `allowedUserIds` with both IDs

### 5.2 POST /api/cart/coupon — user-A applies (in set) → 200
- Setup: coupon with `allowedUserIds = [user-A, user-B]`; user-A adds item to cart
- Expect: 200, discount applied

### 5.3 POST /api/cart/coupon — user-B applies (in set) → 200
- Setup: same coupon; user-B applies
- Expect: 200, discount applied

### 5.4 POST /api/cart/coupon — user-C applies (not in set) → 400
- Expect: 400, error indicates account restriction

### 5.5 POST /api/cart/coupon — coupon with empty allowedUserIds → any user → 200
- Expect: 200, no identity restriction

### 5.6 POST /api/orders — user-A redeems multi-user coupon → discount in order
- Setup: coupon with `allowedUserIds = [user-A, user-B]`, user-A applies it
- Expect: 201, `discountAmount > 0`, `usedCount` incremented

### 5.7 POST /api/orders — user-B redeems same coupon → discount in order
- Expect: 201, `discountAmount > 0` (each eligible user can redeem independently,
  subject to `maxUses` overall limit)

### 5.8 GET /api/admin/coupons — allowedUserIds shown in response
- Expect: each coupon object includes `allowedUserIds` array (empty or populated)

---

## Test Data Conventions

- Generate unique user IDs per test via registration; use the returned `id`
- Generate unique coupon codes per test (UUID prefix)
- Seed wallet balance before placing orders

---

## Notes on Implementation

- `Coupon.allowedUserIds` is stored in table `BS_COUPON_ALLOWED_USERS(COUPON_ID, USER_ID)`
  as an `@ElementCollection(fetch = EAGER)`.
- An **empty set** means no restriction. A **non-empty set** enforces identity check.
- `CouponHelper` checks `!allowedUserIds.isEmpty()` before calling `.contains(userId)`.
- `CreateCouponRequest.allowedUserIds` is `List<String>`; converted to `HashSet` in `AdminService`.
- Frontend sends `allowedUserIds` as a JSON array (split from comma-separated textarea input).
  An empty array is treated identically to null by the backend.
- Existing tests that previously passed `null` as the 4th arg to `validateCoupon` remain valid
  since an empty `allowedUserIds` skips the identity check entirely.
