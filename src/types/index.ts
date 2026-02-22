import { z } from "zod";

// ─── Domain Models ────────────────────────────────────────────────────────────

export interface Book {
  id: string;
  title: string;
  author: string;
  genre: string;
  price: number;
  stock: number;
  description: string;
  isbn: string;
  createdAt: Date;
  updatedAt: Date;
}

export interface User {
  id: string;
  email: string;
  passwordHash: string;
  name: string;
  role: "customer" | "admin";
  createdAt: Date;
}

export interface CartItem {
  bookId: string;
  quantity: number;
  priceAtAdd: number;
}

export interface Cart {
  userId: string;
  items: CartItem[];
  couponCode?: string; // applied coupon code
  updatedAt: Date;
}

export type OrderStatus = "pending" | "confirmed" | "shipped" | "cancelled";

export interface OrderItem {
  bookId: string;
  title: string;
  quantity: number;
  priceAtOrder: number;
}

export interface Order {
  id: string;
  userId: string;
  items: OrderItem[];
  subtotal: number;        // pre-discount total
  discountAmount: number;  // 0 if no coupon
  totalAmount: number;     // subtotal - discountAmount
  couponCode?: string;     // applied coupon code
  status: OrderStatus;
  createdAt: Date;
  updatedAt: Date;
}

// ─── Coupons ──────────────────────────────────────────────────────────────────

export type CouponType = "percentage" | "fixed";

export interface Coupon {
  id: string;
  code: string;           // uppercase, unique
  type: CouponType;
  value: number;          // % amount (0-100) for percentage, dollar amount for fixed
  description: string;
  minOrderAmount: number; // minimum subtotal to use (0 = no minimum)
  maxUses: number | null; // null = unlimited
  usedCount: number;
  isActive: boolean;
  expiresAt: Date | null; // null = no expiry
  createdAt: Date;
}

// ─── Wallet / Accounts ────────────────────────────────────────────────────────

export interface Account {
  userId: string;
  balance: number;    // current wallet balance in USD
  updatedAt: Date;
}

export type TransactionType = "credit" | "debit" | "order_payment" | "refund";

export interface Transaction {
  id: string;
  userId: string;
  type: TransactionType;
  amount: number;        // always positive; type describes direction
  description: string;
  orderId?: string;      // set for order_payment and refund
  balanceAfter: number;  // snapshot of balance after this tx
  createdAt: Date;
}

// ─── JWT Payload ──────────────────────────────────────────────────────────────

export interface JwtPayload {
  userId: string;
  email: string;
  role: "customer" | "admin";
}

// ─── Express Request Augmentation ─────────────────────────────────────────────

declare global {
  // eslint-disable-next-line @typescript-eslint/no-namespace
  namespace Express {
    interface Request {
      user?: JwtPayload;
    }
  }
}

// ─── Zod Schemas ──────────────────────────────────────────────────────────────

export const RegisterSchema = z.object({
  email: z.string().email(),
  password: z.string().min(6, "Password must be at least 6 characters"),
  name: z.string().min(1),
});

export const LoginSchema = z.object({
  email: z.string().email(),
  password: z.string().min(1),
});

export const CreateBookSchema = z.object({
  title: z.string().min(1),
  author: z.string().min(1),
  genre: z.string().min(1),
  price: z.number().positive(),
  stock: z.number().int().nonnegative(),
  description: z.string().default(""),
  isbn: z.string().min(10).max(13),
});

export const UpdateBookSchema = CreateBookSchema.partial();

export const BookQuerySchema = z.object({
  title: z.string().optional(),
  author: z.string().optional(),
  genre: z.string().optional(),
  minPrice: z.coerce.number().positive().optional(),
  maxPrice: z.coerce.number().positive().optional(),
  page: z.coerce.number().int().positive().default(1),
  limit: z.coerce.number().int().positive().max(100).default(20),
});

export const AddToCartSchema = z.object({
  bookId: z.string().uuid(),
  quantity: z.number().int().positive(),
});

export const UpdateCartItemSchema = z.object({
  quantity: z.number().int().nonnegative(),
});

export const CreateCouponSchema = z.object({
  code: z.string().min(3).max(20).transform((v) => v.toUpperCase()),
  type: z.enum(["percentage", "fixed"]),
  value: z.number().positive(),
  description: z.string().min(1),
  minOrderAmount: z.number().nonnegative().default(0),
  maxUses: z.number().int().positive().nullable().default(null),
  expiresAt: z.string().datetime().nullable().default(null)
    .transform((v) => (v ? new Date(v) : null)),
});

export const ApplyCouponSchema = z.object({
  code: z.string().min(1).transform((v) => v.toUpperCase()),
});

export const AdjustBalanceSchema = z.object({
  amount: z.number().positive(),
  description: z.string().min(1),
});

export const UpdateOrderStatusSchema = z.object({
  status: z.enum(["confirmed", "shipped"]),
});
