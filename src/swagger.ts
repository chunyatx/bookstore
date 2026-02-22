export const swaggerSpec = {
  openapi: "3.0.3",
  info: {
    title: "Bookstore API",
    version: "1.0.0",
    description:
      "A REST API for an online book store — browse books, manage your cart, and place orders.",
  },
  servers: [{ url: "http://localhost:3000", description: "Local dev server" }],
  components: {
    securitySchemes: {
      BearerAuth: {
        type: "http",
        scheme: "bearer",
        bearerFormat: "JWT",
        description: "Paste the token returned by POST /api/auth/login",
      },
    },
    schemas: {
      Book: {
        type: "object",
        properties: {
          id: { type: "string", format: "uuid" },
          title: { type: "string", example: "The Hitchhiker's Guide to the Galaxy" },
          author: { type: "string", example: "Douglas Adams" },
          genre: { type: "string", example: "Science Fiction" },
          price: { type: "number", example: 9.99 },
          stock: { type: "integer", example: 42 },
          description: { type: "string" },
          isbn: { type: "string", example: "9780345391803" },
          createdAt: { type: "string", format: "date-time" },
          updatedAt: { type: "string", format: "date-time" },
        },
      },
      User: {
        type: "object",
        properties: {
          id: { type: "string", format: "uuid" },
          email: { type: "string", format: "email" },
          name: { type: "string" },
          role: { type: "string", enum: ["customer", "admin"] },
          createdAt: { type: "string", format: "date-time" },
        },
      },
      CartItem: {
        type: "object",
        properties: {
          bookId: { type: "string", format: "uuid" },
          quantity: { type: "integer", example: 2 },
          priceAtAdd: { type: "number", example: 9.99 },
        },
      },
      Cart: {
        type: "object",
        properties: {
          userId: { type: "string", format: "uuid" },
          items: { type: "array", items: { $ref: "#/components/schemas/CartItem" } },
          updatedAt: { type: "string", format: "date-time" },
        },
      },
      OrderItem: {
        type: "object",
        properties: {
          bookId: { type: "string", format: "uuid" },
          title: { type: "string" },
          quantity: { type: "integer" },
          priceAtOrder: { type: "number" },
        },
      },
      Order: {
        type: "object",
        properties: {
          id: { type: "string", format: "uuid" },
          userId: { type: "string", format: "uuid" },
          items: { type: "array", items: { $ref: "#/components/schemas/OrderItem" } },
          totalAmount: { type: "number", example: 19.98 },
          status: { type: "string", enum: ["pending", "confirmed", "shipped", "cancelled"] },
          createdAt: { type: "string", format: "date-time" },
          updatedAt: { type: "string", format: "date-time" },
        },
      },
      Error: {
        type: "object",
        properties: { error: { type: "string" } },
      },
    },
  },
  tags: [
    { name: "Auth", description: "Register, login, and view your profile" },
    { name: "Books", description: "Browse and manage books (admin: create/update/delete)" },
    { name: "Cart", description: "Manage your shopping cart" },
    { name: "Orders", description: "Place and track orders" },
  ],
  paths: {
    // ── Auth ────────────────────────────────────────────────────────────────
    "/api/auth/register": {
      post: {
        tags: ["Auth"],
        summary: "Register a new account",
        requestBody: {
          required: true,
          content: {
            "application/json": {
              schema: {
                type: "object",
                required: ["email", "password", "name"],
                properties: {
                  email: { type: "string", format: "email", example: "alice@example.com" },
                  password: { type: "string", minLength: 8, example: "password123" },
                  name: { type: "string", example: "Alice" },
                },
              },
            },
          },
        },
        responses: {
          "201": { description: "User created", content: { "application/json": { schema: { $ref: "#/components/schemas/User" } } } },
          "400": { description: "Validation error", content: { "application/json": { schema: { $ref: "#/components/schemas/Error" } } } },
          "409": { description: "Email already registered", content: { "application/json": { schema: { $ref: "#/components/schemas/Error" } } } },
        },
      },
    },
    "/api/auth/login": {
      post: {
        tags: ["Auth"],
        summary: "Login and receive a JWT",
        requestBody: {
          required: true,
          content: {
            "application/json": {
              schema: {
                type: "object",
                required: ["email", "password"],
                properties: {
                  email: { type: "string", format: "email", example: "alice@example.com" },
                  password: { type: "string", example: "password123" },
                },
              },
            },
          },
        },
        responses: {
          "200": {
            description: "Login successful",
            content: {
              "application/json": {
                schema: {
                  type: "object",
                  properties: {
                    token: { type: "string", description: "JWT — use as Bearer token" },
                    user: { $ref: "#/components/schemas/User" },
                  },
                },
              },
            },
          },
          "401": { description: "Invalid credentials", content: { "application/json": { schema: { $ref: "#/components/schemas/Error" } } } },
        },
      },
    },
    "/api/auth/me": {
      get: {
        tags: ["Auth"],
        summary: "Get your own profile",
        security: [{ BearerAuth: [] }],
        responses: {
          "200": { description: "Your profile", content: { "application/json": { schema: { $ref: "#/components/schemas/User" } } } },
          "401": { description: "Unauthorized", content: { "application/json": { schema: { $ref: "#/components/schemas/Error" } } } },
        },
      },
    },

    // ── Books ────────────────────────────────────────────────────────────────
    "/api/books": {
      get: {
        tags: ["Books"],
        summary: "List / search books",
        parameters: [
          { name: "title", in: "query", schema: { type: "string" }, description: "Case-insensitive title search" },
          { name: "author", in: "query", schema: { type: "string" }, description: "Case-insensitive author search" },
          { name: "genre", in: "query", schema: { type: "string" }, description: "Case-insensitive genre filter" },
          { name: "minPrice", in: "query", schema: { type: "number" }, description: "Minimum price (inclusive)" },
          { name: "maxPrice", in: "query", schema: { type: "number" }, description: "Maximum price (inclusive)" },
          { name: "page", in: "query", schema: { type: "integer", default: 1 } },
          { name: "limit", in: "query", schema: { type: "integer", default: 20, maximum: 100 } },
        ],
        responses: {
          "200": {
            description: "Paginated book list",
            content: {
              "application/json": {
                schema: {
                  type: "object",
                  properties: {
                    data: { type: "array", items: { $ref: "#/components/schemas/Book" } },
                    total: { type: "integer" },
                    page: { type: "integer" },
                    limit: { type: "integer" },
                  },
                },
              },
            },
          },
        },
      },
      post: {
        tags: ["Books"],
        summary: "Create a book (admin only)",
        security: [{ BearerAuth: [] }],
        requestBody: {
          required: true,
          content: {
            "application/json": {
              schema: {
                type: "object",
                required: ["title", "author", "genre", "price", "stock", "isbn"],
                properties: {
                  title: { type: "string", example: "Dune" },
                  author: { type: "string", example: "Frank Herbert" },
                  genre: { type: "string", example: "Science Fiction" },
                  price: { type: "number", example: 14.99 },
                  stock: { type: "integer", example: 100 },
                  description: { type: "string", example: "A science fiction epic." },
                  isbn: { type: "string", example: "9780441013593" },
                },
              },
            },
          },
        },
        responses: {
          "201": { description: "Book created", content: { "application/json": { schema: { $ref: "#/components/schemas/Book" } } } },
          "400": { description: "Validation error" },
          "401": { description: "Unauthorized" },
          "403": { description: "Forbidden — admin only" },
          "409": { description: "ISBN already exists" },
        },
      },
    },
    "/api/books/{id}": {
      get: {
        tags: ["Books"],
        summary: "Get a single book",
        parameters: [{ name: "id", in: "path", required: true, schema: { type: "string", format: "uuid" } }],
        responses: {
          "200": { description: "Book details", content: { "application/json": { schema: { $ref: "#/components/schemas/Book" } } } },
          "404": { description: "Not found" },
        },
      },
      patch: {
        tags: ["Books"],
        summary: "Update a book (admin only)",
        security: [{ BearerAuth: [] }],
        parameters: [{ name: "id", in: "path", required: true, schema: { type: "string", format: "uuid" } }],
        requestBody: {
          content: {
            "application/json": {
              schema: {
                type: "object",
                properties: {
                  title: { type: "string" },
                  author: { type: "string" },
                  genre: { type: "string" },
                  price: { type: "number" },
                  stock: { type: "integer" },
                  description: { type: "string" },
                  isbn: { type: "string" },
                },
              },
            },
          },
        },
        responses: {
          "200": { description: "Updated book", content: { "application/json": { schema: { $ref: "#/components/schemas/Book" } } } },
          "401": { description: "Unauthorized" },
          "403": { description: "Forbidden" },
          "404": { description: "Not found" },
        },
      },
      delete: {
        tags: ["Books"],
        summary: "Delete a book (admin only)",
        security: [{ BearerAuth: [] }],
        parameters: [{ name: "id", in: "path", required: true, schema: { type: "string", format: "uuid" } }],
        responses: {
          "204": { description: "Deleted" },
          "401": { description: "Unauthorized" },
          "403": { description: "Forbidden" },
          "404": { description: "Not found" },
        },
      },
    },

    // ── Cart ─────────────────────────────────────────────────────────────────
    "/api/cart": {
      get: {
        tags: ["Cart"],
        summary: "Get your cart",
        security: [{ BearerAuth: [] }],
        responses: {
          "200": { description: "Your cart", content: { "application/json": { schema: { $ref: "#/components/schemas/Cart" } } } },
          "401": { description: "Unauthorized" },
        },
      },
      delete: {
        tags: ["Cart"],
        summary: "Clear your cart",
        security: [{ BearerAuth: [] }],
        responses: {
          "204": { description: "Cart cleared" },
          "401": { description: "Unauthorized" },
        },
      },
    },
    "/api/cart/items": {
      post: {
        tags: ["Cart"],
        summary: "Add an item to your cart",
        security: [{ BearerAuth: [] }],
        requestBody: {
          required: true,
          content: {
            "application/json": {
              schema: {
                type: "object",
                required: ["bookId", "quantity"],
                properties: {
                  bookId: { type: "string", format: "uuid" },
                  quantity: { type: "integer", minimum: 1, example: 2 },
                },
              },
            },
          },
        },
        responses: {
          "200": { description: "Updated cart", content: { "application/json": { schema: { $ref: "#/components/schemas/Cart" } } } },
          "400": { description: "Insufficient stock or validation error" },
          "401": { description: "Unauthorized" },
          "404": { description: "Book not found" },
        },
      },
    },
    "/api/cart/items/{bookId}": {
      patch: {
        tags: ["Cart"],
        summary: "Update item quantity (set to 0 to remove)",
        security: [{ BearerAuth: [] }],
        parameters: [{ name: "bookId", in: "path", required: true, schema: { type: "string", format: "uuid" } }],
        requestBody: {
          required: true,
          content: {
            "application/json": {
              schema: {
                type: "object",
                required: ["quantity"],
                properties: { quantity: { type: "integer", minimum: 0, example: 3 } },
              },
            },
          },
        },
        responses: {
          "200": { description: "Updated cart", content: { "application/json": { schema: { $ref: "#/components/schemas/Cart" } } } },
          "400": { description: "Insufficient stock" },
          "401": { description: "Unauthorized" },
          "404": { description: "Cart or item not found" },
        },
      },
    },

    // ── Orders ───────────────────────────────────────────────────────────────
    "/api/orders": {
      post: {
        tags: ["Orders"],
        summary: "Place an order from your cart",
        security: [{ BearerAuth: [] }],
        responses: {
          "201": { description: "Order placed", content: { "application/json": { schema: { $ref: "#/components/schemas/Order" } } } },
          "400": { description: "Cart is empty or insufficient stock" },
          "401": { description: "Unauthorized" },
        },
      },
      get: {
        tags: ["Orders"],
        summary: "List your orders",
        security: [{ BearerAuth: [] }],
        responses: {
          "200": { description: "Your orders", content: { "application/json": { schema: { type: "array", items: { $ref: "#/components/schemas/Order" } } } } },
          "401": { description: "Unauthorized" },
        },
      },
    },
    "/api/orders/admin/all": {
      get: {
        tags: ["Orders"],
        summary: "List all orders (admin only)",
        security: [{ BearerAuth: [] }],
        parameters: [
          { name: "status", in: "query", schema: { type: "string", enum: ["pending", "confirmed", "shipped", "cancelled"] }, description: "Filter by status" },
        ],
        responses: {
          "200": { description: "All orders", content: { "application/json": { schema: { type: "array", items: { $ref: "#/components/schemas/Order" } } } } },
          "401": { description: "Unauthorized" },
          "403": { description: "Forbidden" },
        },
      },
    },
    "/api/orders/{id}": {
      get: {
        tags: ["Orders"],
        summary: "Get a single order",
        security: [{ BearerAuth: [] }],
        parameters: [{ name: "id", in: "path", required: true, schema: { type: "string", format: "uuid" } }],
        responses: {
          "200": { description: "Order details", content: { "application/json": { schema: { $ref: "#/components/schemas/Order" } } } },
          "401": { description: "Unauthorized" },
          "403": { description: "Forbidden" },
          "404": { description: "Not found" },
        },
      },
    },
    "/api/orders/{id}/cancel": {
      patch: {
        tags: ["Orders"],
        summary: "Cancel a pending order",
        security: [{ BearerAuth: [] }],
        parameters: [{ name: "id", in: "path", required: true, schema: { type: "string", format: "uuid" } }],
        responses: {
          "200": { description: "Cancelled order", content: { "application/json": { schema: { $ref: "#/components/schemas/Order" } } } },
          "400": { description: "Order is not cancellable" },
          "401": { description: "Unauthorized" },
          "403": { description: "Forbidden" },
          "404": { description: "Not found" },
        },
      },
    },
  },
};
