# CLAUDE.md — Bookstore Codebase Guide

This document describes the full-stack bookstore application structure, conventions, and workflows for AI assistants working in this repository.

---

## Project Overview

A full-stack bookstore application with a **Java 21 + Spring Boot 3.2.5** REST API backend and an **Angular 17** standalone-component frontend.

**Backend base URL:** `http://localhost:8080`
**Frontend dev server:** `http://localhost:4200` (proxies `/api` to port 8080)
**Swagger UI:** `http://localhost:8080/docs`
**Health check:** `http://localhost:8080/health`

---

## Repository Layout

```
bookstore/
├── backend/                         # Spring Boot Maven project
│   ├── pom.xml
│   ├── .mvn/maven.config            # Maven transport config
│   └── src/
│       ├── main/java/com/bookstore/
│       │   ├── BookstoreApplication.java
│       │   ├── config/              # SecurityConfig, CorsConfig, OpenApiConfig
│       │   ├── controller/          # REST controllers (thin — delegate to services)
│       │   ├── dto/
│       │   │   ├── request/         # Inbound request POJOs
│       │   │   └── response/        # Outbound response POJOs
│       │   ├── exception/           # GlobalExceptionHandler
│       │   ├── model/               # JPA entity POJOs
│       │   ├── repository/          # Spring Data JPA repositories
│       │   ├── security/            # JwtUtil, JwtAuthFilter, BookstorePrincipal
│       │   ├── seed/                # DataSeeder (ApplicationRunner)
│       │   ├── service/             # Business logic layer
│       │   └── store/               # InMemoryStore (legacy — unit tests only)
│       └── test/java/com/bookstore/
│           ├── integration/         # @SpringBootTest + @AutoConfigureMockMvc tests
│           └── service/             # Service-layer unit tests + TestFixtures + MockRepositories
├── frontend/                        # Angular 17 project
│   ├── angular.json
│   ├── package.json
│   └── src/app/
│       ├── app.component.ts
│       ├── app.config.ts            # provideRouter, provideHttpClient
│       ├── app.routes.ts            # Lazy-loaded routes
│       ├── components/              # Standalone UI components
│       │   ├── admin/               # admin, admin-coupons, admin-customers, admin-orders
│       │   ├── auth/                # auth-modal
│       │   ├── cart/                # cart-panel
│       │   ├── navbar/              # navbar
│       │   ├── orders/              # orders
│       │   └── shop/                # shop (main book grid)
│       ├── guards/                  # auth.guard.ts, admin.guard.ts
│       ├── interceptors/            # auth.interceptor.ts (attaches JWT Bearer header)
│       ├── models/                  # index.ts — all TypeScript interfaces
│       └── services/                # account, admin, auth, book, cart, order, toast
├── plan/                            # Architecture markdown docs
├── docs/                            # Test case reports (PDF)
├── CHANGES.md                       # Detailed changelog
└── README.md
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend language | Java 21 |
| Backend framework | Spring Boot 3.2.5 (spring-boot-starter-web, -security, -validation, -data-jpa) |
| Auth | JWT via `jjwt` 0.12.5; BCrypt (strength 12) |
| Database (prod) | Oracle XE via `ojdbc11` 23.4.0; `spring.jpa.hibernate.ddl-auto=update` |
| Database (test) | H2 in Oracle-compatibility mode; `ddl-auto=create-drop` |
| API docs | SpringDoc / Swagger UI (`/docs`) |
| Frontend language | TypeScript 5.4 |
| Frontend framework | Angular 17 (standalone components, signals) |
| HTTP client | Angular `HttpClient` with functional interceptor |
| Reactive | RxJS 7.8 |
| Build (backend) | Maven 3.9 |
| Build (frontend) | Angular CLI 17 |

---

## Development Commands

### Backend

```bash
cd backend
mvn spring-boot:run          # Start dev server on :8080 (requires Oracle DB)
mvn test                     # Run all 140 tests (uses H2 — no Oracle needed)
mvn package -DskipTests      # Build JAR
```

### Frontend

```bash
cd frontend
npm install                  # Install dependencies (first time)
ng serve                     # Dev server on :4200 with proxy to :8080
ng build                     # Production build to dist/
```

> The frontend's `/api` proxy is configured in `frontend/src/proxy.conf.json`.

---

## Backend Architecture

### Database & Persistence

The application uses **Oracle XE** via **Spring Data JPA** (Hibernate). Hibernate DDL `update` mode creates tables automatically on first run. Data persists across server restarts.

**JPA table mapping:**

| Entity | Table | Notes |
|---|---|---|
| `User` | `BS_USERS` | |
| `Book` | `BS_BOOKS` | |
| `Cart` | `BS_CARTS` | PK is `USER_ID` |
| `CartItem` | `BS_CART_ITEMS` | `@ElementCollection` on `Cart` |
| `Order` | `BS_ORDERS` | |
| `OrderItem` | `BS_ORDER_ITEMS` | `@ElementCollection` on `Order` |
| `Account` | `BS_ACCOUNTS` | PK is `USER_ID` |
| `Transaction` | `BS_TRANSACTIONS` | |
| `Coupon` | `BS_COUPONS` | column `COUPON_VALUE` (not `VALUE` — Oracle reserved word) |

**Oracle setup** (Docker quick-start):
```bash
docker run -d -p 1521:1521 -e ORACLE_PASSWORD=oracle gvenzl/oracle-xe
# Then create user:
# CREATE USER bookstore IDENTIFIED BY bookstore123;
# GRANT CONNECT, RESOURCE, UNLIMITED TABLESPACE TO bookstore;
```

### Repository Layer (`repository/`)

Seven Spring Data JPA repositories. Services inject these instead of `InMemoryStore`.

| Repository | Key custom queries |
|---|---|
| `UserRepository` | `findByEmail`, `existsByEmail`, `findByRole` |
| `BookRepository` | `existsByIsbn`, `findAllByOrderByCreatedAtAsc` |
| `CartRepository` | inherits `findById` / `save` (keyed by userId) |
| `OrderRepository` | `findByUserIdOrderByCreatedAtDesc`, `findAllByOrderByCreatedAtDesc`, `findByStatusOrderByCreatedAtDesc` |
| `AccountRepository` | inherits `findById` / `save` (keyed by userId) |
| `TransactionRepository` | `findByUserIdOrderByCreatedAtDesc` |
| `CouponRepository` | `findByCode`, `existsByCode`, `findAllByOrderByCreatedAtDesc` |

### Domain Models (`model/`)

JPA `@Entity` POJOs with standard getters/setters (no Lombok). All annotated with `@Table(name = "BS_*")`.

| Class | Key Fields |
|---|---|
| `User` | id, email, passwordHash, name, role (`"customer"` or `"admin"`), createdAt |
| `Book` | id, title, author, genre, price, stock, description, isbn, createdAt, updatedAt |
| `Cart` | userId (PK), `List<CartItem>` (`@ElementCollection`), couponCode, updatedAt |
| `CartItem` | bookId, quantity, priceAtAdd (price snapshot) |
| `Order` | id, userId, `List<OrderItem>` (`@ElementCollection`), subtotal, discountAmount, totalAmount, couponCode, status, createdAt, updatedAt |
| `OrderItem` | bookId, title, quantity, priceAtOrder (price snapshot) |
| `OrderStatus` | enum: `pending`, `confirmed`, `shipped`, `cancelled` |
| `Account` | userId (PK), balance (wallet) |
| `Transaction` | id, userId, type, amount, description, orderId (nullable), balanceAfter, createdAt |
| `TransactionType` | enum: `credit`, `debit`, `order_payment`, `refund` |
| `Coupon` | id, code, type, value, description, minOrderAmount, maxUses (nullable), usedCount, isActive, expiresAt (nullable), createdAt, newUserOnlyDays (nullable) |
| `CouponType` | enum: `percentage`, `fixed` |

### Service Layer (`service/`)

All services are `@Transactional`. Controllers are thin and delegate all business logic to services:

| Service | Responsibility |
|---|---|
| `AuthService` | Register, login (returns JWT), `/me` profile |
| `BookService` | List (with filters + pagination), get by ID, create, update, delete |
| `CartService` | Add item, update quantity, clear, apply/remove coupon, enrich cart |
| `OrderService` | Place order (validates all items then mutates stock + wallet), list, get, cancel |
| `AccountService` | Get account info, load transactions |
| `AdminService` | Manage customers, orders, books, coupons (admin-only operations) |
| `CouponHelper` | `validateCoupon(code, subtotal, userRegisteredAt)`, `tryValidateCoupon(code, subtotal, userRegisteredAt)` (returns null on invalid), `computeDiscount()` |

### Security

- **Stateless JWT** — no server-side sessions. `JwtAuthFilter` reads `Authorization: Bearer <token>` on every request.
- **Roles** — Spring Security uses `ROLE_` prefix internally; store uses bare strings `"customer"` / `"admin"`. `BookstorePrincipal` bridges the two.
- **BCrypt** strength 12 in production (strength 4 in tests for speed).
- **JWT expiry** — 7 days (604800000 ms), configured in `application.properties`.

**Public endpoints** (no auth required):
- `POST /api/auth/register`, `POST /api/auth/login`
- `GET /api/books`, `GET /api/books/**`
- `/docs/**`, `/api-docs/**`, `/swagger-ui/**`, `/health`

**Admin-only:** `/api/admin/**`

**Authenticated (any role):** all other `/api/**` endpoints.

### Exception Handling

`GlobalExceptionHandler` (`@RestControllerAdvice`) maps:
- `ResponseStatusException` → its HTTP status and reason
- `MethodArgumentNotValidException` → 400 with field-level validation errors
- Unhandled exceptions → 500

Services throw `ResponseStatusException` (e.g. `NOT_FOUND`, `BAD_REQUEST`, `CONFLICT`, `FORBIDDEN`) directly — do not throw raw exceptions expecting the handler to catch them.

### Key Design Rules

1. **Price snapshots** — `CartItem.priceAtAdd` and `OrderItem.priceAtOrder` capture prices at action time. Never recalculate from current `Book.price` for existing items.
2. **Stock validation order** — `OrderService.placeOrder()` validates ALL items have sufficient stock before mutating ANY stock. The wrapping `@Transactional` handles atomicity; no explicit `synchronized` block is needed.
3. **Email case** — Always store and look up emails as `email.toLowerCase().trim()`.
4. **Coupon codes** — Always stored and looked up as `code.toUpperCase()`.
5. **UUID IDs** — All entity IDs are `UUID.randomUUID().toString()`.
6. **New-user-only coupons** — `CouponHelper.validateCoupon()` and `tryValidateCoupon()` require a third argument `Instant userRegisteredAt`. Callers (`CartService`, `OrderService`) must pass `user.getCreatedAt()`. A coupon with `newUserOnlyDays = N` rejects users whose account is older than N days.

---

## Frontend Architecture

### Standalone Components (Angular 17)

All components use `standalone: true` — there are no `NgModule` files. Imports are declared directly in each component's `@Component` decorator.

### Signals

State management uses Angular signals (`signal()`, `computed()`, `effect()`). Prefer signals over `BehaviorSubject` for component-local state.

### Routes (`app.routes.ts`)

| Path | Component | Guards |
|---|---|---|
| `/` | `ShopComponent` | none |
| `/orders` | `OrdersComponent` | `authGuard` |
| `/admin` | `AdminComponent` | `authGuard`, `adminGuard` |
| `**` | redirect to `/` | — |

All routes are lazy-loaded via `loadComponent`.

### Services (`services/`)

| Service | Purpose |
|---|---|
| `AuthService` | Login, logout, register; stores JWT in `localStorage`; exposes `isLoggedIn()` and `currentUser()` signals |
| `BookService` | Book listing with filters, CRUD |
| `CartService` | Cart state, coupon application |
| `OrderService` | Place, list, cancel orders |
| `AccountService` | Wallet balance, transactions; exposes `triggerRefresh()` (RxJS Subject) for cross-component balance refresh |
| `AdminService` | Admin CRUD for books, coupons, customers, orders |
| `ToastService` | Global toast notifications |

### Auth Interceptor

`auth.interceptor.ts` attaches `Authorization: Bearer <token>` to every outgoing `HttpClient` request when a JWT exists in `localStorage`.

### Currency Display in Templates

Angular templates are embedded in TypeScript backtick strings. The `${{ }}` Angular pipe syntax must be escaped as `\${{ }}` to prevent TypeScript from interpreting it as a template literal interpolation.

```typescript
// CORRECT — escape the $ inside a TypeScript backtick template string
`<span>\${{ item.price | number:'1.2-2' }}</span>`

// WRONG — TypeScript parse error
`<span>${{ item.price | number:'1.2-2' }}</span>`
```

---

## Testing

### Unit Tests (`src/test/java/com/bookstore/service/`)

- Use **`MockRepositories`** — Mockito-backed `HashMap` mocks of all JPA repositories. Do **not** use the real `InMemoryStore` or `@SpringBootTest` in unit tests.
- Use `TestFixtures` helpers: `addUser(mr, encoder, ...)`, `addBook(mr, ...)`, `addCoupon(mr, ...)`, `credit(mr, userId, amount)`, `addToCart(mr, ...)`.
- Each test creates a fresh `new MockRepositories()` — fully isolated, no shared state.
- `BCryptPasswordEncoder(4)` for speed.

### Integration Tests (`src/test/java/com/bookstore/integration/`)

- Use `@SpringBootTest(webEnvironment = MOCK)` + `@AutoConfigureMockMvc`.
- Run against **H2** in Oracle-compatibility mode (`src/test/resources/application.properties` overrides the datasource). No Oracle instance required to run tests.
- Share a single Spring context (no `@DirtiesContext`) for speed; `ddl-auto=create-drop` rebuilds the schema once per test run.
- Isolation is achieved by generating UUID-based unique emails and ISBNs per test — never hardcode values that could collide.
- Perform HTTP calls via `MockMvc` and assert full response status + JSON body.

### Running Tests

```bash
cd backend && mvn test   # No Oracle DB needed — uses H2
```

Total: **140 tests, 0 failures** as of last update.

---

## Seeded Data

`DataSeeder` runs on every fresh application start (skips if users already exist):

### Admin Account

| Email | Password | Role |
|---|---|---|
| `admin@bookstore.com` | `admin123` | admin |

### Sample Books (15 total)

Genres: Science Fiction, Dystopian Fiction, Literary Fiction, Fantasy, Mystery, Fiction, Non-Fiction, Biography, Business, Memoir.

### Coupons (3 seeded)

| Code | Type | Value | Constraint |
|---|---|---|---|
| `WELCOME10` | percentage | 10% | No minimum |
| `SAVE5` | fixed | $5 off | Min order $20 |
| `HALFOFF` | percentage | 50% off | Max 10 uses |

---

## API Endpoints Reference

### Auth

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/auth/register` | None | Create customer account |
| POST | `/api/auth/login` | None | Login → JWT |
| GET | `/api/auth/me` | Bearer | Own profile |

### Books

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/books` | None | List/search (params: title, author, genre, minPrice, maxPrice, page, limit) |
| GET | `/api/books/:id` | None | Get single book |
| POST | `/api/books` | Admin | Create book |
| PATCH | `/api/books/:id` | Admin | Partial update |
| DELETE | `/api/books/:id` | Admin | Delete book |

### Cart

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/cart` | Bearer | Get enriched cart |
| POST | `/api/cart/items` | Bearer | Add item |
| PATCH | `/api/cart/items/:bookId` | Bearer | Update quantity (0 = remove) |
| DELETE | `/api/cart` | Bearer | Clear cart |
| POST | `/api/cart/coupon` | Bearer | Apply coupon |
| DELETE | `/api/cart/coupon` | Bearer | Remove coupon |

### Orders

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/orders` | Bearer | Place order from cart |
| GET | `/api/orders` | Bearer | List own orders |
| GET | `/api/orders/:id` | Bearer | Get single order |
| PATCH | `/api/orders/:id/cancel` | Bearer | Cancel pending order |

### Account

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/account` | Bearer | Balance + transaction history |

### Admin

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/admin/customers` | Admin | List all customers |
| GET | `/api/admin/customers/:id` | Admin | Customer detail |
| POST | `/api/admin/customers/:id/credit` | Admin | Adjust wallet balance |
| GET | `/api/admin/orders` | Admin | All orders (optional `?status=`) |
| PATCH | `/api/admin/orders/:id/status` | Admin | Update order status |
| POST | `/api/admin/orders/:id/refund` | Admin | Refund order |
| GET | `/api/admin/coupons` | Admin | List coupons |
| POST | `/api/admin/coupons` | Admin | Create coupon |
| PATCH | `/api/admin/coupons/:id/activate` | Admin | Activate coupon |
| PATCH | `/api/admin/coupons/:id/deactivate` | Admin | Deactivate coupon |

---

## Configuration

### `backend/src/main/resources/application.properties` (production)

```properties
server.port=8080

# Oracle DataSource
spring.datasource.url=jdbc:oracle:thin:@localhost:1521/XEPDB1
spring.datasource.username=bookstore
spring.datasource.password=bookstore123
spring.datasource.driver-class-name=oracle.jdbc.OracleDriver

# JPA / Hibernate
spring.jpa.database-platform=org.hibernate.dialect.OracleDialect
spring.jpa.hibernate.ddl-auto=update
spring.jpa.properties.hibernate.jdbc.time_zone=UTC

app.jwt.secret=change-me-in-production-use-at-least-32-chars-long-secret
app.jwt.expiration-ms=604800000   # 7 days
springdoc.swagger-ui.path=/docs
springdoc.api-docs.path=/api-docs
```

### `backend/src/test/resources/application.properties` (tests)

```properties
# H2 in Oracle-compatibility mode — no Oracle instance required
spring.datasource.url=jdbc:h2:mem:testdb;MODE=Oracle;DB_CLOSE_DELAY=-1
spring.datasource.driver-class-name=org.h2.Driver
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.jpa.hibernate.ddl-auto=create-drop
```

Override `app.jwt.secret` in production via environment variable or external config.

### `frontend/src/proxy.conf.json`

Proxies all `/api` requests from `localhost:4200` to `localhost:8080` during `ng serve`.

---

## Common Pitfalls

- **Oracle required to run the app** — start the Oracle Docker container before `mvn spring-boot:run`. Tests use H2 and do not need Oracle.
- **Template literal escaping** — always use `\${{` (not `${{`) for Angular pipes inside TypeScript backtick strings.
- **Coupon code lookup** — always `.toUpperCase()` before calling `couponRepository.findByCode()`.
- **Email lookup** — always `.toLowerCase().trim()` before calling `userRepository.findByEmail()` or `existsByEmail()`.
- **Coupon `expiresAt` parsing** — the `AdminService` parses datetime strings with `LocalDateTime` → `Instant` (UTC). Do not use `Instant.parse()` directly for user-supplied strings (they may lack the `Z` suffix).
- **Integration test isolation** — never hardcode emails/ISBNs in integration tests; generate unique values per test to avoid collisions across the shared application context.
- **New-user coupon callers** — `CouponHelper.validateCoupon()` and `tryValidateCoupon()` take a third `Instant userRegisteredAt` argument. Always pass `user.getCreatedAt()`; the two-argument overload does not exist.
- **`InMemoryStore` is not a Spring bean** — it is a plain class retained for unit tests only. Never inject or `@Autowired` it in production code; use the JPA repositories instead.
- **Oracle reserved word `VALUE`** — the `Coupon` entity maps `value` to the column `COUPON_VALUE` to avoid the Oracle reserved word conflict. Use the same pattern for any new numeric columns named `value`, `level`, `comment`, etc.

---

## Git Workflow

- Active development branch: `claude/claude-md-mmd6ju3vhnm4bloz-azCS9`
- Main branch: `main` / `master`
- Commit messages follow imperative mood (e.g. `feat(coupon): add new-user-only registration criteria`).
- Push with: `git push -u origin <branch-name>`
