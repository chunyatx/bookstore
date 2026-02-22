import { Request, Response } from "express";
import bcrypt from "bcryptjs";
import jwt from "jsonwebtoken";
import { v4 as uuidv4 } from "uuid";
import { RegisterSchema, LoginSchema, User } from "../types";
import { usersStore, emailIndex } from "../store";

const JWT_SECRET = process.env["JWT_SECRET"] ?? "change-me-in-production";

function safeUser(user: User) {
  const { passwordHash: _omit, ...safe } = user;
  return safe;
}

export async function register(req: Request, res: Response): Promise<void> {
  const result = RegisterSchema.safeParse(req.body);
  if (!result.success) {
    res.status(400).json({ error: result.error.issues });
    return;
  }

  const { email, password, name } = result.data;

  if (emailIndex.has(email)) {
    res.status(409).json({ error: "Email already registered" });
    return;
  }

  const passwordHash = await bcrypt.hash(password, 12);
  const user: User = {
    id: uuidv4(),
    email,
    passwordHash,
    name,
    role: "customer",
    createdAt: new Date(),
  };

  usersStore.set(user.id, user);
  emailIndex.set(email, user.id);

  res.status(201).json(safeUser(user));
}

export async function login(req: Request, res: Response): Promise<void> {
  const result = LoginSchema.safeParse(req.body);
  if (!result.success) {
    res.status(400).json({ error: result.error.issues });
    return;
  }

  const { email, password } = result.data;

  const userId = emailIndex.get(email);
  const user = userId ? usersStore.get(userId) : undefined;

  if (!user || !(await bcrypt.compare(password, user.passwordHash))) {
    res.status(401).json({ error: "Invalid email or password" });
    return;
  }

  const token = jwt.sign(
    { userId: user.id, email: user.email, role: user.role },
    JWT_SECRET,
    { expiresIn: "7d" }
  );

  res.json({ token, user: safeUser(user) });
}

export function getMe(req: Request, res: Response): void {
  const user = usersStore.get(req.user!.userId);
  if (!user) {
    res.status(404).json({ error: "User not found" });
    return;
  }
  res.json(safeUser(user));
}
