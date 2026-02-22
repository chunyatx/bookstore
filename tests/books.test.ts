import request from "supertest";
import { app } from "../src/app";
import { clearStores, seedBook, seedUser } from "./helpers";

beforeEach(clearStores);

// ── List / search ──────────────────────────────────────────────────────────────

describe("GET /api/books", () => {
  it("returns empty list when store is empty", async () => {
    const res = await request(app).get("/api/books");
    expect(res.status).toBe(200);
    expect(res.body).toMatchObject({ data: [], total: 0, page: 1 });
  });

  it("returns all books with pagination metadata", async () => {
    seedBook({ title: "Alpha" });
    seedBook({ title: "Beta" });

    const res = await request(app).get("/api/books");
    expect(res.status).toBe(200);
    expect(res.body.total).toBe(2);
    expect(res.body.data).toHaveLength(2);
  });

  it("filters by title (case-insensitive)", async () => {
    seedBook({ title: "The Hobbit" });
    seedBook({ title: "Dune" });

    const res = await request(app).get("/api/books?title=hobbit");
    expect(res.status).toBe(200);
    expect(res.body.total).toBe(1);
    expect(res.body.data[0].title).toBe("The Hobbit");
  });

  it("filters by author", async () => {
    seedBook({ author: "Tolkien" });
    seedBook({ author: "Herbert" });

    const res = await request(app).get("/api/books?author=tolkien");
    expect(res.status).toBe(200);
    expect(res.body.total).toBe(1);
    expect(res.body.data[0].author).toBe("Tolkien");
  });

  it("filters by genre", async () => {
    seedBook({ genre: "Fantasy" });
    seedBook({ genre: "Sci-Fi" });

    const res = await request(app).get("/api/books?genre=fantasy");
    expect(res.status).toBe(200);
    expect(res.body.total).toBe(1);
  });

  it("filters by minPrice and maxPrice", async () => {
    seedBook({ price: 5 });
    seedBook({ price: 15 });
    seedBook({ price: 25 });

    const res = await request(app).get("/api/books?minPrice=10&maxPrice=20");
    expect(res.status).toBe(200);
    expect(res.body.total).toBe(1);
    expect(res.body.data[0].price).toBe(15);
  });

  it("paginates results correctly", async () => {
    for (let i = 0; i < 5; i++) seedBook();

    const res = await request(app).get("/api/books?page=2&limit=2");
    expect(res.status).toBe(200);
    expect(res.body.data).toHaveLength(2);
    expect(res.body.page).toBe(2);
    expect(res.body.limit).toBe(2);
    expect(res.body.total).toBe(5);
  });

  it("returns 400 for invalid query params", async () => {
    const res = await request(app).get("/api/books?minPrice=negative");
    expect(res.status).toBe(400);
  });
});

// ── Get single book ────────────────────────────────────────────────────────────

describe("GET /api/books/:id", () => {
  it("returns the book by id", async () => {
    const book = seedBook({ title: "Neuromancer" });

    const res = await request(app).get(`/api/books/${book.id}`);
    expect(res.status).toBe(200);
    expect(res.body).toMatchObject({ id: book.id, title: "Neuromancer" });
  });

  it("returns 404 for unknown id", async () => {
    const res = await request(app).get("/api/books/non-existent-id");
    expect(res.status).toBe(404);
    expect(res.body.error).toMatch(/not found/i);
  });
});

// ── Create book ────────────────────────────────────────────────────────────────

describe("POST /api/books", () => {
  const validPayload = {
    title: "Clean Code",
    author: "Robert Martin",
    genre: "Technology",
    price: 29.99,
    stock: 50,
    description: "A guide to writing clean code",
    isbn: "9780132350884",
  };

  it("admin can create a book", async () => {
    const admin = await seedUser({ role: "admin" });

    const res = await request(app)
      .post("/api/books")
      .set("Authorization", `Bearer ${admin.token}`)
      .send(validPayload);

    expect(res.status).toBe(201);
    expect(res.body).toMatchObject({ title: "Clean Code", isbn: "9780132350884" });
    expect(res.body.id).toBeDefined();
  });

  it("returns 409 when ISBN already exists", async () => {
    const admin = await seedUser({ role: "admin" });
    seedBook({ isbn: "9780132350884" });

    const res = await request(app)
      .post("/api/books")
      .set("Authorization", `Bearer ${admin.token}`)
      .send(validPayload);

    expect(res.status).toBe(409);
    expect(res.body.error).toMatch(/isbn/i);
  });

  it("returns 403 when a customer tries to create a book", async () => {
    const customer = await seedUser({ role: "customer" });

    const res = await request(app)
      .post("/api/books")
      .set("Authorization", `Bearer ${customer.token}`)
      .send(validPayload);

    expect(res.status).toBe(403);
  });

  it("returns 401 when unauthenticated", async () => {
    const res = await request(app).post("/api/books").send(validPayload);
    expect(res.status).toBe(401);
  });

  it("returns 400 for missing required fields", async () => {
    const admin = await seedUser({ role: "admin" });

    const res = await request(app)
      .post("/api/books")
      .set("Authorization", `Bearer ${admin.token}`)
      .send({ title: "Incomplete" });

    expect(res.status).toBe(400);
  });

  it("returns 400 when price is negative", async () => {
    const admin = await seedUser({ role: "admin" });

    const res = await request(app)
      .post("/api/books")
      .set("Authorization", `Bearer ${admin.token}`)
      .send({ ...validPayload, price: -5 });

    expect(res.status).toBe(400);
  });
});

// ── Update book ────────────────────────────────────────────────────────────────

describe("PATCH /api/books/:id", () => {
  it("admin can update a book's price and stock", async () => {
    const admin = await seedUser({ role: "admin" });
    const book = seedBook({ price: 10, stock: 5 });

    const res = await request(app)
      .patch(`/api/books/${book.id}`)
      .set("Authorization", `Bearer ${admin.token}`)
      .send({ price: 19.99, stock: 100 });

    expect(res.status).toBe(200);
    expect(res.body.price).toBe(19.99);
    expect(res.body.stock).toBe(100);
  });

  it("returns 409 when updating to an existing ISBN", async () => {
    const admin = await seedUser({ role: "admin" });
    const book1 = seedBook({ isbn: "1111111111" });
    const book2 = seedBook({ isbn: "2222222222" });

    const res = await request(app)
      .patch(`/api/books/${book2.id}`)
      .set("Authorization", `Bearer ${admin.token}`)
      .send({ isbn: book1.isbn });

    expect(res.status).toBe(409);
  });

  it("returns 404 for unknown book", async () => {
    const admin = await seedUser({ role: "admin" });

    const res = await request(app)
      .patch("/api/books/no-such-book")
      .set("Authorization", `Bearer ${admin.token}`)
      .send({ price: 5 });

    expect(res.status).toBe(404);
  });

  it("returns 403 when a customer tries to update", async () => {
    const customer = await seedUser({ role: "customer" });
    const book = seedBook();

    const res = await request(app)
      .patch(`/api/books/${book.id}`)
      .set("Authorization", `Bearer ${customer.token}`)
      .send({ price: 5 });

    expect(res.status).toBe(403);
  });
});

// ── Delete book ────────────────────────────────────────────────────────────────

describe("DELETE /api/books/:id", () => {
  it("admin can delete a book", async () => {
    const admin = await seedUser({ role: "admin" });
    const book = seedBook();

    const del = await request(app)
      .delete(`/api/books/${book.id}`)
      .set("Authorization", `Bearer ${admin.token}`);
    expect(del.status).toBe(204);

    const get = await request(app).get(`/api/books/${book.id}`);
    expect(get.status).toBe(404);
  });

  it("returns 404 when book does not exist", async () => {
    const admin = await seedUser({ role: "admin" });

    const res = await request(app)
      .delete("/api/books/no-such-book")
      .set("Authorization", `Bearer ${admin.token}`);
    expect(res.status).toBe(404);
  });

  it("returns 403 when a customer tries to delete", async () => {
    const customer = await seedUser({ role: "customer" });
    const book = seedBook();

    const res = await request(app)
      .delete(`/api/books/${book.id}`)
      .set("Authorization", `Bearer ${customer.token}`);
    expect(res.status).toBe(403);
  });
});
