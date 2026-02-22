# 📚 BookStore API

A full-stack book store application built with **TypeScript** and **Express 5** — featuring a REST API backend, an Amazon-style browser frontend, and interactive Swagger docs.

---

## Features

- **Browse & search books** — filter by title, author, genre, and price range
- **User authentication** — register, login, JWT-based sessions (7-day expiry)
- **Role-based access** — customers shop, admins manage inventory
- **Shopping cart** — add, update quantity, remove items; price snapshots prevent stale-price bugs
- **Order management** — place orders (decrements stock), list history, cancel pending orders (restores stock)
- **Swagger UI** — interactive API docs at `/docs`
- **Amazon-style frontend** — vanilla HTML/CSS/JS served directly by Express

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Runtime | Node.js 18+ |
| Language | TypeScript 5 (strict mode) |
| Framework | Express 5 |
| Auth | JSON Web Tokens (`jsonwebtoken`) + `bcryptjs` |
| Validation | Zod 4 |
| Storage | In-memory Maps (no database required) |
| API Docs | swagger-ui-express |
| Dev server | ts-node-dev |

---

## Getting Started

### Prerequisites

- Node.js 18 or higher
- npm 9 or higher

### Installation

```bash
git clone https://github.com/chunyatx/bookstore.git
cd bookstore
npm install
```

### Environment Variables

Copy the example env file and set your JWT secret:

```bash
cp .env.example .env
```

`.env` options:

| Variable | Default | Description |
|----------|---------|-------------|
| `PORT` | `3000` | Port the server listens on |
| `JWT_SECRET` | `change-me-in-production` | Secret used to sign JWT tokens — **change this in production** |

---

## Developer Menu

### Scripts

| Command | Description |
|---------|-------------|
| `npm run dev` | Start development server with hot-reload (`ts-node-dev`) |
| `npm run build` | Compile TypeScript to `dist/` |
| `npm start` | Run the compiled production build |

### Development workflow

```bash
# Start hot-reloading dev server
npm run dev

# Build for production
npm run build

# Run production build
npm start
```

### Project Structure

```
bookstore/
├── public/
│   └── index.html              # Amazon-style frontend (single file, no build step)
├── src/
│   ├── index.ts                # Entry point — loads env, seeds data, starts server
│   ├── app.ts                  # Express app setup and route wiring
│   ├── seed.ts                 # Startup seed: admin user + 15 sample books
│   ├── swagger.ts              # OpenAPI 3.0 spec object
│   ├── types/
│   │   └── index.ts            # TypeScript interfaces + Zod validation schemas
│   ├── store/
│   │   └── index.ts            # In-memory singleton Maps (the "database")
│   ├── middleware/
│   │   └── auth.ts             # JWT authenticate + requireRole middleware
│   ├── routes/
│   │   ├── auth.routes.ts
│   │   ├── books.routes.ts
│   │   ├── cart.routes.ts
│   │   └── orders.routes.ts
│   └── controllers/
│       ├── auth.controller.ts
│       ├── books.controller.ts
│       ├── cart.controller.ts
│       └── orders.controller.ts
├── .env.example
├── .gitignore
├── package.json
└── tsconfig.json
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

- **In-memory storage** — all data lives in Node.js `Map` objects and is reset on server restart. Swap out `src/store/index.ts` for a database adapter to make it persistent.
- **Price snapshots** — `priceAtAdd` and `priceAtOrder` capture the price at the time of the action, so changing a book's price never affects existing carts or order history.
- **Stock atomicity** — `placeOrder` validates all items before mutating any stock. Because Node.js is single-threaded and no `await` exists between the check and the mutate, this is race-condition-free for in-memory storage.
- **Admin role** — the seed creates one admin on startup. To promote an existing user you would update their `role` directly in `usersStore` (or add an admin-only `PATCH /api/users/:id` endpoint).

---

## License

MIT
