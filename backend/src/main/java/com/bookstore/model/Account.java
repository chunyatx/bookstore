package com.bookstore.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "BS_ACCOUNTS")
public class Account {

    @Id
    @Column(name = "USER_ID", length = 36, nullable = false)
    private String userId;

    @Column(name = "BALANCE", nullable = false)
    private double balance;

    @Column(name = "UPDATED_AT", nullable = false)
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
