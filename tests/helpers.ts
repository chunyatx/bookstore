import jwt from "jsonwebtoken";
import { v4 as uuidv4 } from "uuid";
import bcrypt from "bcryptjs";
import {
  booksStore,
  usersStore,
  emailIndex,
  isbnIndex,
  cartsStore,
  ordersStore,
  userOrders,
} from "../src/store";
import { Book, User } from "../src/types";

const JWT_SECRET = process.env["JWT_SECRET"] ?? "change-me-in-production";

/** Wipe all in-memory stores between tests. */
export function clearStores(): void {
  booksStore.clear();
  usersStore.clear();
  emailIndex.clear();
  isbnIndex.clear();
  cartsStore.clear();
  ordersStore.clear();
  userOrders.clear();
}

/** Create a signed JWT for a user fixture. */
export function signToken(payload: {
  userId: string;
  email: string;
  role: "customer" | "admin";
}): string {
  return jwt.sign(payload, JWT_SECRET, { expiresIn: "1h" });
}

/** Seed a user directly into the store (bypasses bcrypt for speed). */
export async function seedUser(
  overrides: Partial<User> & { password?: string } = {}
): Promise<User & { token: string }> {
  const id = uuidv4();
  const email = overrides.email ?? `user-${id}@example.com`;
  const password = overrides.password ?? "password123";
  const role = overrides.role ?? "customer";

  const user: User = {
    id,
    email,
    passwordHash: await bcrypt.hash(password, 1), // cost=1 for speed in tests
    name: overrides.name ?? "Test User",
    role,
    createdAt: new Date(),
  };

  usersStore.set(user.id, user);
  emailIndex.set(user.email, user.id);

  const token = signToken({ userId: user.id, email: user.email, role });
  return { ...user, token };
}

/** Seed a book directly into the store. */
export function seedBook(overrides: Partial<Book> = {}): Book {
  const id = uuidv4();
  const isbn = overrides.isbn ?? `978-${Date.now()}-${Math.floor(Math.random() * 1000)}`;
  const now = new Date();
  const book: Book = {
    title: "Test Book",
    author: "Test Author",
    genre: "Fiction",
    price: 9.99,
    stock: 10,
    description: "",
    ...overrides,
    id,   // always use the generated id
    isbn, // always use the generated isbn (overrides.isbn was used above)
    createdAt: now,
    updatedAt: now,
  };

  booksStore.set(book.id, book);
  isbnIndex.set(book.isbn, book.id);
  return book;
}
