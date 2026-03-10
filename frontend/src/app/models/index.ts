export interface User {
  id: string;
  email: string;
  name: string;
  role: 'customer' | 'admin';
  createdAt: string;
}

export interface Book {
  id: string;
  title: string;
  author: string;
  genre: string;
  price: number;
  stock: number;
  description: string;
  isbn: string;
  createdAt: string;
  updatedAt: string;
}

export interface PagedBooksResponse {
  data: Book[];
  total: number;
  page: number;
  limit: number;
}

export interface CartItem {
  bookId: string;
  title: string;
  author: string;
  quantity: number;
  priceAtAdd: number;
}

export interface CouponDetails {
  code: string;
  type: 'percentage' | 'fixed';
  value: number;
  description: string;
}

export interface EnrichedCart {
  userId: string;
  items: CartItem[];
  couponCode: string | null;
  subtotal: number;
  discountAmount: number;
  finalTotal: number;
  coupon: CouponDetails | null;
  updatedAt: string;
}

export interface OrderItem {
  bookId: string;
  title: string;
  quantity: number;
  priceAtOrder: number;
}

export type OrderStatus = 'pending' | 'confirmed' | 'shipped' | 'cancelled';

export interface Order {
  id: string;
  userId: string;
  items: OrderItem[];
  subtotal: number;
  discountAmount: number;
  totalAmount: number;
  couponCode: string | null;
  status: OrderStatus;
  createdAt: string;
  updatedAt: string;
}

export type TransactionType = 'credit' | 'debit' | 'order_payment' | 'refund';

export interface Transaction {
  id: string;
  userId: string;
  type: TransactionType;
  amount: number;
  description: string;
  orderId: string | null;
  balanceAfter: number;
  createdAt: string;
}

export interface AccountInfo {
  balance: number;
  transactions: Transaction[];
}

export interface Coupon {
  id: string;
  code: string;
  type: 'percentage' | 'fixed';
  value: number;
  description: string;
  minOrderAmount: number;
  maxUses: number | null;
  usedCount: number;
  active: boolean;
  expiresAt: string | null;
  createdAt: string;
  newUserOnlyDays: number | null;
  accountLevel: string | null;
}

export interface CustomerSummary {
  id: string;
  name: string;
  email: string;
  balance: number;
  orderCount: number;
  createdAt: string;
  accountLevel: string | null;
}

export interface CustomerDetail extends CustomerSummary {
  role: string;
  transactions: Transaction[];
  orders: Order[];
}
