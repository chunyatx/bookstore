import { Request, Response } from "express";
import { v4 as uuidv4 } from "uuid";
import { CreateBookSchema, UpdateBookSchema, BookQuerySchema, Book } from "../types";
import { booksStore, isbnIndex } from "../store";

export function listBooks(req: Request, res: Response): void {
  const result = BookQuerySchema.safeParse(req.query);
  if (!result.success) {
    res.status(400).json({ error: result.error.issues });
    return;
  }

  const { title, author, genre, minPrice, maxPrice, page, limit } = result.data;

  let books = Array.from(booksStore.values());

  if (title) {
    const q = title.toLowerCase();
    books = books.filter((b) => b.title.toLowerCase().includes(q));
  }
  if (author) {
    const q = author.toLowerCase();
    books = books.filter((b) => b.author.toLowerCase().includes(q));
  }
  if (genre) {
    const q = genre.toLowerCase();
    books = books.filter((b) => b.genre.toLowerCase().includes(q));
  }
  if (minPrice !== undefined) {
    books = books.filter((b) => b.price >= minPrice);
  }
  if (maxPrice !== undefined) {
    books = books.filter((b) => b.price <= maxPrice);
  }

  const total = books.length;
  const data = books.slice((page - 1) * limit, page * limit);

  res.json({ data, total, page, limit });
}

export function getBook(req: Request, res: Response): void {
  const book = booksStore.get(String(req.params["id"]));
  if (!book) {
    res.status(404).json({ error: "Book not found" });
    return;
  }
  res.json(book);
}

export function createBook(req: Request, res: Response): void {
  const result = CreateBookSchema.safeParse(req.body);
  if (!result.success) {
    res.status(400).json({ error: result.error.issues });
    return;
  }

  const data = result.data;

  if (isbnIndex.has(data.isbn)) {
    res.status(409).json({ error: "A book with this ISBN already exists" });
    return;
  }

  const now = new Date();
  const book: Book = {
    id: uuidv4(),
    ...data,
    createdAt: now,
    updatedAt: now,
  };

  booksStore.set(book.id, book);
  isbnIndex.set(book.isbn, book.id);

  res.status(201).json(book);
}

export function updateBook(req: Request, res: Response): void {
  const id = String(req.params["id"]);
  const existing = booksStore.get(id);
  if (!existing) {
    res.status(404).json({ error: "Book not found" });
    return;
  }

  const result = UpdateBookSchema.safeParse(req.body);
  if (!result.success) {
    res.status(400).json({ error: result.error.issues });
    return;
  }

  const updates = result.data;

  // ISBN uniqueness check on change
  if (updates.isbn && updates.isbn !== existing.isbn) {
    if (isbnIndex.has(updates.isbn)) {
      res.status(409).json({ error: "A book with this ISBN already exists" });
      return;
    }
    isbnIndex.delete(existing.isbn);
    isbnIndex.set(updates.isbn, id);
  }

  const updated: Book = { ...existing, ...updates, updatedAt: new Date() };
  booksStore.set(id, updated);

  res.json(updated);
}

export function deleteBook(req: Request, res: Response): void {
  const id = String(req.params["id"]);
  const book = booksStore.get(id);
  if (!book) {
    res.status(404).json({ error: "Book not found" });
    return;
  }

  booksStore.delete(id);
  isbnIndex.delete(book.isbn);

  res.status(204).send();
}
