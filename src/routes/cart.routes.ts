import { Router } from "express";
import { getCart, addItem, updateItem, clearCart, applyCoupon, removeCoupon } from "../controllers/cart.controller";
import { authenticate } from "../middleware/auth";

export const cartRouter = Router();

cartRouter.use(authenticate);

cartRouter.get("/", getCart);
cartRouter.post("/items", addItem);
cartRouter.patch("/items/:bookId", updateItem);
cartRouter.delete("/", clearCart);

// Coupon routes
cartRouter.post("/coupon", applyCoupon);
cartRouter.delete("/coupon", removeCoupon);
