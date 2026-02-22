import { Request, Response } from "express";
import { v4 as uuidv4 } from "uuid";
import { Order, OrderItem } from "../types";
import { accountsStore, booksStore, cartsStore, couponsStore, couponCodeIndex, ordersStore, userOrders } from "../store";
import { computeDiscount, getOrCreateAccount, recordTransaction, validateCoupon } from "./admin.controller";

export function placeOrder(req: Request, res: Response): void {
  const userId = req.user!.userId;
  const cart = cartsStore.get(userId);

  if (!cart || cart.items.length === 0) {
    res.status(400).json({ error: "Cart is empty" });
    return;
  }

  // Validate all items first (before any mutation)
  for (const item of cart.items) {
    const book = booksStore.get(item.bookId);
    if (!book) {
      res.status(400).json({ error: `Book ${item.bookId} no longer exists` });
      return;
    }
    if (book.stock < item.quantity) {
      res.status(400).json({
        error: `Insufficient stock for "${book.title}". Available: ${book.stock}`,
      });
      return;
    }
  }

  // Calculate subtotal before any mutation
  let subtotal = 0;
  for (const item of cart.items) {
    subtotal += item.priceAtAdd * item.quantity;
  }
  subtotal = Math.round(subtotal * 100) / 100;

  // Re-validate coupon (cart may have changed since it was applied)
  let discountAmount = 0;
  let appliedCouponCode: string | undefined;
  if (cart.couponCode) {
    const validation = validateCoupon(cart.couponCode, subtotal);
    if (typeof validation === "string") {
      // Coupon is no longer valid — clear it silently and proceed without discount
      delete cart.couponCode;
    } else {
      discountAmount = computeDiscount(validation, subtotal);
      appliedCouponCode = validation.code;
    }
  }

  const totalAmount = Math.max(0, Math.round((subtotal - discountAmount) * 100) / 100);

  const account = getOrCreateAccount(userId);
  if (account.balance < totalAmount) {
    res.status(400).json({
      error: `Insufficient wallet balance. Balance: $${account.balance.toFixed(2)}, Required: $${totalAmount.toFixed(2)}`,
    });
    return;
  }

  // All checks passed — mutate synchronously (single-threaded, no await between check and mutate)
  const orderItems: OrderItem[] = [];

  for (const item of cart.items) {
    const book = booksStore.get(item.bookId)!;
    book.stock -= item.quantity;
    booksStore.set(book.id, book);

    orderItems.push({
      bookId: book.id,
      title: book.title,
      quantity: item.quantity,
      priceAtOrder: item.priceAtAdd,
    });
  }

  const now = new Date();
  const order: Order = {
    id: uuidv4(),
    userId,
    items: orderItems,
    subtotal,
    discountAmount,
    totalAmount,
    couponCode: appliedCouponCode,
    status: "pending",
    createdAt: now,
    updatedAt: now,
  };

  // Increment coupon usage count
  if (appliedCouponCode) {
    const couponId = couponCodeIndex.get(appliedCouponCode);
    if (couponId) {
      const coupon = couponsStore.get(couponId);
      if (coupon) {
        coupon.usedCount += 1;
        couponsStore.set(couponId, coupon);
      }
    }
  }

  // Deduct wallet balance
  account.balance = Math.round((account.balance - totalAmount) * 100) / 100;
  account.updatedAt = now;
  accountsStore.set(userId, account);

  const txDescription = appliedCouponCode
    ? `Payment for order ${order.id.slice(0, 8)} (coupon: ${appliedCouponCode})`
    : `Payment for order ${order.id.slice(0, 8)}`;

  recordTransaction({
    userId,
    type: "order_payment",
    amount: totalAmount,
    description: txDescription,
    orderId: order.id,
    balanceAfter: account.balance,
  });

  ordersStore.set(order.id, order);
  const existing = userOrders.get(userId) ?? [];
  userOrders.set(userId, [...existing, order.id]);
  cartsStore.delete(userId);

  res.status(201).json(order);
}

export function listOrders(req: Request, res: Response): void {
  const userId = req.user!.userId;
  const ids = userOrders.get(userId) ?? [];
  const orders = ids
    .map((id) => ordersStore.get(id))
    .filter((o): o is Order => o !== undefined)
    .sort((a, b) => b.createdAt.getTime() - a.createdAt.getTime());

  res.json(orders);
}

export function getOrder(req: Request, res: Response): void {
  const order = ordersStore.get(String(req.params["id"]));
  if (!order) {
    res.status(404).json({ error: "Order not found" });
    return;
  }

  if (order.userId !== req.user!.userId && req.user!.role !== "admin") {
    res.status(403).json({ error: "Forbidden" });
    return;
  }

  res.json(order);
}

export function cancelOrder(req: Request, res: Response): void {
  const order = ordersStore.get(String(req.params["id"]));
  if (!order) {
    res.status(404).json({ error: "Order not found" });
    return;
  }

  if (order.userId !== req.user!.userId && req.user!.role !== "admin") {
    res.status(403).json({ error: "Forbidden" });
    return;
  }

  if (order.status !== "pending") {
    res.status(400).json({ error: `Cannot cancel an order with status "${order.status}"` });
    return;
  }

  // Restore stock
  for (const item of order.items) {
    const book = booksStore.get(item.bookId);
    if (book) {
      book.stock += item.quantity;
      booksStore.set(book.id, book);
    }
  }

  // Refund wallet
  const account = getOrCreateAccount(order.userId);
  account.balance = Math.round((account.balance + order.totalAmount) * 100) / 100;
  account.updatedAt = new Date();
  accountsStore.set(order.userId, account);

  recordTransaction({
    userId: order.userId,
    type: "refund",
    amount: order.totalAmount,
    description: `Refund for order ${order.id.slice(0, 8)}`,
    orderId: order.id,
    balanceAfter: account.balance,
  });

  order.status = "cancelled";
  order.updatedAt = new Date();
  ordersStore.set(order.id, order);

  res.json(order);
}

export function listAllOrders(req: Request, res: Response): void {
  const { status } = req.query as { status?: string };

  let orders = Array.from(ordersStore.values());

  if (status) {
    orders = orders.filter((o) => o.status === status);
  }

  orders.sort((a, b) => b.createdAt.getTime() - a.createdAt.getTime());

  res.json(orders);
}
