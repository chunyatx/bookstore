# Changelog

## Java Spring Boot + Angular Conversion & Fixes

### Backend: Java Spring Boot Migration

**Converted** the original Node.js/Express application to a Java 21 + Spring Boot 3.2.5 backend
and an Angular 17 standalone-component frontend, preserving all existing API contracts.

---

### Bug Fixes

#### `OrderService.cancelOrder()` — NullPointerException guard
- **File:** `backend/src/main/java/com/bookstore/service/OrderService.java`
- **Problem:** If the order ID did not exist, `store.orders.get(id)` returned `null` and the
  method immediately threw an unhandled NPE instead of a proper 404.
- **Fix:** Added an explicit null-check that throws `ResponseStatusException(NOT_FOUND)` when
  the order is not found, and a second null-check for the user account before attempting
  the wallet refund.

#### `AdminService.refundOrder()` — NullPointerException guard
- **File:** `backend/src/main/java/com/bookstore/service/AdminService.java`
- **Problem:** Same pattern as above — no null-check on the fetched order, causing an NPE
  when an invalid order ID was passed.
- **Fix:** Added null-check throwing `ResponseStatusException(NOT_FOUND)`.

#### `BookService.listBooks()` — Negative `Stream.skip()` on page 0 or negative page
- **File:** `backend/src/main/java/com/bookstore/service/BookService.java`
- **Problem:** The skip value was computed as `(page - 1) * limit`. When `page` was 0 or
  negative the skip became negative, causing an `IllegalArgumentException` from the Stream API.
- **Fix:** Clamped `page` to a minimum of `1` before computing the skip offset.

#### `AdminService.createCoupon()` — `DateTimeParseException` leaked as HTTP 500
- **File:** `backend/src/main/java/com/bookstore/service/AdminService.java`
- **Problem:** `Instant.parse(expiresAt)` only accepts ISO-8601 with a `Z` suffix. Sending a
  datetime-local string (e.g. `2025-12-31T23:59`) from the Angular form threw an unhandled
  `DateTimeParseException`, which the global handler converted to a 500.
- **Fix:** Replaced `Instant.parse()` with `LocalDateTime.parse()` via
  `DateTimeFormatter.ISO_LOCAL_DATE_TIME`, then converted to `Instant` using UTC. Invalid
  formats now return HTTP 400 with a descriptive message.

#### `CartService.applyCoupon()` — `IllegalArgumentException` not wrapped in HTTP 400
- **File:** `backend/src/main/java/com/bookstore/service/CartService.java`
- **Problem:** `CouponHelper.validateCoupon()` throws `IllegalArgumentException` for invalid
  or below-minimum-order coupons. `applyCoupon()` did not catch this, so the raw exception
  propagated and was mapped to HTTP 500 by the default handler instead of HTTP 400.
- **Fix:** Wrapped the `validateCoupon()` call in a try-catch that rethrows as
  `ResponseStatusException(BAD_REQUEST, e.getMessage())`.

#### `CartService.enrichCart()` — Expired coupon cleared from cart but not from response
- **File:** `backend/src/main/java/com/bookstore/service/CartService.java`
- **Problem:** `resp.setCouponCode(cart.getCouponCode())` was called at the top of
  `enrichCart()`, before the coupon-validation block. When an expired/invalid coupon was
  detected and `cart.setCouponCode(null)` was called, the response DTO still held the old
  coupon code, causing the API to return `couponCode: "OLD"` even after silent clearing.
- **Fix:** Added `resp.setCouponCode(null)` immediately after `cart.setCouponCode(null)` in
  the else-branch of the coupon validation block.

---

### Tests Added

#### Unit Tests (`backend/src/test/java/com/bookstore/service/`)

| File | Tests | What is covered |
|---|---|---|
| `AuthServiceTest.java` | 11 | Register (success, duplicate email, missing fields), login (correct, wrong password, unknown user), `me()` (valid token, expired token) |
| `BookServiceTest.java` | 17 | List with filters (title, author, genre, price range, pagination), get by ID (found, not found), create (success, duplicate ISBN), update (success, not found), delete (success, not found) |
| `CartServiceTest.java` | 18 | Add item (success, exceeds stock), update qty (remove on 0), clear cart, apply coupon (success, invalid code → 400, below min order → 400), remove coupon, enrich cart (expired coupon silently cleared) |
| `OrderServiceTest.java` | 16 | Place order (success, empty cart → 400, insufficient wallet → 400), list orders, cancel order (success, non-pending → 400, wrong user → 403, not found → 404), wallet/stock side-effects verified |
| `AdminServiceTest.java` | 26 | List/get customers, credit/debit wallet, list/update-status/refund orders, list/create/activate/deactivate coupons, list/create/update/delete books |
| `CouponHelperTest.java` | 15 | Validate (inactive, expired, maxed-out, below min-order, valid), compute discount (percentage capped at subtotal, fixed, zero), tryValidateCoupon (null on invalid, coupon on valid) |

All unit tests use the real `InMemoryStore` (no mocking) and `BCryptPasswordEncoder(strength=4)`
for speed.

#### Integration Tests (`backend/src/test/java/com/bookstore/integration/`)

| File | Tests | What is covered |
|---|---|---|
| `AuthIntegrationTest.java` | 9 | Full HTTP stack: register (201, 409 duplicate, 400 missing field), login (200+JWT, 401 wrong password, 401 unknown), `/me` (200, 401 no token, 401 invalid token) |
| `BooksIntegrationTest.java` | 14 | Public book listing, title-filter, pagination, get by ID (200/404), create book (admin→201, customer→403, anon→401, duplicate ISBN→409), PATCH title, DELETE (204 then 404, customer→403) |
| `CartOrderIntegrationTest.java` | 15 | Cart CRUD, coupon apply/remove, full checkout happy-path (wallet deducted, stock reduced, cart cleared), empty-cart→400, insufficient-wallet→400, cancel order (wallet refunded, stock restored), cancel non-pending→400, cancel other-user's order→403, admin status advance |

Integration tests use `@SpringBootTest` + `@AutoConfigureMockMvc`. Tests share a single Spring
application context for speed (no `@DirtiesContext`); isolation is achieved with UUID-based
unique emails and ISBNs per test.

#### Maven Build Config (`backend/.mvn/maven.config`)
- Added `-Dmaven.resolver.transport=wagon` to force Maven 3.9 to use the Wagon HTTP transport,
  which correctly handles authenticated HTTPS proxy tunneling via `settings.xml`.

**Total: 141 tests, 0 failures.**

---

### Frontend: Angular Template Literal Fix

- **Files:** `shop.component.ts`, `cart-panel.component.ts`, `orders.component.ts`,
  `navbar.component.ts`, `admin-customers.component.ts`, `admin-orders.component.ts`,
  `admin-coupons.component.ts`
- **Problem:** Angular inline templates are written inside TypeScript backtick strings. The
  sequence `${{` is interpreted by the TypeScript compiler as the start of a template literal
  interpolation `${...}`, causing compile errors (e.g. `TS2304: Cannot find name 'number'`)
  at every `${{ value | number:'1.2-2' }}` price binding.
- **Fix:** Escaped every occurrence as `\${{`. TypeScript consumes the backslash escape and
  emits a literal `$` character; Angular's template compiler then sees `${{ expr }}` and
  processes it normally. All price displays are unaffected at runtime.

---

### Miscellaneous

#### `.gitignore` — Added build output directories
- Added `backend/target/`, `frontend/dist/`, `frontend/node_modules/`, and
  `frontend/.angular/` to prevent Maven, Angular build, and Angular CLI cache artifacts
  from appearing as untracked files.

---

### Frontend: Login & Balance Fixes

#### Login — Balance not refreshed on sign-in
- **Files:** `navbar.component.ts`, `account.service.ts`
- **Problem:** After a successful login the navbar balance stayed at `$0.00` until the user
  manually clicked "Refresh Balance", because `loadBalance()` was never called on auth state
  change.
- **Fix:** Added an `effect()` in `NavbarComponent` that watches `auth.isLoggedIn()`; when it
  becomes `true` the balance is fetched automatically. When the user logs out the balance is
  reset to `0` immediately.

#### Login — Current page not checked for accessibility after sign-in
- **Files:** `auth-modal.component.ts`
- **Problem:** Angular route guards only run on navigation, not when authentication state
  changes in place. If a user was on `/admin`, logged out (staying on the page), then logged
  back in as a regular customer, the admin page remained visible with no redirect.
- **Fix:** After a successful login, `AuthModalComponent` checks `router.url`; if the current
  path starts with `/admin` and the newly logged-in user is not an admin, it navigates to `/`.

#### Cancel Order — Balance not refreshed in navbar
- **Files:** `orders.component.ts`, `account.service.ts`
- **Problem:** Cancelling an order refunds the wallet on the backend, but the navbar balance
  was not updated — it only changed after a manual "Refresh Balance" click.
- **Fix:** Added a `triggerRefresh()` method (backed by an RxJS `Subject`) to `AccountService`.
  `OrdersComponent` calls it after a successful cancel; `NavbarComponent` subscribes to the
  observable and calls `loadBalance()` whenever a refresh is requested.
