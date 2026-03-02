package com.bookstore.model;

import java.time.Instant;

public class Account {
    private String userId;
    private double balance;
    private Instant updatedAt;

    public Account() {}

    public Account(String userId, double balance) {
        this.userId = userId;
        this.balance = balance;
        this.updatedAt = Instant.now();
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public double getBalance() { return balance; }
    public void setBalance(double balance) { this.balance = balance; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
