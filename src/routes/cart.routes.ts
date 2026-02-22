import { Router } from "express";
import { getCart, addItem, updateItem, clearCart } from "../controllers/cart.controller";
import { authenticate } from "../middleware/auth";

export const cartRouter = Router();

cartRouter.use(authenticate);

cartRouter.get("/", getCart);
cartRouter.post("/items", addItem);
cartRouter.patch("/items/:bookId", updateItem);
cartRouter.delete("/", clearCart);
