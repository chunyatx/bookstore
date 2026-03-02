# Conversion Plan: Java Spring Boot + Angular

## Summary

Convert Node.js/TypeScript/Express + vanilla HTML bookstore to:
- **Backend**: Java Spring Boot 3.2 (REST API, JWT auth, in-memory storage)
- **Frontend**: Angular 17+ (standalone components, signals, reactive forms)

Same REST API contract maintained. Same business logic preserved.

## Project Layout

```
/home/user/bookstore/
├── backend/      # Spring Boot Maven project (port 8080)
├── frontend/     # Angular 17 project (port 4200 dev / proxied to 8080)
├── plan/         # This directory
└── README.md
```

Old files (src/, public/, tests/, package.json, tsconfig.json) removed after validation.

## Backend: Spring Boot 3.2

### Key Dependencies
- spring-boot-starter-web, spring-boot-starter-security, spring-boot-starter-validation
- io.jsonwebtoken:jjwt-api/impl/jackson:0.12.5
- org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0
- java 21

### Architecture
- `model/` — POJOs: User, Book, Cart, CartItem, Order, OrderItem, Coupon, Account, Transaction (+ enums)
- `store/InMemoryStore.java` — @Component singleton with ConcurrentHashMaps
- `security/` — JwtUtil, JwtAuthFilter (OncePerRequestFilter), BookstorePrincipal
- `config/` — SecurityConfig (stateless JWT), CorsConfig (allow port 4200), OpenApiConfig
- `dto/request/` — @Valid annotated request bodies
- `dto/response/` — enriched response shapes
- `service/` — AuthService, BookService, CartService, OrderService, AdminService, AccountService, CouponHelper
- `controller/` — thin @RestControllers, one per domain
- `exception/GlobalExceptionHandler.java` — @ControllerAdvice
- `seed/DataSeeder.java` — ApplicationRunner, idempotent seeding

### Critical Business Logic Rules
| Rule | Implementation |
|---|---|
| Price snapshot at cart add | `priceAtAdd = book.getPrice()` at add-time |
| Price snapshot at order | Use `item.getPriceAtAdd()` for OrderItem.priceAtOrder |
| All-or-nothing stock validation | Validate ALL items before ANY mutation |
| Atomic operations | `synchronized(store)` in placeOrder/cancelOrder |
| Coupon re-validation at checkout | Re-validate inside placeOrder; if invalid, proceed without discount |
| Admin refund: NO stock restore | Only credits wallet, does not touch stock |
| Forward-only order status | pending→confirmed→shipped only |
| Lazy cart creation | `store.carts.computeIfAbsent(userId, k -> new Cart(...))` |

## Frontend: Angular 17

### Structure
- `app.config.ts` — provideRouter, provideHttpClient with authInterceptor
- `app.routes.ts` — lazy routes: / (shop), /orders (auth guard), /admin (admin guard)
- `src/proxy.conf.json` — /api → http://localhost:8080
- `models/` — TypeScript interfaces matching backend DTOs
- `services/` — auth, book, cart, order, admin, account + toast
- `interceptors/auth.interceptor.ts` — functional, attaches Bearer token
- `guards/` — authGuard, adminGuard
- `components/` — navbar, auth-modal, shop, cart-panel, orders, admin/customers|orders|coupons

### Angular Signals Usage
- `AuthService.currentUser = signal<User|null>(null)`
- `CartService.cart = signal<EnrichedCart|null>(null)`
- `CartService.itemCount = computed(() => cart()?.items.length ?? 0)`

## Run Instructions

```bash
# Backend
cd backend
mvn spring-boot:run
# → http://localhost:8080/docs (Swagger)

# Frontend
cd frontend
npm install
ng serve
# → http://localhost:4200
```

## Seed Credentials
- Admin: admin@bookstore.com / admin123
- Sample coupons: WELCOME10 (10%), SAVE5 ($5 off), HALFOFF (50% max 10 uses)
