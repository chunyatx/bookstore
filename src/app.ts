import path from "path";
import express, { Request, Response, NextFunction } from "express";
import swaggerUi from "swagger-ui-express";
import { swaggerSpec } from "./swagger";
import { authRouter } from "./routes/auth.routes";
import { booksRouter } from "./routes/books.routes";
import { cartRouter } from "./routes/cart.routes";
import { ordersRouter } from "./routes/orders.routes";

export const app = express();

app.use(express.json());
app.use(express.urlencoded({ extended: true }));

app.get("/health", (_req: Request, res: Response) => {
  res.json({ status: "ok" });
});

app.use("/docs", swaggerUi.serve, swaggerUi.setup(swaggerSpec));

app.use("/api/auth", authRouter);
app.use("/api/books", booksRouter);
app.use("/api/cart", cartRouter);
app.use("/api/orders", ordersRouter);

// Serve the frontend — express.static handles index.html automatically
app.use(express.static(path.join(__dirname, "..", "public")));

app.use((err: Error, _req: Request, res: Response, _next: NextFunction) => {
  console.error(err.stack);
  res.status(500).json({ error: "Internal server error" });
});
