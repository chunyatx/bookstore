import { Router } from "express";
import {
  activateCoupon,
  creditCustomer,
  createCoupon,
  deactivateCoupon,
  debitCustomer,
  getCustomer,
  listAllOrders,
  listCoupons,
  listCustomers,
  refundOrder,
  updateOrderStatus,
} from "../controllers/admin.controller";
import { authenticate, requireRole } from "../middleware/auth";

export const adminRouter = Router();

adminRouter.use(authenticate, requireRole("admin"));

// Customer management
adminRouter.get("/customers",                       listCustomers);
adminRouter.get("/customers/:userId",               getCustomer);
adminRouter.post("/customers/:userId/credit",       creditCustomer);
adminRouter.post("/customers/:userId/debit",        debitCustomer);

// Order management
adminRouter.get("/orders",                          listAllOrders);
adminRouter.patch("/orders/:orderId/status",        updateOrderStatus);
adminRouter.post("/orders/:orderId/refund",         refundOrder);

// Coupon management
adminRouter.get("/coupons",                         listCoupons);
adminRouter.post("/coupons",                        createCoupon);
adminRouter.patch("/coupons/:code/deactivate",      deactivateCoupon);
adminRouter.patch("/coupons/:code/activate",        activateCoupon);
