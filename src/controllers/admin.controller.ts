import { Request, Response } from "express";
import { v4 as uuidv4 } from "uuid";
import {
  Account,
  AdjustBalanceSchema,
  Coupon,
  CreateCouponSchema,
  Transaction,
  UpdateOrderStatusSchema,
} from "../types";
import {
  accountsStore,
  couponCodeIndex,
  couponsStore,
  ordersStore,
  transactionsStore,
  userOrders,
  usersStore,
  userTransactions,
} from "../store";

// ─── Helpers ──────────────────────────────────────────────────────────────────

function getOrCreateAccount(userId: string): Account {
  const existing = accountsStore.get(userId);
  if (existing) return existing;
  return { userId, balance: 0, updatedAt: new Date() };
}

function recordTransaction(
  data: Omit<Transaction, "id" | "createdAt">
): Transaction {
  const tx: Transaction = { ...data, id: uuidv4(), createdAt: new Date() };
  transactionsStore.set(tx.id, tx);
  const list = userTransactions.get(tx.userId) ?? [];
  userTransactions.set(tx.userId, [...list, tx.id]);
  return tx;
}

// Export helpers so orders.controller / cart.controller can use them
export { getOrCreateAccount, recordTransaction };

// ─── Coupon helpers ───────────────────────────────────────────────────────────

/** Compute discount amount for a coupon given a subtotal. */
export function computeDiscount(coupon: Coupon, subtotal: number): number {
  if (coupon.type === "percentage") {
    return Math.round(subtotal * (coupon.value / 100) * 100) / 100;
  }
  // fixed: discount cannot exceed the subtotal
  return Math.min(Math.round(coupon.value * 100) / 100, subtotal);
}

/** Look up a coupon by code and validate it is usable for a given subtotal.
 *  Returns the coupon if valid, or an error string. */
export function validateCoupon(
  code: string,
  subtotal: number
): Coupon | string {
  const couponId = couponCodeIndex.get(code);
  if (!couponId) return "Coupon code not found";
  const coupon = couponsStore.get(couponId);
  if (!coupon) return "Coupon code not found";
  if (!coupon.isActive) return "Coupon is no longer active";
  if (coupon.expiresAt && coupon.expiresAt < new Date()) return "Coupon has expired";
  if (coupon.maxUses !== null && coupon.usedCount >= coupon.maxUses) return "Coupon usage limit reached";
  if (subtotal < coupon.minOrderAmount)
    return `Minimum order amount for this coupon is $${coupon.minOrderAmount.toFixed(2)}`;
  return coupon;
}

// ─── Coupon management ────────────────────────────────────────────────────────

export function createCoupon(req: Request, res: Response): void {
  const result = CreateCouponSchema.safeParse(req.body);
  if (!result.success) {
    res.status(400).json({ error: result.error.issues });
    return;
  }

  const data = result.data;

  // Enforce unique code
  if (couponCodeIndex.has(data.code)) {
    res.status(409).json({ error: `Coupon code "${data.code}" already exists` });
    return;
  }

  // Validate percentage range
  if (data.type === "percentage" && data.value > 100) {
    res.status(400).json({ error: "Percentage discount cannot exceed 100%" });
    return;
  }

  const coupon: Coupon = {
    id: uuidv4(),
    code: data.code,
    type: data.type,
    value: data.value,
    description: data.description,
    minOrderAmount: data.minOrderAmount,
    maxUses: data.maxUses,
    usedCount: 0,
    isActive: true,
    expiresAt: data.expiresAt,
    createdAt: new Date(),
  };

  couponsStore.set(coupon.id, coupon);
  couponCodeIndex.set(coupon.code, coupon.id);

  res.status(201).json(coupon);
}

export function listCoupons(_req: Request, res: Response): void {
  const coupons = Array.from(couponsStore.values()).sort(
    (a, b) => b.createdAt.getTime() - a.createdAt.getTime()
  );
  res.json(coupons);
}

export function deactivateCoupon(req: Request, res: Response): void {
  const code = String(req.params["code"]).toUpperCase();
  const couponId = couponCodeIndex.get(code);
  if (!couponId) {
    res.status(404).json({ error: "Coupon not found" });
    return;
  }
  const coupon = couponsStore.get(couponId)!;
  coupon.isActive = false;
  couponsStore.set(couponId, coupon);
  res.json(coupon);
}

export function activateCoupon(req: Request, res: Response): void {
  const code = String(req.params["code"]).toUpperCase();
  const couponId = couponCodeIndex.get(code);
  if (!couponId) {
    res.status(404).json({ error: "Coupon not found" });
    return;
  }
  const coupon = couponsStore.get(couponId)!;
  coupon.isActive = true;
  couponsStore.set(couponId, coupon);
  res.json(coupon);
}

// ─── Customer management ──────────────────────────────────────────────────────

export function listCustomers(_req: Request, res: Response): void {
  const customers = Array.from(usersStore.values())
    .filter((u) => u.role === "customer")
    .map((u) => {
      const account = getOrCreateAccount(u.id);
      const orderCount = (userOrders.get(u.id) ?? []).length;
      return {
        id: u.id,
        name: u.name,
        email: u.email,
        balance: account.balance,
        orderCount,
        createdAt: u.createdAt,
      };
    })
    .sort((a, b) => a.name.localeCompare(b.name));

  res.json(customers);
}

export function getCustomer(req: Request, res: Response): void {
  const userId = String(req.params["userId"]);
  const user = usersStore.get(userId);
  if (!user || user.role !== "customer") {
    res.status(404).json({ error: "Customer not found" });
    return;
  }

  const account = getOrCreateAccount(userId);
  const txIds = userTransactions.get(userId) ?? [];
  const transactions = txIds
    .map((id) => transactionsStore.get(id))
    .filter((t): t is Transaction => t !== undefined)
    .sort((a, b) => b.createdAt.getTime() - a.createdAt.getTime())
    .slice(0, 50);

  const orderIds = userOrders.get(userId) ?? [];
  const orders = orderIds
    .map((id) => ordersStore.get(id))
    .filter(Boolean)
    .sort((a, b) => b!.createdAt.getTime() - a!.createdAt.getTime());

  res.json({
    id: user.id,
    name: user.name,
    email: user.email,
    balance: account.balance,
    transactions,
    orders,
  });
}

export function creditCustomer(req: Request, res: Response): void {
  const userId = String(req.params["userId"]);
  const user = usersStore.get(userId);
  if (!user || user.role !== "customer") {
    res.status(404).json({ error: "Customer not found" });
    return;
  }

  const result = AdjustBalanceSchema.safeParse(req.body);
  if (!result.success) {
    res.status(400).json({ error: result.error.issues });
    return;
  }

  const { amount, description } = result.data;
  const account = getOrCreateAccount(userId);
  account.balance = Math.round((account.balance + amount) * 100) / 100;
  account.updatedAt = new Date();
  accountsStore.set(userId, account);

  const tx = recordTransaction({
    userId,
    type: "credit",
    amount,
    description,
    balanceAfter: account.balance,
  });

  res.json({ balance: account.balance, transaction: tx });
}

export function debitCustomer(req: Request, res: Response): void {
  const userId = String(req.params["userId"]);
  const user = usersStore.get(userId);
  if (!user || user.role !== "customer") {
    res.status(404).json({ error: "Customer not found" });
    return;
  }

  const result = AdjustBalanceSchema.safeParse(req.body);
  if (!result.success) {
    res.status(400).json({ error: result.error.issues });
    return;
  }

  const { amount, description } = result.data;
  const account = getOrCreateAccount(userId);

  if (account.balance < amount) {
    res.status(400).json({
      error: `Insufficient balance. Current balance: $${account.balance.toFixed(2)}`,
    });
    return;
  }

  account.balance = Math.round((account.balance - amount) * 100) / 100;
  account.updatedAt = new Date();
  accountsStore.set(userId, account);

  const tx = recordTransaction({
    userId,
    type: "debit",
    amount,
    description,
    balanceAfter: account.balance,
  });

  res.json({ balance: account.balance, transaction: tx });
}

// ─── Order management ─────────────────────────────────────────────────────────

export function listAllOrders(req: Request, res: Response): void {
  const { status } = req.query as { status?: string };
  let orders = Array.from(ordersStore.values());

  if (status) {
    orders = orders.filter((o) => o.status === status);
  }

  orders.sort((a, b) => b.createdAt.getTime() - a.createdAt.getTime());

  // Enrich with customer name
  const enriched = orders.map((o) => ({
    ...o,
    customerName: usersStore.get(o.userId)?.name ?? "Unknown",
  }));

  res.json(enriched);
}

export function updateOrderStatus(req: Request, res: Response): void {
  const orderId = String(req.params["orderId"]);
  const order = ordersStore.get(orderId);
  if (!order) {
    res.status(404).json({ error: "Order not found" });
    return;
  }

  const result = UpdateOrderStatusSchema.safeParse(req.body);
  if (!result.success) {
    res.status(400).json({ error: result.error.issues });
    return;
  }

  const { status } = result.data;

  // Enforce forward-only progression
  const progression: Record<string, string> = {
    pending: "confirmed",
    confirmed: "shipped",
  };
  if (progression[order.status] !== status) {
    res.status(400).json({
      error: `Cannot advance from "${order.status}" to "${status}"`,
    });
    return;
  }

  order.status = status;
  order.updatedAt = new Date();
  ordersStore.set(orderId, order);

  res.json(order);
}

export function refundOrder(req: Request, res: Response): void {
  const orderId = String(req.params["orderId"]);
  const order = ordersStore.get(orderId);
  if (!order) {
    res.status(404).json({ error: "Order not found" });
    return;
  }

  if (order.status === "cancelled") {
    res.status(400).json({ error: "Order is already cancelled" });
    return;
  }
  if (order.status === "shipped") {
    res.status(400).json({ error: "Cannot refund a shipped order" });
    return;
  }

  // Credit the wallet
  const account = getOrCreateAccount(order.userId);
  account.balance = Math.round((account.balance + order.totalAmount) * 100) / 100;
  account.updatedAt = new Date();
  accountsStore.set(order.userId, account);

  recordTransaction({
    userId: order.userId,
    type: "refund",
    amount: order.totalAmount,
    description: `Refund for order ${orderId.slice(0, 8)}`,
    orderId,
    balanceAfter: account.balance,
  });

  order.status = "cancelled";
  order.updatedAt = new Date();
  ordersStore.set(orderId, order);

  res.json({ order, balance: account.balance });
}
