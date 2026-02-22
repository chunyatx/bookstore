import { Account, Book, Cart, Coupon, Order, Transaction, User } from "../types";

// Singleton Maps — live for the lifetime of the process.
// Node.js caches require() calls so every import gets the same instance.

export const booksStore = new Map<string, Book>();
export const usersStore = new Map<string, User>();
export const emailIndex = new Map<string, string>(); // email  -> userId
export const isbnIndex  = new Map<string, string>(); // isbn   -> bookId
export const cartsStore = new Map<string, Cart>();   // userId -> Cart
export const ordersStore = new Map<string, Order>(); // orderId -> Order
export const userOrders  = new Map<string, string[]>(); // userId -> orderId[]

export const accountsStore     = new Map<string, Account>();      // userId  -> Account
export const transactionsStore = new Map<string, Transaction>();  // txId    -> Transaction
export const userTransactions  = new Map<string, string[]>();     // userId  -> txId[]

export const couponsStore    = new Map<string, Coupon>(); // couponId -> Coupon
export const couponCodeIndex = new Map<string, string>(); // UPPERCASE_CODE -> couponId
