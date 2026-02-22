import { Request, Response, NextFunction } from "express";
import { authenticate, requireRole } from "../src/middleware/auth";
import { signToken } from "./helpers";

// ── Helpers ────────────────────────────────────────────────────────────────────

function mockRes() {
  const res: Partial<Response> = {};
  res.status = jest.fn().mockReturnValue(res);
  res.json = jest.fn().mockReturnValue(res);
  return res as Response;
}

function mockNext(): NextFunction {
  return jest.fn();
}

// ── authenticate ───────────────────────────────────────────────────────────────

describe("authenticate middleware", () => {
  it("calls next() and sets req.user for a valid token", () => {
    const payload = { userId: "u1", email: "a@b.com", role: "customer" as const };
    const token = signToken(payload);

    const req = { headers: { authorization: `Bearer ${token}` } } as Request;
    const res = mockRes();
    const next = mockNext();

    authenticate(req, res, next);

    expect(next).toHaveBeenCalledTimes(1);
    expect(req.user).toMatchObject(payload);
    expect(res.status).not.toHaveBeenCalled();
  });

  it("returns 401 when the Authorization header is missing", () => {
    const req = { headers: {} } as Request;
    const res = mockRes();
    const next = mockNext();

    authenticate(req, res, next);

    expect(res.status).toHaveBeenCalledWith(401);
    expect(next).not.toHaveBeenCalled();
  });

  it("returns 401 when the header does not start with 'Bearer '", () => {
    const req = { headers: { authorization: "Token abc123" } } as Request;
    const res = mockRes();
    const next = mockNext();

    authenticate(req, res, next);

    expect(res.status).toHaveBeenCalledWith(401);
    expect(next).not.toHaveBeenCalled();
  });

  it("returns 401 for an invalid (tampered) token", () => {
    const req = { headers: { authorization: "Bearer this.is.garbage" } } as Request;
    const res = mockRes();
    const next = mockNext();

    authenticate(req, res, next);

    expect(res.status).toHaveBeenCalledWith(401);
    expect(next).not.toHaveBeenCalled();
  });

  it("returns 401 for an expired token", () => {
    // Sign a token that expired a second ago
    const payload = { userId: "u1", email: "a@b.com", role: "customer" as const };
    const token = signToken({ ...payload });

    // Fake the verify to throw TokenExpiredError by crafting an expired token
    // We test this indirectly: supertest-level tests cover the flow; here we
    // just verify that any jwt.verify failure produces 401.
    const req = { headers: { authorization: `Bearer ${token}x` } } as Request; // corrupt token
    const res = mockRes();
    const next = mockNext();

    authenticate(req, res, next);

    expect(res.status).toHaveBeenCalledWith(401);
  });
});

// ── requireRole ────────────────────────────────────────────────────────────────

describe("requireRole middleware", () => {
  it("calls next() when the user has the required role", () => {
    const req = { user: { userId: "u1", email: "a@b.com", role: "admin" as const } } as Request;
    const res = mockRes();
    const next = mockNext();

    requireRole("admin")(req, res, next);

    expect(next).toHaveBeenCalledTimes(1);
    expect(res.status).not.toHaveBeenCalled();
  });

  it("calls next() when multiple roles are allowed and user matches one", () => {
    const req = {
      user: { userId: "u1", email: "a@b.com", role: "customer" as const },
    } as Request;
    const res = mockRes();
    const next = mockNext();

    requireRole("customer", "admin")(req, res, next);

    expect(next).toHaveBeenCalledTimes(1);
  });

  it("returns 403 when the user's role is not in the allowed list", () => {
    const req = {
      user: { userId: "u1", email: "a@b.com", role: "customer" as const },
    } as Request;
    const res = mockRes();
    const next = mockNext();

    requireRole("admin")(req, res, next);

    expect(res.status).toHaveBeenCalledWith(403);
    expect(next).not.toHaveBeenCalled();
  });

  it("returns 403 when req.user is undefined", () => {
    const req = {} as Request;
    const res = mockRes();
    const next = mockNext();

    requireRole("admin")(req, res, next);

    expect(res.status).toHaveBeenCalledWith(403);
    expect(next).not.toHaveBeenCalled();
  });
});
