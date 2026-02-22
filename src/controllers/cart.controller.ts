import { Request, Response } from "express";
import { AddToCartSchema, ApplyCouponSchema, UpdateCartItemSchema, Cart } from "../types";
import { booksStore, cartsStore, couponCodeIndex, couponsStore } from "../store";
import { computeDiscount, validateCoupon } from "./admin.controller";

function getOrCreateCart(userId: string): Cart {
  const existing = cartsStore.get(userId);
  if (existing) return existing;
  return { userId, items: [], updatedAt: new Date() };
}

function enrichCart(cart: Cart) {
  const subtotal = Math.round(
    cart.items.reduce((s, i) => s + i.priceAtAdd * i.quantity, 0) * 100
  ) / 100;

  // Resolve applied coupon
  let discountAmount = 0;
  let couponDetails: object | null = null;
  if (cart.couponCode) {
    const couponId = couponCodeIndex.get(cart.couponCode);
    const coupon = couponId ? couponsStore.get(couponId) : undefined;
    if (coupon && coupon.isActive) {
      discountAmount = computeDiscount(coupon, subtotal);
      couponDetails = {
        code: coupon.code,
        type: coupon.type,
        value: coupon.value,
        description: coupon.description,
        discountAmount,
      };
    }
  }

  const finalTotal = Math.max(0, Math.round((subtotal - discountAmount) * 100) / 100);

  return {
    ...cart,
    items: cart.items.map((item) => {
      const book = booksStore.get(item.bookId);
      return { ...item, title: book?.title ?? "Unknown", author: book?.author ?? "" };
    }),
    subtotal,
    discountAmount,
    finalTotal,
    coupon: couponDetails,
  };
}

export function getCart(req: Request, res: Response): void {
  const cart = getOrCreateCart(req.user!.userId);
  res.json(enrichCart(cart));
}

export function addItem(req: Request, res: Response): void {
  const result = AddToCartSchema.safeParse(req.body);
  if (!result.success) {
    res.status(400).json({ error: result.error.issues });
    return;
  }

  const { bookId, quantity } = result.data;
  const book = booksStore.get(bookId);
  if (!book) {
    res.status(404).json({ error: "Book not found" });
    return;
  }

  const cart = getOrCreateCart(req.user!.userId);
  const existingItem = cart.items.find((i) => i.bookId === bookId);
  const newQty = (existingItem?.quantity ?? 0) + quantity;

  if (newQty > book.stock) {
    res.status(400).json({ error: `Insufficient stock. Available: ${book.stock}` });
    return;
  }

  if (existingItem) {
    existingItem.quantity = newQty;
  } else {
    cart.items.push({ bookId, quantity, priceAtAdd: book.price });
  }

  cart.updatedAt = new Date();
  cartsStore.set(cart.userId, cart);

  res.json(enrichCart(cart));
}

export function updateItem(req: Request, res: Response): void {
  const result = UpdateCartItemSchema.safeParse(req.body);
  if (!result.success) {
    res.status(400).json({ error: result.error.issues });
    return;
  }

  const { quantity } = result.data;
  const bookId = String(req.params["bookId"]);
  const userId = req.user!.userId;

  const cart = cartsStore.get(userId);
  if (!cart) {
    res.status(404).json({ error: "Cart is empty" });
    return;
  }

  const itemIndex = cart.items.findIndex((i) => i.bookId === bookId);
  if (itemIndex === -1) {
    res.status(404).json({ error: "Item not found in cart" });
    return;
  }

  if (quantity === 0) {
    cart.items.splice(itemIndex, 1);
  } else {
    const book = booksStore.get(bookId);
    if (book && quantity > book.stock) {
      res.status(400).json({ error: `Insufficient stock. Available: ${book.stock}` });
      return;
    }
    cart.items[itemIndex]!.quantity = quantity;
  }

  cart.updatedAt = new Date();
  cartsStore.set(userId, cart);

  res.json(enrichCart(cart));
}

export function clearCart(req: Request, res: Response): void {
  cartsStore.delete(req.user!.userId);
  res.status(204).send();
}

export function applyCoupon(req: Request, res: Response): void {
  const result = ApplyCouponSchema.safeParse(req.body);
  if (!result.success) {
    res.status(400).json({ error: result.error.issues });
    return;
  }

  const { code } = result.data;
  const cart = getOrCreateCart(req.user!.userId);

  if (!cart.items.length) {
    res.status(400).json({ error: "Cart is empty — add items before applying a coupon" });
    return;
  }

  // Compute current subtotal to validate min order amount
  const subtotal = Math.round(
    cart.items.reduce((s, i) => s + i.priceAtAdd * i.quantity, 0) * 100
  ) / 100;

  const validation = validateCoupon(code, subtotal);
  if (typeof validation === "string") {
    res.status(400).json({ error: validation });
    return;
  }

  cart.couponCode = code;
  cart.updatedAt = new Date();
  cartsStore.set(cart.userId, cart);

  res.json(enrichCart(cart));
}

export function removeCoupon(req: Request, res: Response): void {
  const cart = cartsStore.get(req.user!.userId);
  if (!cart) {
    res.status(404).json({ error: "Cart is empty" });
    return;
  }

  delete cart.couponCode;
  cart.updatedAt = new Date();
  cartsStore.set(cart.userId, cart);

  res.json(enrichCart(cart));
}
