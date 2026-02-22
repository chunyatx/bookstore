import { Router } from "express";
import {
  listBooks,
  getBook,
  createBook,
  updateBook,
  deleteBook,
} from "../controllers/books.controller";
import { authenticate, requireRole } from "../middleware/auth";

export const booksRouter = Router();

booksRouter.get("/", listBooks);
booksRouter.get("/:id", getBook);
booksRouter.post("/", authenticate, requireRole("admin"), createBook);
booksRouter.patch("/:id", authenticate, requireRole("admin"), updateBook);
booksRouter.delete("/:id", authenticate, requireRole("admin"), deleteBook);
