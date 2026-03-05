package com.bookstore.service;

import com.bookstore.model.*;
import com.bookstore.security.JwtUtil;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.UUID;

/**
 * Shared helpers for service-layer unit tests.
 * Each test creates a fresh MockRepositories so tests are fully isolated.
 */
public final class TestFixtures {

    public static final String JWT_SECRET = "test-secret-that-is-at-least-32-chars-long!!";
    public static final long JWT_EXPIRY_MS = 3_600_000L;

    private TestFixtures() {}

    public static PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(4);
    }

    public static JwtUtil jwtUtil() {
        return new JwtUtil(JWT_SECRET, JWT_EXPIRY_MS);
    }

    /** Creates a user and zero-balance account in the mock repositories. */
    public static User addUser(MockRepositories mr, PasswordEncoder encoder,
                               String email, String password, String name, String role) {
        String id = UUID.randomUUID().toString();
        User user = new User(id, email.toLowerCase(), encoder.encode(password), name, role, Instant.now());
        mr.users.put(id, user);
        mr.accounts.put(id, new Account(id, 0.0));
        return user;
    }

    /** Adds a book to the mock repository. Returns the stored Book. */
    public static Book addBook(MockRepositories mr, String title, String author,
                               String genre, double price, int stock, String isbn) {
        Instant now = Instant.now();
        Book book = new Book(UUID.randomUUID().toString(), title, author, genre,
                             price, stock, "", isbn, now, now);
        mr.books.put(book.getId(), book);
        return book;
    }

    /** Creates an active coupon in the mock repository. */
    public static Coupon addCoupon(MockRepositories mr, String code,
                                   CouponType type, double value, double minOrder) {
        Coupon c = new Coupon();
        c.setId(UUID.randomUUID().toString());
        c.setCode(code);
        c.setType(type);
        c.setValue(value);
        c.setDescription("Test coupon");
        c.setMinOrderAmount(minOrder);
        c.setMaxUses(null);
        c.setUsedCount(0);
        c.setActive(true);
        c.setExpiresAt(null);
        c.setCreatedAt(Instant.now());
        mr.coupons.put(c.getId(), c);
        return c;
    }

    /** Funds a user's wallet. */
    public static void credit(MockRepositories mr, String userId, double amount) {
        Account acc = mr.accounts.get(userId);
        acc.setBalance(acc.getBalance() + amount);
    }

    /** Puts an item directly into the user's cart (creates cart if absent). */
    public static CartItem addToCart(MockRepositories mr, String userId,
                                     String bookId, int qty, double priceAtAdd) {
        Cart cart = mr.carts.computeIfAbsent(userId, Cart::new);
        CartItem item = new CartItem(bookId, qty, priceAtAdd);
        cart.getItems().add(item);
        return item;
    }
}
