import { Router } from "express";
import { Request, Response } from "express";
import { Transaction } from "../types";
import { transactionsStore, userTransactions } from "../store";
import { getOrCreateAccount } from "../controllers/admin.controller";
import { authenticate } from "../middleware/auth";

export const accountRouter = Router();

accountRouter.use(authenticate);

accountRouter.get("/", (req: Request, res: Response): void => {
  const userId = req.user!.userId;
  const account = getOrCreateAccount(userId);

  const txIds = userTransactions.get(userId) ?? [];
  const transactions = txIds
    .map((id) => transactionsStore.get(id))
    .filter((t): t is Transaction => t !== undefined)
    .sort((a, b) => b.createdAt.getTime() - a.createdAt.getTime())
    .slice(0, 20);

  res.json({ balance: account.balance, transactions });
});
