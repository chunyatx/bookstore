import request from "supertest";
import { app } from "../src/app";
import { clearStores, seedBook, seedCoupon, seedUser } from "./helpers";
import { accountsStore, couponsStore } from "../src/store";

/** Give a user sufficient wallet balance to pay for their cart. */
function fundAccount(userId: string, balance: number): void {
  accountsStore.set(userId, { userId, balance, updatedAt: new Date() });
}

beforeEach(clearStores);

// ── Helpers ────────────────────────────────────────────────────────────────────

async function addToCart(token: string, bookId: string, quantity: number) {
  return request(app)
    .post("/api/cart/items")
    .set("Authorization", `Bearer ${token}`)
    .send({ bookId, quantity });
}

async function createCouponAsAdmin(
  adminToken: string,
  body: Record<string, unknown>
) {
  return request(app)
    .post("/api/admin/coupons")
    .set("Authorization", `Bearer ${adminToken}`)
    .send(body);
}

// ── POST /api/admin/coupons ────────────────────────────────────────────────────

describe("POST /api/admin/coupons", () => {
  it("creates a percentage coupon", async () => {
    const admin = await seedUser({ role: "admin" });

    const res = await createCouponAsAdmin(admin.token, {
      code: "SAVE10",
      type: "percentage",
      value: 10,
      description: "10% off",
    });

    expect(res.status).toBe(201);
    expect(res.body).toMatchObject({
      code: "SAVE10",
      type: "percentage",
      value: 10,
      description: "10% off",
      isActive: true,
      usedCount: 0,
      minOrderAmount: 0,
      maxUses: null,
      expiresAt: null,
    });
    expect(res.body.id).toBeDefined();
  });

  it("creates a fixed coupon", async () => {
    const admin = await seedUser({ role: "admin" });

    const res = await createCouponAsAdmin(admin.token, {
      code: "SAVE5",
      type: "fixed",
      value: 5,
      description: "$5 off",
      minOrderAmount: 20,
      maxUses: 100,
    });

    expect(res.status).toBe(201);
    expect(res.body).toMatchObject({
      code: "SAVE5",
      type: "fixed",
      value: 5,
      minOrderAmount: 20,
      maxUses: 100,
    });
  });

  it("normalises the coupon code to uppercase", async () => {
    const admin = await seedUser({ role: "admin" });

    const res = await createCouponAsAdmin(admin.token, {
      code: "lowercase",
      type: "percentage",
      value: 5,
      description: "Should be upper",
    });

    expect(res.status).toBe(201);
    expect(res.body.code).toBe("LOWERCASE");
  });

  it("returns 400 when percentage value exceeds 100", async () => {
    const admin = await seedUser({ role: "admin" });

    const res = await createCouponAsAdmin(admin.token, {
      code: "OVER100",
      type: "percentage",
      value: 150,
      description: "Too much",
    });

    expect(res.status).toBe(400);
    expect(res.body.error).toMatch(/100%/i);
  });

  it("returns 400 when required fields are missing", async () => {
    const admin = await seedUser({ role: "admin" });

    const res = await createCouponAsAdmin(admin.token, {
      code: "MISSING",
      // missing type, value, description
    });

    expect(res.status).toBe(400);
  });

  it("returns 409 when the coupon code already exists", async () => {
    const admin = await seedUser({ role: "admin" });
    seedCoupon({ code: "DUPE" });

    const res = await createCouponAsAdmin(admin.token, {
      code: "DUPE",
      type: "fixed",
      value: 3,
      description: "Duplicate",
    });

    expect(res.status).toBe(409);
    expect(res.body.error).toMatch(/already exists/i);
  });

  it("returns 401 when unauthenticated", async () => {
    const res = await request(app).post("/api/admin/coupons").send({
      code: "NOAUTH",
      type: "percentage",
      value: 5,
      description: "No auth",
    });

    expect(res.status).toBe(401);
  });

  it("returns 403 when called by a customer", async () => {
    const customer = await seedUser();

    const res = await request(app)
      .post("/api/admin/coupons")
      .set("Authorization", `Bearer ${customer.token}`)
      .send({
        code: "CUSTCOUP",
        type: "percentage",
        value: 5,
        description: "Should be forbidden",
      });

    expect(res.status).toBe(403);
  });
});

// ── GET /api/admin/coupons ─────────────────────────────────────────────────────

describe("GET /api/admin/coupons", () => {
  it("returns an empty array when no coupons exist", async () => {
    const admin = await seedUser({ role: "admin" });

    const res = await request(app)
      .get("/api/admin/coupons")
      .set("Authorization", `Bearer ${admin.token}`);

    expect(res.status).toBe(200);
    expect(res.body).toEqual([]);
  });

  it("returns all coupons", async () => {
    const admin = await seedUser({ role: "admin" });
    seedCoupon({ code: "FIRST" });
    seedCoupon({ code: "SECOND" });

    const res = await request(app)
      .get("/api/admin/coupons")
      .set("Authorization", `Bearer ${admin.token}`);

    expect(res.status).toBe(200);
    expect(res.body).toHaveLength(2);
    const codes = res.body.map((c: { code: string }) => c.code);
    expect(codes).toContain("FIRST");
    expect(codes).toContain("SECOND");
  });

  it("returns 401 when unauthenticated", async () => {
    const res = await request(app).get("/api/admin/coupons");
    expect(res.status).toBe(401);
  });

  it("returns 403 when called by a customer", async () => {
    const customer = await seedUser();

    const res = await request(app)
      .get("/api/admin/coupons")
      .set("Authorization", `Bearer ${customer.token}`);

    expect(res.status).toBe(403);
  });
});

// ── PATCH /api/admin/coupons/:code/deactivate ──────────────────────────────────

describe("PATCH /api/admin/coupons/:code/deactivate", () => {
  it("deactivates an active coupon", async () => {
    const admin = await seedUser({ role: "admin" });
    seedCoupon({ code: "ACTIVE", isActive: true });

    const res = await request(app)
      .patch("/api/admin/coupons/ACTIVE/deactivate")
      .set("Authorization", `Bearer ${admin.token}`);

    expect(res.status).toBe(200);
    expect(res.body.isActive).toBe(false);

    const stored = Array.from(couponsStore.values()).find((c) => c.code === "ACTIVE");
    expect(stored?.isActive).toBe(false);
  });

  it("is case-insensitive for the code parameter", async () => {
    const admin = await seedUser({ role: "admin" });
    seedCoupon({ code: "MYCODE" });

    const res = await request(app)
      .patch("/api/admin/coupons/mycode/deactivate")
      .set("Authorization", `Bearer ${admin.token}`);

    expect(res.status).toBe(200);
    expect(res.body.isActive).toBe(false);
  });

  it("returns 404 for an unknown coupon code", async () => {
    const admin = await seedUser({ role: "admin" });

    const res = await request(app)
      .patch("/api/admin/coupons/NOSUCHCODE/deactivate")
      .set("Authorization", `Bearer ${admin.token}`);

    expect(res.status).toBe(404);
  });

  it("returns 401 when unauthenticated", async () => {
    const res = await request(app).patch(
      "/api/admin/coupons/ANYCOUPON/deactivate"
    );
    expect(res.status).toBe(401);
  });
});

// ── PATCH /api/admin/coupons/:code/activate ────────────────────────────────────

describe("PATCH /api/admin/coupons/:code/activate", () => {
  it("activates an inactive coupon", async () => {
    const admin = await seedUser({ role: "admin" });
    seedCoupon({ code: "INACTIVE", isActive: false });

    const res = await request(app)
      .patch("/api/admin/coupons/INACTIVE/activate")
      .set("Authorization", `Bearer ${admin.token}`);

    expect(res.status).toBe(200);
    expect(res.body.isActive).toBe(true);
  });

  it("returns 404 for an unknown coupon code", async () => {
    const admin = await seedUser({ role: "admin" });

    const res = await request(app)
      .patch("/api/admin/coupons/NOSUCHCODE/activate")
      .set("Authorization", `Bearer ${admin.token}`);

    expect(res.status).toBe(404);
  });

  it("returns 401 when unauthenticated", async () => {
    const res = await request(app).patch(
      "/api/admin/coupons/ANYCOUPON/activate"
    );
    expect(res.status).toBe(401);
  });
});

// ── POST /api/cart/coupon ──────────────────────────────────────────────────────

describe("POST /api/cart/coupon", () => {
  it("applies a percentage coupon and returns correct discount", async () => {
    const user = await seedUser();
    const book = seedBook({ price: 20, stock: 5 });
    seedCoupon({ code: "PCT10", type: "percentage", value: 10 });

    await addToCart(user.token, book.id, 2); // subtotal = 40

    const res = await request(app)
      .post("/api/cart/coupon")
      .set("Authorization", `Bearer ${user.token}`)
      .send({ code: "PCT10" });

    expect(res.status).toBe(200);
    expect(res.body.subtotal).toBeCloseTo(40, 2);
    expect(res.body.discountAmount).toBeCloseTo(4, 2); // 10% of 40
    expect(res.body.finalTotal).toBeCloseTo(36, 2);
    expect(res.body.coupon).toMatchObject({ code: "PCT10", discountAmount: 4 });
  });

  it("applies a fixed coupon and returns correct discount", async () => {
    const user = await seedUser();
    const book = seedBook({ price: 30, stock: 5 });
    seedCoupon({ code: "FIXED5", type: "fixed", value: 5, minOrderAmount: 20 });

    await addToCart(user.token, book.id, 1); // subtotal = 30

    const res = await request(app)
      .post("/api/cart/coupon")
      .set("Authorization", `Bearer ${user.token}`)
      .send({ code: "FIXED5" });

    expect(res.status).toBe(200);
    expect(res.body.subtotal).toBeCloseTo(30, 2);
    expect(res.body.discountAmount).toBeCloseTo(5, 2);
    expect(res.body.finalTotal).toBeCloseTo(25, 2);
  });

  it("fixed coupon discount is capped at the subtotal", async () => {
    const user = await seedUser();
    const book = seedBook({ price: 3, stock: 5 });
    seedCoupon({ code: "BIG50", type: "fixed", value: 50 }); // $50 off on $3 cart

    await addToCart(user.token, book.id, 1); // subtotal = 3

    const res = await request(app)
      .post("/api/cart/coupon")
      .set("Authorization", `Bearer ${user.token}`)
      .send({ code: "BIG50" });

    expect(res.status).toBe(200);
    expect(res.body.discountAmount).toBeCloseTo(3, 2); // capped at subtotal
    expect(res.body.finalTotal).toBeCloseTo(0, 2);
  });

  it("is case-insensitive when applying a coupon code", async () => {
    const user = await seedUser();
    const book = seedBook({ price: 10, stock: 5 });
    seedCoupon({ code: "UPPER" });

    await addToCart(user.token, book.id, 1);

    const res = await request(app)
      .post("/api/cart/coupon")
      .set("Authorization", `Bearer ${user.token}`)
      .send({ code: "upper" });

    expect(res.status).toBe(200);
    expect(res.body.coupon.code).toBe("UPPER");
  });

  it("returns 400 when the cart is empty", async () => {
    const user = await seedUser();
    seedCoupon({ code: "EMPTYCART" });

    const res = await request(app)
      .post("/api/cart/coupon")
      .set("Authorization", `Bearer ${user.token}`)
      .send({ code: "EMPTYCART" });

    expect(res.status).toBe(400);
    expect(res.body.error).toMatch(/empty/i);
  });

  it("returns 400 for an unknown coupon code", async () => {
    const user = await seedUser();
    const book = seedBook({ stock: 5 });

    await addToCart(user.token, book.id, 1);

    const res = await request(app)
      .post("/api/cart/coupon")
      .set("Authorization", `Bearer ${user.token}`)
      .send({ code: "NOSUCHCODE" });

    expect(res.status).toBe(400);
    expect(res.body.error).toMatch(/not found/i);
  });

  it("returns 400 when the coupon is inactive", async () => {
    const user = await seedUser();
    const book = seedBook({ stock: 5 });
    seedCoupon({ code: "DISABLED", isActive: false });

    await addToCart(user.token, book.id, 1);

    const res = await request(app)
      .post("/api/cart/coupon")
      .set("Authorization", `Bearer ${user.token}`)
      .send({ code: "DISABLED" });

    expect(res.status).toBe(400);
    expect(res.body.error).toMatch(/no longer active/i);
  });

  it("returns 400 when the coupon has expired", async () => {
    const user = await seedUser();
    const book = seedBook({ stock: 5 });
    seedCoupon({
      code: "EXPIRED",
      expiresAt: new Date(Date.now() - 1000 * 60 * 60), // 1 hour ago
    });

    await addToCart(user.token, book.id, 1);

    const res = await request(app)
      .post("/api/cart/coupon")
      .set("Authorization", `Bearer ${user.token}`)
      .send({ code: "EXPIRED" });

    expect(res.status).toBe(400);
    expect(res.body.error).toMatch(/expired/i);
  });

  it("returns 400 when the minimum order amount is not met", async () => {
    const user = await seedUser();
    const book = seedBook({ price: 5, stock: 5 });
    seedCoupon({ code: "MINORDER", minOrderAmount: 50 });

    await addToCart(user.token, book.id, 1); // subtotal = 5, below minimum

    const res = await request(app)
      .post("/api/cart/coupon")
      .set("Authorization", `Bearer ${user.token}`)
      .send({ code: "MINORDER" });

    expect(res.status).toBe(400);
    expect(res.body.error).toMatch(/minimum order amount/i);
  });

  it("returns 400 when the usage limit has been reached", async () => {
    const user = await seedUser();
    const book = seedBook({ stock: 5 });
    seedCoupon({ code: "MAXED", maxUses: 3, usedCount: 3 });

    await addToCart(user.token, book.id, 1);

    const res = await request(app)
      .post("/api/cart/coupon")
      .set("Authorization", `Bearer ${user.token}`)
      .send({ code: "MAXED" });

    expect(res.status).toBe(400);
    expect(res.body.error).toMatch(/usage limit/i);
  });

  it("returns 400 for missing code field", async () => {
    const user = await seedUser();
    const book = seedBook({ stock: 5 });

    await addToCart(user.token, book.id, 1);

    const res = await request(app)
      .post("/api/cart/coupon")
      .set("Authorization", `Bearer ${user.token}`)
      .send({});

    expect(res.status).toBe(400);
  });

  it("returns 401 when unauthenticated", async () => {
    const res = await request(app)
      .post("/api/cart/coupon")
      .send({ code: "ANYCODE" });
    expect(res.status).toBe(401);
  });
});

// ── DELETE /api/cart/coupon ────────────────────────────────────────────────────

describe("DELETE /api/cart/coupon", () => {
  it("removes an applied coupon from the cart", async () => {
    const user = await seedUser();
    const book = seedBook({ price: 20, stock: 5 });
    seedCoupon({ code: "REMOVE10" });

    await addToCart(user.token, book.id, 1);

    // Apply the coupon first
    await request(app)
      .post("/api/cart/coupon")
      .set("Authorization", `Bearer ${user.token}`)
      .send({ code: "REMOVE10" });

    // Now remove it
    const res = await request(app)
      .delete("/api/cart/coupon")
      .set("Authorization", `Bearer ${user.token}`);

    expect(res.status).toBe(200);
    expect(res.body.coupon).toBeNull();
    expect(res.body.discountAmount).toBe(0);
    expect(res.body.finalTotal).toBeCloseTo(res.body.subtotal, 2);
  });

  it("returns 404 when no cart exists", async () => {
    const user = await seedUser();

    const res = await request(app)
      .delete("/api/cart/coupon")
      .set("Authorization", `Bearer ${user.token}`);

    expect(res.status).toBe(404);
  });

  it("returns 401 when unauthenticated", async () => {
    const res = await request(app).delete("/api/cart/coupon");
    expect(res.status).toBe(401);
  });
});

// ── Order coupon integration ───────────────────────────────────────────────────

describe("POST /api/orders (with coupon)", () => {
  it("applies a percentage discount when placing an order", async () => {
    const user = await seedUser();
    const book = seedBook({ price: 50, stock: 10 });
    seedCoupon({ code: "ORDER20", type: "percentage", value: 20 });
    fundAccount(user.id, 1000);

    await addToCart(user.token, book.id, 2); // subtotal = 100

    await request(app)
      .post("/api/cart/coupon")
      .set("Authorization", `Bearer ${user.token}`)
      .send({ code: "ORDER20" });

    const res = await request(app)
      .post("/api/orders")
      .set("Authorization", `Bearer ${user.token}`);

    expect(res.status).toBe(201);
    expect(res.body.subtotal).toBeCloseTo(100, 2);
    expect(res.body.discountAmount).toBeCloseTo(20, 2); // 20% of 100
    expect(res.body.totalAmount).toBeCloseTo(80, 2);
    expect(res.body.couponCode).toBe("ORDER20");
  });

  it("applies a fixed discount when placing an order", async () => {
    const user = await seedUser();
    const book = seedBook({ price: 40, stock: 5 });
    seedCoupon({ code: "FIXED10", type: "fixed", value: 10 });
    fundAccount(user.id, 1000);

    await addToCart(user.token, book.id, 1); // subtotal = 40

    await request(app)
      .post("/api/cart/coupon")
      .set("Authorization", `Bearer ${user.token}`)
      .send({ code: "FIXED10" });

    const res = await request(app)
      .post("/api/orders")
      .set("Authorization", `Bearer ${user.token}`);

    expect(res.status).toBe(201);
    expect(res.body.subtotal).toBeCloseTo(40, 2);
    expect(res.body.discountAmount).toBeCloseTo(10, 2);
    expect(res.body.totalAmount).toBeCloseTo(30, 2);
  });

  it("increments the coupon usedCount after order is placed", async () => {
    const user = await seedUser();
    const book = seedBook({ price: 20, stock: 10 });
    const coupon = seedCoupon({ code: "USECOUNT", usedCount: 0 });
    fundAccount(user.id, 1000);

    await addToCart(user.token, book.id, 1);

    await request(app)
      .post("/api/cart/coupon")
      .set("Authorization", `Bearer ${user.token}`)
      .send({ code: "USECOUNT" });

    await request(app)
      .post("/api/orders")
      .set("Authorization", `Bearer ${user.token}`);

    const updated = couponsStore.get(coupon.id)!;
    expect(updated.usedCount).toBe(1);
  });

  it("places order without discount when coupon becomes invalid at checkout", async () => {
    const user = await seedUser();
    const book = seedBook({ price: 20, stock: 10 });
    const coupon = seedCoupon({ code: "GOESAWAY" });
    fundAccount(user.id, 1000);

    await addToCart(user.token, book.id, 1);

    await request(app)
      .post("/api/cart/coupon")
      .set("Authorization", `Bearer ${user.token}`)
      .send({ code: "GOESAWAY" });

    // Deactivate the coupon between apply and checkout
    const stored = couponsStore.get(coupon.id)!;
    couponsStore.set(coupon.id, { ...stored, isActive: false });

    const res = await request(app)
      .post("/api/orders")
      .set("Authorization", `Bearer ${user.token}`);

    expect(res.status).toBe(201);
    expect(res.body.discountAmount).toBe(0);
    expect(res.body.totalAmount).toBeCloseTo(res.body.subtotal, 2);
  });

  it("places order without discount when no coupon is applied", async () => {
    const user = await seedUser();
    const book = seedBook({ price: 15, stock: 5 });
    fundAccount(user.id, 1000);

    await addToCart(user.token, book.id, 2); // subtotal = 30

    const res = await request(app)
      .post("/api/orders")
      .set("Authorization", `Bearer ${user.token}`);

    expect(res.status).toBe(201);
    expect(res.body.subtotal).toBeCloseTo(30, 2);
    expect(res.body.discountAmount).toBe(0);
    expect(res.body.totalAmount).toBeCloseTo(30, 2);
  });
});
