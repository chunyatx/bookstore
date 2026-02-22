import request from "supertest";
import { app } from "../src/app";
import { clearStores, seedBook, seedUser } from "./helpers";
import { booksStore } from "../src/store";

beforeEach(clearStores);

/** Helper: add a book to the user's cart via the API. */
async function addToCart(token: string, bookId: string, quantity: number) {
  return request(app)
    .post("/api/cart/items")
    .set("Authorization", `Bearer ${token}`)
    .send({ bookId, quantity });
}

// ── POST /api/orders ───────────────────────────────────────────────────────────

describe("POST /api/orders", () => {
  it("places an order and deducts stock", async () => {
    const user = await seedUser();
    const book = seedBook({ stock: 10, price: 9.99 });

    await addToCart(user.token, book.id, 3);

    const res = await request(app)
      .post("/api/orders")
      .set("Authorization", `Bearer ${user.token}`);

    expect(res.status).toBe(201);
    expect(res.body.status).toBe("pending");
    expect(res.body.items).toHaveLength(1);
    expect(res.body.items[0].quantity).toBe(3);
    expect(res.body.totalAmount).toBeCloseTo(29.97, 2);

    // Stock must be reduced
    const updated = booksStore.get(book.id)!;
    expect(updated.stock).toBe(7);
  });

  it("clears the cart after placing an order", async () => {
    const user = await seedUser();
    const book = seedBook({ stock: 5 });

    await addToCart(user.token, book.id, 1);
    await request(app)
      .post("/api/orders")
      .set("Authorization", `Bearer ${user.token}`);

    const cartRes = await request(app)
      .get("/api/cart")
      .set("Authorization", `Bearer ${user.token}`);
    expect(cartRes.body.items).toHaveLength(0);
  });

  it("returns 400 when the cart is empty", async () => {
    const user = await seedUser();

    const res = await request(app)
      .post("/api/orders")
      .set("Authorization", `Bearer ${user.token}`);

    expect(res.status).toBe(400);
    expect(res.body.error).toMatch(/cart is empty/i);
  });

  it("returns 400 when a book has insufficient stock at order time", async () => {
    const user = await seedUser();
    const book = seedBook({ stock: 5 });

    await addToCart(user.token, book.id, 5);

    // Reduce stock externally (simulate concurrent purchase)
    const b = booksStore.get(book.id)!;
    booksStore.set(b.id, { ...b, stock: 2 });

    const res = await request(app)
      .post("/api/orders")
      .set("Authorization", `Bearer ${user.token}`);

    expect(res.status).toBe(400);
    expect(res.body.error).toMatch(/insufficient stock/i);
  });

  it("returns 401 when unauthenticated", async () => {
    const res = await request(app).post("/api/orders");
    expect(res.status).toBe(401);
  });
});

// ── GET /api/orders ────────────────────────────────────────────────────────────

describe("GET /api/orders", () => {
  it("returns an empty list when no orders exist", async () => {
    const user = await seedUser();

    const res = await request(app)
      .get("/api/orders")
      .set("Authorization", `Bearer ${user.token}`);

    expect(res.status).toBe(200);
    expect(res.body).toEqual([]);
  });

  it("returns all orders for the authenticated user", async () => {
    const user = await seedUser();
    const book = seedBook({ stock: 20 });

    // Place two orders
    await addToCart(user.token, book.id, 1);
    await request(app).post("/api/orders").set("Authorization", `Bearer ${user.token}`);

    await addToCart(user.token, book.id, 2);
    await request(app).post("/api/orders").set("Authorization", `Bearer ${user.token}`);

    const res = await request(app)
      .get("/api/orders")
      .set("Authorization", `Bearer ${user.token}`);

    expect(res.status).toBe(200);
    expect(res.body).toHaveLength(2);
  });

  it("does not return another user's orders", async () => {
    const alice = await seedUser({ email: "alice@example.com" });
    const bob = await seedUser({ email: "bob@example.com" });
    const book = seedBook({ stock: 10 });

    await addToCart(alice.token, book.id, 1);
    await request(app).post("/api/orders").set("Authorization", `Bearer ${alice.token}`);

    const res = await request(app)
      .get("/api/orders")
      .set("Authorization", `Bearer ${bob.token}`);

    expect(res.status).toBe(200);
    expect(res.body).toHaveLength(0);
  });

  it("returns 401 when unauthenticated", async () => {
    const res = await request(app).get("/api/orders");
    expect(res.status).toBe(401);
  });
});

// ── GET /api/orders/:id ────────────────────────────────────────────────────────

describe("GET /api/orders/:id", () => {
  it("returns the order for its owner", async () => {
    const user = await seedUser();
    const book = seedBook({ stock: 5 });

    await addToCart(user.token, book.id, 1);
    const orderRes = await request(app)
      .post("/api/orders")
      .set("Authorization", `Bearer ${user.token}`);
    const orderId = orderRes.body.id;

    const res = await request(app)
      .get(`/api/orders/${orderId}`)
      .set("Authorization", `Bearer ${user.token}`);

    expect(res.status).toBe(200);
    expect(res.body.id).toBe(orderId);
  });

  it("allows an admin to view any order", async () => {
    const customer = await seedUser({ email: "cust@example.com" });
    const admin = await seedUser({ email: "admin@example.com", role: "admin" });
    const book = seedBook({ stock: 5 });

    await addToCart(customer.token, book.id, 1);
    const orderRes = await request(app)
      .post("/api/orders")
      .set("Authorization", `Bearer ${customer.token}`);
    const orderId = orderRes.body.id;

    const res = await request(app)
      .get(`/api/orders/${orderId}`)
      .set("Authorization", `Bearer ${admin.token}`);

    expect(res.status).toBe(200);
    expect(res.body.id).toBe(orderId);
  });

  it("returns 403 when another customer tries to view the order", async () => {
    const alice = await seedUser({ email: "alice@example.com" });
    const bob = await seedUser({ email: "bob@example.com" });
    const book = seedBook({ stock: 5 });

    await addToCart(alice.token, book.id, 1);
    const orderRes = await request(app)
      .post("/api/orders")
      .set("Authorization", `Bearer ${alice.token}`);
    const orderId = orderRes.body.id;

    const res = await request(app)
      .get(`/api/orders/${orderId}`)
      .set("Authorization", `Bearer ${bob.token}`);

    expect(res.status).toBe(403);
  });

  it("returns 404 for an unknown order id", async () => {
    const user = await seedUser();

    const res = await request(app)
      .get("/api/orders/no-such-order")
      .set("Authorization", `Bearer ${user.token}`);

    expect(res.status).toBe(404);
  });
});

// ── DELETE /api/orders/:id (cancel) ───────────────────────────────────────────

describe("PATCH /api/orders/:id/cancel", () => {
  it("cancels a pending order and restores stock", async () => {
    const user = await seedUser();
    const book = seedBook({ stock: 10 });

    await addToCart(user.token, book.id, 4);
    const orderRes = await request(app)
      .post("/api/orders")
      .set("Authorization", `Bearer ${user.token}`);
    const orderId = orderRes.body.id;

    // Stock should be 6 now
    expect(booksStore.get(book.id)!.stock).toBe(6);

    const res = await request(app)
      .patch(`/api/orders/${orderId}/cancel`)
      .set("Authorization", `Bearer ${user.token}`);

    expect(res.status).toBe(200);
    expect(res.body.status).toBe("cancelled");

    // Stock should be restored to 10
    expect(booksStore.get(book.id)!.stock).toBe(10);
  });

  it("returns 400 when trying to cancel a non-pending order", async () => {
    const user = await seedUser();
    const book = seedBook({ stock: 5 });

    await addToCart(user.token, book.id, 1);
    const orderRes = await request(app)
      .post("/api/orders")
      .set("Authorization", `Bearer ${user.token}`);
    const orderId = orderRes.body.id;

    // Cancel once (now status = "cancelled")
    await request(app)
      .patch(`/api/orders/${orderId}/cancel`)
      .set("Authorization", `Bearer ${user.token}`);

    // Try to cancel again
    const res = await request(app)
      .patch(`/api/orders/${orderId}/cancel`)
      .set("Authorization", `Bearer ${user.token}`);

    expect(res.status).toBe(400);
    expect(res.body.error).toMatch(/cannot cancel/i);
  });

  it("returns 403 when another customer tries to cancel", async () => {
    const alice = await seedUser({ email: "alice@example.com" });
    const bob = await seedUser({ email: "bob@example.com" });
    const book = seedBook({ stock: 5 });

    await addToCart(alice.token, book.id, 1);
    const orderRes = await request(app)
      .post("/api/orders")
      .set("Authorization", `Bearer ${alice.token}`);
    const orderId = orderRes.body.id;

    const res = await request(app)
      .patch(`/api/orders/${orderId}/cancel`)
      .set("Authorization", `Bearer ${bob.token}`);

    expect(res.status).toBe(403);
  });

  it("returns 404 for unknown order", async () => {
    const user = await seedUser();

    const res = await request(app)
      .patch("/api/orders/no-such-order/cancel")
      .set("Authorization", `Bearer ${user.token}`);

    expect(res.status).toBe(404);
  });

  it("allows admin to cancel any order", async () => {
    const customer = await seedUser({ email: "cust@example.com" });
    const admin = await seedUser({ email: "admin@example.com", role: "admin" });
    const book = seedBook({ stock: 5 });

    await addToCart(customer.token, book.id, 1);
    const orderRes = await request(app)
      .post("/api/orders")
      .set("Authorization", `Bearer ${customer.token}`);
    const orderId = orderRes.body.id;

    const res = await request(app)
      .patch(`/api/orders/${orderId}/cancel`)
      .set("Authorization", `Bearer ${admin.token}`);

    expect(res.status).toBe(200);
    expect(res.body.status).toBe("cancelled");
  });
});
