package com.bookstore.store;

import com.bookstore.model.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryStore {

    // Books
    public final ConcurrentHashMap<String, Book> books = new ConcurrentHashMap<>();
    public final ConcurrentHashMap<String, String> isbnIndex = new ConcurrentHashMap<>(); // isbn -> bookId

    // Users
    public final ConcurrentHashMap<String, User> users = new ConcurrentHashMap<>();
    public final ConcurrentHashMap<String, String> emailIndex = new ConcurrentHashMap<>(); // email -> userId

    // Carts (userId -> Cart)
    public final ConcurrentHashMap<String, Cart> carts = new ConcurrentHashMap<>();

    // Orders
    public final ConcurrentHashMap<String, Order> orders = new ConcurrentHashMap<>();
    public final ConcurrentHashMap<String, List<String>> userOrders = new ConcurrentHashMap<>(); // userId -> [orderId]

    // Accounts & Transactions
    public final ConcurrentHashMap<String, Account> accounts = new ConcurrentHashMap<>();
    public final ConcurrentHashMap<String, Transaction> transactions = new ConcurrentHashMap<>();
    public final ConcurrentHashMap<String, List<String>> userTransactions = new ConcurrentHashMap<>(); // userId -> [txId]

    // Coupons
    public final ConcurrentHashMap<String, Coupon> coupons = new ConcurrentHashMap<>();
    public final ConcurrentHashMap<String, String> couponCodeIndex = new ConcurrentHashMap<>(); // UPPER_CODE -> couponId

    public List<String> getUserOrders(String userId) {
        return userOrders.computeIfAbsent(userId, k -> new ArrayList<>());
    }

    public List<String> getUserTransactions(String userId) {
        return userTransactions.computeIfAbsent(userId, k -> new ArrayList<>());
    }
}
