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

The backend connects to `jdbc:oracle:thin:@localhost:1521/XEPDB1` with username `system` and password `oracle`. Adjust `backend/src/main/resources/application.properties` if your setup differs.

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
