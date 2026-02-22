import bcrypt from "bcryptjs";
import { v4 as uuidv4 } from "uuid";
import { User, Book, Coupon } from "./types";
import { usersStore, emailIndex, booksStore, isbnIndex, accountsStore, couponsStore, couponCodeIndex } from "./store";

const ADMIN_EMAIL = "admin@bookstore.com";
const ADMIN_PASS  = "admin123";

const SAMPLE_BOOKS: Omit<Book, "id" | "createdAt" | "updatedAt">[] = [
  { title: "Dune", author: "Frank Herbert", genre: "Science Fiction", price: 14.99, stock: 50, description: "A sweeping sci-fi epic set on the desert planet Arrakis.", isbn: "9780441013593" },
  { title: "The Hitchhiker's Guide to the Galaxy", author: "Douglas Adams", genre: "Science Fiction", price: 9.99, stock: 30, description: "The comedic sci-fi adventure about the end of Earth.", isbn: "9780345391803" },
  { title: "1984", author: "George Orwell", genre: "Fiction", price: 11.99, stock: 40, description: "A dystopian novel set in a totalitarian surveillance state.", isbn: "9780451524935" },
  { title: "To Kill a Mockingbird", author: "Harper Lee", genre: "Fiction", price: 10.99, stock: 25, description: "A classic of modern American literature.", isbn: "9780061935466" },
  { title: "The Lord of the Rings", author: "J.R.R. Tolkien", genre: "Fantasy", price: 19.99, stock: 35, description: "The definitive fantasy epic of the Third Age of Middle-earth.", isbn: "9780544003415" },
  { title: "Harry Potter and the Sorcerer's Stone", author: "J.K. Rowling", genre: "Fantasy", price: 12.99, stock: 60, description: "The boy who lived begins his journey at Hogwarts.", isbn: "9780439708180" },
  { title: "The Girl with the Dragon Tattoo", author: "Stieg Larsson", genre: "Mystery", price: 13.99, stock: 20, description: "A gripping Scandinavian noir mystery.", isbn: "9780307949486" },
  { title: "Gone Girl", author: "Gillian Flynn", genre: "Mystery", price: 12.49, stock: 28, description: "A psychological thriller about a marriage gone wrong.", isbn: "9780307588371" },
  { title: "Sapiens", author: "Yuval Noah Harari", genre: "Non-Fiction", price: 16.99, stock: 45, description: "A brief history of humankind.", isbn: "9780062316097" },
  { title: "Educated", author: "Tara Westover", genre: "Biography", price: 14.49, stock: 22, description: "A memoir about a young woman's struggle for education.", isbn: "9780399590504" },
  { title: "Atomic Habits", author: "James Clear", genre: "Non-Fiction", price: 15.99, stock: 55, description: "An easy and proven way to build good habits.", isbn: "9780735211292" },
  { title: "The Alchemist", author: "Paulo Coelho", genre: "Fiction", price: 10.49, stock: 38, description: "A fable about following your dreams.", isbn: "9780062315007" },
  { title: "Thinking, Fast and Slow", author: "Daniel Kahneman", genre: "Non-Fiction", price: 14.99, stock: 18, description: "A deep dive into the two systems that drive the way we think.", isbn: "9780374533557" },
  { title: "A Brief History of Time", author: "Stephen Hawking", genre: "Non-Fiction", price: 13.49, stock: 30, description: "An exploration of cosmology for a general audience.", isbn: "9780553380163" },
  { title: "The Catcher in the Rye", author: "J.D. Salinger", genre: "Fiction", price: 9.99, stock: 20, description: "A classic coming-of-age story.", isbn: "9780316769174" },
];

const SAMPLE_COUPONS: Omit<Coupon, "id" | "usedCount" | "createdAt">[] = [
  {
    code: "WELCOME10",
    type: "percentage",
    value: 10,
    description: "10% off your first order",
    minOrderAmount: 0,
    maxUses: null,
    isActive: true,
    expiresAt: null,
  },
  {
    code: "SAVE5",
    type: "fixed",
    value: 5,
    description: "$5 off orders over $20",
    minOrderAmount: 20,
    maxUses: null,
    isActive: true,
    expiresAt: null,
  },
  {
    code: "HALFOFF",
    type: "percentage",
    value: 50,
    description: "50% off — limited to 10 uses",
    minOrderAmount: 0,
    maxUses: 10,
    isActive: true,
    expiresAt: null,
  },
];

export async function seedData(): Promise<void> {
  // Create admin user if not exists
  if (!emailIndex.has(ADMIN_EMAIL)) {
    const passwordHash = await bcrypt.hash(ADMIN_PASS, 12);
    const admin: User = {
      id: uuidv4(),
      email: ADMIN_EMAIL,
      passwordHash,
      name: "Admin",
      role: "admin",
      createdAt: new Date(),
    };
    usersStore.set(admin.id, admin);
    emailIndex.set(ADMIN_EMAIL, admin.id);
    accountsStore.set(admin.id, { userId: admin.id, balance: 0, updatedAt: new Date() });
    console.log(`[seed] Admin created — email: ${ADMIN_EMAIL} | password: ${ADMIN_PASS}`);
  }

  // Create sample books if store is empty
  if (booksStore.size === 0) {
    const now = new Date();
    for (const data of SAMPLE_BOOKS) {
      const book: Book = { id: uuidv4(), ...data, createdAt: now, updatedAt: now };
      booksStore.set(book.id, book);
      isbnIndex.set(book.isbn, book.id);
    }
    console.log(`[seed] ${SAMPLE_BOOKS.length} sample books added`);
  }

  // Create sample coupons if store is empty
  if (couponsStore.size === 0) {
    const now = new Date();
    for (const data of SAMPLE_COUPONS) {
      const coupon: Coupon = { id: uuidv4(), ...data, usedCount: 0, createdAt: now };
      couponsStore.set(coupon.id, coupon);
      couponCodeIndex.set(coupon.code, coupon.id);
    }
    console.log(`[seed] ${SAMPLE_COUPONS.length} sample coupons added (WELCOME10, SAVE5, HALFOFF)`);
  }
}
