# 📚 Bookstore — Java Spring Boot + Angular

A full-stack bookstore application with a **Java Spring Boot** REST API backend and **Angular 17** frontend.

---

## Features

- **Browse & search books** — filter by title, author, genre, and price range
- **User authentication** — register, login, JWT-based sessions (7-day expiry)
- **Role-based access** — customers shop, admins manage inventory
- **Shopping cart** — add, update quantity, remove items; price snapshots prevent stale-price bugs
- **Order management** — place orders (decrements stock), list history, cancel pending orders (restores stock)
- **Swagger UI** — interactive API docs at `/docs`
- **Angular frontend** — reactive SPA with book grid, cart panel, auth modal, order history, and admin dashboard

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 21, Spring Boot 3.2, Spring Security |
| Frontend | Angular 17 (standalone components, signals) |
| Auth | JWT (Bearer token), BCrypt password hashing |
| Validation | Jakarta Bean Validation |
| Database | Oracle XE (production), H2 in-memory Oracle mode (tests) |
| ORM | Spring Data JPA (Hibernate 6) |
| API Docs | SpringDoc / Swagger UI at `/docs` |

---

## Getting Started

### Prerequisites

- Java 21+
- Maven 3.9+
- Node.js 18+ and npm (for the frontend)
- Angular CLI 17: `npm install -g @angular/cli@17`
- Docker (for Oracle XE database)

### Start the Oracle Database

```bash
docker run -d \
  --name oracle-xe \
  -p 1521:1521 \
  -e ORACLE_PASSWORD=oracle \
  gvenzl/oracle-xe
```

Then create the `bookstore` database user (see [Database Setup → Step 2](#2-create-the-bookstore-database-user) below). The backend connects to `jdbc:oracle:thin:@localhost:1521/XEPDB1` as `bookstore`.

### Run the Backend

```bash
cd backend
mvn spring-boot:run
```

- API base: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/docs`
- Health check: `http://localhost:8080/health`

### Run the Frontend

```bash
cd frontend
npm install
ng serve
```

- App: `http://localhost:4200`
- All `/api` requests are proxied to `http://localhost:8080`

### Project Structure

```
bookstore/
├── backend/                    # Spring Boot Maven project
│   ├── pom.xml
│   └── src/main/java/com/bookstore/
│       ├── config/             # SecurityConfig, CorsConfig, OpenApiConfig
│       ├── model/              # JPA entities (User, Book, Cart, Order, Coupon, ...)
│       ├── repository/         # Spring Data JPA repositories
│       ├── store/              # InMemoryStore (unused; kept for reference)
│       ├── security/           # JwtUtil, JwtAuthFilter, BookstorePrincipal
│       ├── dto/                # Request/response DTOs
│       ├── service/            # Business logic
│       ├── controller/         # REST controllers
│       ├── exception/          # GlobalExceptionHandler
│       └── seed/               # DataSeeder (ApplicationRunner)
├── frontend/                   # Angular 17 project
│   ├── angular.json
│   └── src/app/
│       ├── models/             # TypeScript interfaces
│       ├── services/           # HTTP services (auth, books, cart, orders, admin, account)
│       ├── interceptors/       # Auth JWT interceptor
│       ├── guards/             # authGuard, adminGuard
│       └── components/         # navbar, auth, shop, cart, orders, admin
└── plan/                       # Architecture docs
```

---

## Database Setup

### 1. Start Oracle XE with Docker

```bash
docker run -d \
  --name oracle-xe \
  -p 1521:1521 \
  -e ORACLE_PASSWORD=oracle \
  gvenzl/oracle-xe
```

Wait ~60 seconds for the database to be ready. You can check with:

```bash
docker logs -f oracle-xe
# Ready when you see: DATABASE IS READY TO USE!
```

### 2. Create the `bookstore` Database User

The application connects as a dedicated `bookstore` schema user. Run the following as `system` (or any DBA user) before starting the backend:

```sql
-- Connect as system, then run:
CREATE USER bookstore IDENTIFIED BY bookstore123;
GRANT CONNECT, RESOURCE TO bookstore;
GRANT UNLIMITED TABLESPACE TO bookstore;
```

These credentials are already configured in `backend/src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:oracle:thin:@localhost:1521/XEPDB1
spring.datasource.username=bookstore
spring.datasource.password=bookstore123
```

### 3. Schema — Auto-Created by Hibernate

Tables are created automatically on first startup via `spring.jpa.hibernate.ddl-auto=update`. No manual DDL is needed.

The following 8 tables are created in the connected schema:

| Table | Description |
|-------|-------------|
| `BS_USERS` | Registered users (customers and admins) |
| `BS_BOOKS` | Book catalog |
| `BS_CARTS` | One cart per user |
| `BS_CART_ITEMS` | Line items inside a cart (collection table) |
| `BS_ORDERS` | Placed orders |
| `BS_ORDER_ITEMS` | Line items inside an order (collection table) |
| `BS_ACCOUNTS` | Wallet balance per user |
| `BS_TRANSACTIONS` | Wallet transaction history |
| `BS_COUPONS` | Discount coupons |

### 4. Reference DDL

Equivalent Oracle DDL for the tables Hibernate generates (useful for auditing or manual setup):

```sql
-- Users
CREATE TABLE BS_USERS (
    ID            VARCHAR2(36)  NOT NULL PRIMARY KEY,
    EMAIL         VARCHAR2(255) NOT NULL UNIQUE,
    PASSWORD_HASH VARCHAR2(80)  NOT NULL,
    NAME          VARCHAR2(255) NOT NULL,
    ROLE          VARCHAR2(20)  NOT NULL,
    CREATED_AT    TIMESTAMP     NOT NULL
);

-- Books
CREATE TABLE BS_BOOKS (
    ID          VARCHAR2(36)   NOT NULL PRIMARY KEY,
    ISBN        VARCHAR2(50)   NOT NULL UNIQUE,
    TITLE       VARCHAR2(500)  NOT NULL,
    AUTHOR      VARCHAR2(255)  NOT NULL,
    GENRE       VARCHAR2(100)  NOT NULL,
    PRICE       NUMBER         NOT NULL,
    STOCK       NUMBER(10)     NOT NULL,
    DESCRIPTION VARCHAR2(2000),
    CREATED_AT  TIMESTAMP      NOT NULL,
    UPDATED_AT  TIMESTAMP      NOT NULL
);

-- Carts (one row per user)
CREATE TABLE BS_CARTS (
    USER_ID     VARCHAR2(36) NOT NULL PRIMARY KEY,
    COUPON_CODE VARCHAR2(50),
    UPDATED_AT  TIMESTAMP    NOT NULL
);

-- Cart line items
CREATE TABLE BS_CART_ITEMS (
    CART_USER_ID VARCHAR2(36) NOT NULL REFERENCES BS_CARTS(USER_ID),
    BOOK_ID      VARCHAR2(36) NOT NULL,
    QUANTITY     NUMBER(10)   NOT NULL,
    PRICE_AT_ADD NUMBER       NOT NULL
);

-- Orders
CREATE TABLE BS_ORDERS (
    ID              VARCHAR2(36) NOT NULL PRIMARY KEY,
    USER_ID         VARCHAR2(36) NOT NULL,
    SUBTOTAL        NUMBER       NOT NULL,
    DISCOUNT_AMOUNT NUMBER       NOT NULL,
    TOTAL_AMOUNT    NUMBER       NOT NULL,
    COUPON_CODE     VARCHAR2(50),
    STATUS          VARCHAR2(20) NOT NULL
        CHECK (STATUS IN ('PENDING','CONFIRMED','SHIPPED','DELIVERED','CANCELLED')),
    CREATED_AT      TIMESTAMP    NOT NULL,
    UPDATED_AT      TIMESTAMP    NOT NULL
);

-- Order line items
CREATE TABLE BS_ORDER_ITEMS (
    ORDER_ID      VARCHAR2(36)  NOT NULL REFERENCES BS_ORDERS(ID),
    BOOK_ID       VARCHAR2(36)  NOT NULL,
    TITLE         VARCHAR2(500) NOT NULL,
    QUANTITY      NUMBER(10)    NOT NULL,
    PRICE_AT_ORDER NUMBER       NOT NULL
);

-- Wallet accounts (one row per user)
CREATE TABLE BS_ACCOUNTS (
    USER_ID    VARCHAR2(36) NOT NULL PRIMARY KEY,
    BALANCE    NUMBER       NOT NULL,
    UPDATED_AT TIMESTAMP    NOT NULL
);

-- Wallet transaction history
CREATE TABLE BS_TRANSACTIONS (
    ID            VARCHAR2(36)  NOT NULL PRIMARY KEY,
    USER_ID       VARCHAR2(36)  NOT NULL,
    TYPE          VARCHAR2(30)  NOT NULL
        CHECK (TYPE IN ('DEPOSIT','PURCHASE','REFUND')),
    AMOUNT        NUMBER        NOT NULL,
    BALANCE_AFTER NUMBER        NOT NULL,
    DESCRIPTION   VARCHAR2(500),
    ORDER_ID      VARCHAR2(36),
    CREATED_AT    TIMESTAMP     NOT NULL
);

-- Discount coupons
CREATE TABLE BS_COUPONS (
    ID                 VARCHAR2(36)  NOT NULL PRIMARY KEY,
    CODE               VARCHAR2(50)  NOT NULL UNIQUE,
    TYPE               VARCHAR2(20)  NOT NULL
        CHECK (TYPE IN ('percentage','fixed')),
    COUPON_VALUE       NUMBER        NOT NULL,
    MIN_ORDER_AMOUNT   NUMBER        NOT NULL,
    MAX_USES           NUMBER(10),
    USED_COUNT         NUMBER(10)    NOT NULL,
    IS_ACTIVE          NUMBER(1)     NOT NULL,
    EXPIRES_AT         TIMESTAMP,
    CREATED_AT         TIMESTAMP     NOT NULL,
    NEW_USER_ONLY_DAYS NUMBER(10),
    DESCRIPTION        VARCHAR2(500)
);
```

### 5. Verify the Tables

After starting the backend, connect and confirm:

```sql
-- List all bookstore tables
SELECT table_name FROM user_tables WHERE table_name LIKE 'BS\_%' ESCAPE '\' ORDER BY 1;

-- Check seeded admin user
SELECT id, email, role FROM bs_users;

-- Check seeded books
SELECT id, title, stock FROM bs_books ORDER BY created_at;
```

### 6. Reset the Schema

To wipe all data and start fresh:

```sql
DROP TABLE BS_CART_ITEMS;
DROP TABLE BS_ORDER_ITEMS;
DROP TABLE BS_CARTS;
DROP TABLE BS_ORDERS;
DROP TABLE BS_TRANSACTIONS;
DROP TABLE BS_ACCOUNTS;
DROP TABLE BS_COUPONS;
DROP TABLE BS_BOOKS;
DROP TABLE BS_USERS;
```

Then restart the backend — Hibernate will recreate the tables and `DataSeeder` will re-seed the admin user and sample books.

---

## Default Credentials

The server seeds these on every fresh start:

| Role | Email | Password |
|------|-------|----------|
| Admin | `admin@bookstore.com` | `admin123` |

> Customers can self-register via `POST /api/auth/register` or the sign-in page on the frontend.

---

## API Reference

Interactive docs are available at **`http://localhost:3000/docs`** when the server is running.

### Base URL

```
http://localhost:3000/api
```

### Authentication

Protected routes require a `Bearer` token in the `Authorization` header:

```
Authorization: Bearer <token>
```

Obtain a token from `POST /api/auth/login`.

---

### Auth

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `POST` | `/auth/register` | None | Create a new customer account |
| `POST` | `/auth/login` | None | Login and receive a JWT |
| `GET` | `/auth/me` | Bearer | Get your own profile |

**Register example:**
```bash
curl -X POST http://localhost:3000/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name": "Alice", "email": "alice@example.com", "password": "secret123"}'
```

**Login example:**
```bash
curl -X POST http://localhost:3000/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "alice@example.com", "password": "secret123"}'
```

---

### Books

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `GET` | `/books` | None | List / search books |
| `GET` | `/books/:id` | None | Get a single book |
| `POST` | `/books` | Admin | Create a book |
| `PATCH` | `/books/:id` | Admin | Partially update a book |
| `DELETE` | `/books/:id` | Admin | Delete a book |

**Search query parameters:**

| Param | Type | Description |
|-------|------|-------------|
| `title` | string | Case-insensitive title search |
| `author` | string | Case-insensitive author search |
| `genre` | string | Case-insensitive genre filter |
| `minPrice` | number | Minimum price (inclusive) |
| `maxPrice` | number | Maximum price (inclusive) |
| `page` | integer | Page number (default: `1`) |
| `limit` | integer | Results per page (default: `20`, max: `100`) |

**Example:**
```bash
curl "http://localhost:3000/api/books?genre=fantasy&maxPrice=15&page=1"
```

---

### Cart

All cart endpoints require a Bearer token.

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/cart` | Get your current cart |
| `POST` | `/cart/items` | Add a book to your cart |
| `PATCH` | `/cart/items/:bookId` | Update item quantity (set to `0` to remove) |
| `DELETE` | `/cart` | Clear your entire cart |

**Add to cart example:**
```bash
curl -X POST http://localhost:3000/api/cart/items \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"bookId": "<uuid>", "quantity": 2}'
```

---

### Orders

All order endpoints require a Bearer token.

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `POST` | `/orders` | Bearer | Place an order from your current cart |
| `GET` | `/orders` | Bearer | List your orders |
| `GET` | `/orders/:id` | Bearer | Get a single order |
| `PATCH` | `/orders/:id/cancel` | Bearer | Cancel a pending order |
| `GET` | `/orders/admin/all` | Admin | List all orders (with optional `?status=` filter) |

---

## Design Notes

- **Oracle DB persistence** — all data is stored in Oracle XE via Spring Data JPA. Tables are prefixed with `BS_` (e.g. `BS_USERS`, `BS_ORDERS`) to avoid Oracle reserved-word conflicts. Schema is auto-managed via `spring.jpa.hibernate.ddl-auto=update`.
- **H2 for tests** — integration and unit tests use an H2 in-memory database in Oracle compatibility mode (`jdbc:h2:mem:testdb;MODE=Oracle`), so no Docker is needed to run the test suite.
- **Price snapshots** — `priceAtAdd` and `priceAtOrder` capture the price at the time of the action, so changing a book's price never affects existing carts or order history.
- **Stock atomicity** — `placeOrder` runs inside a `@Transactional` method, so database-level locking prevents race conditions in concurrent requests.
- **Admin role** — `DataSeeder` creates one admin on startup if the users table is empty. To promote an existing user, add an admin-only endpoint or adjust the seeder.

---

## License

MIT
