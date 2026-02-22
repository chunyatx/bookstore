import request from "supertest";
import { app } from "../src/app";
import { clearStores, seedBook, seedUser } from "./helpers";
import { cartsStore } from "../src/store";
import { Cart } from "../src/types";

beforeEach(clearStores);

// ── GET /api/cart ──────────────────────────────────────────────────────────────

describe("GET /api/cart", () => {
  it("returns an empty cart for a new user", async () => {
    const user = await seedUser();

    const res = await request(app)
      .get("/api/cart")
      .set("Authorization", `Bearer ${user.token}`);

    expect(res.status).toBe(200);
    expect(res.body.items).toEqual([]);
  });

  it("returns 401 when unauthenticated", async () => {
    const res = await request(app).get("/api/cart");
    expect(res.status).toBe(401);
  });
});

// ── POST /api/cart/items ───────────────────────────────────────────────────────

describe("POST /api/cart/items", () => {
  it("adds a book to the cart", async () => {
    const user = await seedUser();
    const book = seedBook({ stock: 5, price: 12.5 });

    const res = await request(app)
      .post("/api/cart/items")
      .set("Authorization", `Bearer ${user.token}`)
      .send({ bookId: book.id, quantity: 2 });

    expect(res.status).toBe(200);
    expect(res.body.items).toHaveLength(1);
    expect(res.body.items[0]).toMatchObject({
      bookId: book.id,
      quantity: 2,
      priceAtAdd: 12.5,
      title: book.title,
    });
  });

  it("increments quantity when the same book is added again", async () => {
    const user = await seedUser();
    const book = seedBook({ stock: 10 });

    await request(app)
      .post("/api/cart/items")
      .set("Authorization", `Bearer ${user.token}`)
      .send({ bookId: book.id, quantity: 3 });

    const res = await request(app)
      .post("/api/cart/items")
      .set("Authorization", `Bearer ${user.token}`)
      .send({ bookId: book.id, quantity: 2 });

    expect(res.status).toBe(200);
    expect(res.body.items[0].quantity).toBe(5);
  });

  it("returns 400 when quantity exceeds stock", async () => {
    const user = await seedUser();
    const book = seedBook({ stock: 3 });

    const res = await request(app)
      .post("/api/cart/items")
      .set("Authorization", `Bearer ${user.token}`)
      .send({ bookId: book.id, quantity: 10 });

    expect(res.status).toBe(400);
    expect(res.body.error).toMatch(/stock/i);
  });

  it("returns 404 when book does not exist", async () => {
    const user = await seedUser();

    const res = await request(app)
      .post("/api/cart/items")
      .set("Authorization", `Bearer ${user.token}`)
      .send({ bookId: "00000000-0000-0000-0000-000000000000", quantity: 1 });

    expect(res.status).toBe(404);
  });

  it("returns 400 for invalid payload (non-UUID bookId)", async () => {
    const user = await seedUser();

    const res = await request(app)
      .post("/api/cart/items")
      .set("Authorization", `Bearer ${user.token}`)
      .send({ bookId: "not-a-uuid", quantity: 1 });

    expect(res.status).toBe(400);
  });

  it("returns 400 when quantity is zero", async () => {
    const user = await seedUser();
    const book = seedBook({ stock: 5 });

    const res = await request(app)
      .post("/api/cart/items")
      .set("Authorization", `Bearer ${user.token}`)
      .send({ bookId: book.id, quantity: 0 });

    expect(res.status).toBe(400);
  });

  it("returns 401 when unauthenticated", async () => {
    const book = seedBook();
    const res = await request(app)
      .post("/api/cart/items")
      .send({ bookId: book.id, quantity: 1 });
    expect(res.status).toBe(401);
  });
});

// ── PATCH /api/cart/items/:bookId ─────────────────────────────────────────────

describe("PATCH /api/cart/items/:bookId", () => {
  it("updates the quantity of an existing cart item", async () => {
    const user = await seedUser();
    const book = seedBook({ stock: 10 });

    // Prime the cart
    await request(app)
      .post("/api/cart/items")
      .set("Authorization", `Bearer ${user.token}`)
      .send({ bookId: book.id, quantity: 3 });

    const res = await request(app)
      .patch(`/api/cart/items/${book.id}`)
      .set("Authorization", `Bearer ${user.token}`)
      .send({ quantity: 7 });

    expect(res.status).toBe(200);
    expect(res.body.items[0].quantity).toBe(7);
  });

  it("removes the item when quantity is set to 0", async () => {
    const user = await seedUser();
    const book = seedBook({ stock: 5 });

    await request(app)
      .post("/api/cart/items")
      .set("Authorization", `Bearer ${user.token}`)
      .send({ bookId: book.id, quantity: 2 });

    const res = await request(app)
      .patch(`/api/cart/items/${book.id}`)
      .set("Authorization", `Bearer ${user.token}`)
      .send({ quantity: 0 });

    expect(res.status).toBe(200);
    expect(res.body.items).toHaveLength(0);
  });

  it("returns 400 when quantity exceeds stock", async () => {
    const user = await seedUser();
    const book = seedBook({ stock: 4 });

    await request(app)
      .post("/api/cart/items")
      .set("Authorization", `Bearer ${user.token}`)
      .send({ bookId: book.id, quantity: 2 });

    const res = await request(app)
      .patch(`/api/cart/items/${book.id}`)
      .set("Authorization", `Bearer ${user.token}`)
      .send({ quantity: 10 });

    expect(res.status).toBe(400);
  });

  it("returns 404 when the cart is empty", async () => {
    const user = await seedUser();
    const book = seedBook();

    const res = await request(app)
      .patch(`/api/cart/items/${book.id}`)
      .set("Authorization", `Bearer ${user.token}`)
      .send({ quantity: 1 });

    expect(res.status).toBe(404);
  });

  it("returns 404 when book is not in the cart", async () => {
    const user = await seedUser();
    const book1 = seedBook({ stock: 5 });
    const book2 = seedBook({ stock: 5 });

    await request(app)
      .post("/api/cart/items")
      .set("Authorization", `Bearer ${user.token}`)
      .send({ bookId: book1.id, quantity: 1 });

    const res = await request(app)
      .patch(`/api/cart/items/${book2.id}`)
      .set("Authorization", `Bearer ${user.token}`)
      .send({ quantity: 1 });

    expect(res.status).toBe(404);
  });
});

// ── DELETE /api/cart ───────────────────────────────────────────────────────────

describe("DELETE /api/cart", () => {
  it("clears the cart", async () => {
    const user = await seedUser();
    const book = seedBook({ stock: 5 });

    await request(app)
      .post("/api/cart/items")
      .set("Authorization", `Bearer ${user.token}`)
      .send({ bookId: book.id, quantity: 2 });

    const del = await request(app)
      .delete("/api/cart")
      .set("Authorization", `Bearer ${user.token}`);
    expect(del.status).toBe(204);

    // Verify cart is gone from store
    const cart = cartsStore.get(user.id) as Cart | undefined;
    expect(cart).toBeUndefined();
  });

  it("returns 401 when unauthenticated", async () => {
    const res = await request(app).delete("/api/cart");
    expect(res.status).toBe(401);
  });
});
