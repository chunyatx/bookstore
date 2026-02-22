import request from "supertest";
import { app } from "../src/app";
import { clearStores, seedUser } from "./helpers";

beforeEach(clearStores);

describe("POST /api/auth/register", () => {
  it("creates a new user and returns 201 without passwordHash", async () => {
    const res = await request(app).post("/api/auth/register").send({
      email: "alice@example.com",
      password: "secret123",
      name: "Alice",
    });

    expect(res.status).toBe(201);
    expect(res.body).toMatchObject({ email: "alice@example.com", name: "Alice", role: "customer" });
    expect(res.body).not.toHaveProperty("passwordHash");
    expect(res.body.id).toBeDefined();
  });

  it("returns 409 when email is already registered", async () => {
    await seedUser({ email: "alice@example.com" });

    const res = await request(app).post("/api/auth/register").send({
      email: "alice@example.com",
      password: "secret123",
      name: "Alice Again",
    });

    expect(res.status).toBe(409);
    expect(res.body.error).toMatch(/already registered/i);
  });

  it("returns 400 for invalid payload (bad email)", async () => {
    const res = await request(app).post("/api/auth/register").send({
      email: "not-an-email",
      password: "secret123",
      name: "Bob",
    });

    expect(res.status).toBe(400);
    expect(res.body.error).toBeDefined();
  });

  it("returns 400 when password is too short", async () => {
    const res = await request(app).post("/api/auth/register").send({
      email: "bob@example.com",
      password: "123",
      name: "Bob",
    });

    expect(res.status).toBe(400);
  });

  it("returns 400 when name is missing", async () => {
    const res = await request(app).post("/api/auth/register").send({
      email: "bob@example.com",
      password: "secret123",
    });

    expect(res.status).toBe(400);
  });
});

describe("POST /api/auth/login", () => {
  it("returns a JWT token on valid credentials", async () => {
    await seedUser({ email: "alice@example.com", password: "secret123" });

    const res = await request(app).post("/api/auth/login").send({
      email: "alice@example.com",
      password: "secret123",
    });

    expect(res.status).toBe(200);
    expect(res.body.token).toBeDefined();
    expect(typeof res.body.token).toBe("string");
    expect(res.body.user).toMatchObject({ email: "alice@example.com" });
    expect(res.body.user).not.toHaveProperty("passwordHash");
  });

  it("returns 401 for wrong password", async () => {
    await seedUser({ email: "alice@example.com", password: "secret123" });

    const res = await request(app).post("/api/auth/login").send({
      email: "alice@example.com",
      password: "wrongpassword",
    });

    expect(res.status).toBe(401);
    expect(res.body.error).toMatch(/invalid/i);
  });

  it("returns 401 for unknown email", async () => {
    const res = await request(app).post("/api/auth/login").send({
      email: "nobody@example.com",
      password: "secret123",
    });

    expect(res.status).toBe(401);
  });

  it("returns 400 for invalid payload", async () => {
    const res = await request(app).post("/api/auth/login").send({ email: "not-an-email" });
    expect(res.status).toBe(400);
  });
});

describe("GET /api/auth/me", () => {
  it("returns the authenticated user's profile", async () => {
    const user = await seedUser({ email: "alice@example.com", name: "Alice" });

    const res = await request(app)
      .get("/api/auth/me")
      .set("Authorization", `Bearer ${user.token}`);

    expect(res.status).toBe(200);
    expect(res.body).toMatchObject({ email: "alice@example.com", name: "Alice" });
    expect(res.body).not.toHaveProperty("passwordHash");
  });

  it("returns 401 when no token is provided", async () => {
    const res = await request(app).get("/api/auth/me");
    expect(res.status).toBe(401);
  });

  it("returns 401 for a tampered token", async () => {
    const res = await request(app)
      .get("/api/auth/me")
      .set("Authorization", "Bearer this.is.garbage");
    expect(res.status).toBe(401);
  });
});
