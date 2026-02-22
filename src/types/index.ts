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
  totalAmount: number;
  status: OrderStatus;
  createdAt: Date;
  updatedAt: Date;
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
