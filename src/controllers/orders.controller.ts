import { Request, Response } from "express";
import { v4 as uuidv4 } from "uuid";
import { Order, OrderItem } from "../types";
import { booksStore, cartsStore, ordersStore, userOrders } from "../store";

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

  // All checks passed — mutate synchronously (single-threaded, no await between check and mutate)
  const orderItems: OrderItem[] = [];
  let totalAmount = 0;

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
    totalAmount += item.priceAtAdd * item.quantity;
  }

  const now = new Date();
  const order: Order = {
    id: uuidv4(),
    userId,
    items: orderItems,
    totalAmount: Math.round(totalAmount * 100) / 100,
    status: "pending",
    createdAt: now,
    updatedAt: now,
  };

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
