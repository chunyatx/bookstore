import { Router } from "express";
import {
  placeOrder,
  listOrders,
  getOrder,
  cancelOrder,
  listAllOrders,
} from "../controllers/orders.controller";
import { authenticate, requireRole } from "../middleware/auth";

export const ordersRouter = Router();

ordersRouter.use(authenticate);

ordersRouter.post("/", placeOrder);
ordersRouter.get("/", listOrders);
// /admin/all must be registered before /:id to avoid Express treating "admin" as an ID
ordersRouter.get("/admin/all", requireRole("admin"), listAllOrders);
ordersRouter.get("/:id", getOrder);
ordersRouter.patch("/:id/cancel", cancelOrder);
