package com.bookstore.store;

import com.bookstore.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Legacy in-memory store — retained for unit test use only.
 * The application now uses JPA repositories backed by Oracle DB.
 */
public class InMemoryStore {

    public final ConcurrentHashMap<String, Book> books = new ConcurrentHashMap<>();
    public final ConcurrentHashMap<String, String> isbnIndex = new ConcurrentHashMap<>();

    public final ConcurrentHashMap<String, User> users = new ConcurrentHashMap<>();
    public final ConcurrentHashMap<String, String> emailIndex = new ConcurrentHashMap<>();

    public final ConcurrentHashMap<String, Cart> carts = new ConcurrentHashMap<>();

    public final ConcurrentHashMap<String, Order> orders = new ConcurrentHashMap<>();
    public final ConcurrentHashMap<String, List<String>> userOrders = new ConcurrentHashMap<>();

    public final ConcurrentHashMap<String, Account> accounts = new ConcurrentHashMap<>();
    public final ConcurrentHashMap<String, Transaction> transactions = new ConcurrentHashMap<>();
    public final ConcurrentHashMap<String, List<String>> userTransactions = new ConcurrentHashMap<>();

    public final ConcurrentHashMap<String, Coupon> coupons = new ConcurrentHashMap<>();
    public final ConcurrentHashMap<String, String> couponCodeIndex = new ConcurrentHashMap<>();

    public List<String> getUserOrders(String userId) {
        return userOrders.computeIfAbsent(userId, k -> new ArrayList<>());
    }

    public List<String> getUserTransactions(String userId) {
        return userTransactions.computeIfAbsent(userId, k -> new ArrayList<>());
    }
}
