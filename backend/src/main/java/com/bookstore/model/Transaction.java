package com.bookstore.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "BS_TRANSACTIONS")
public class Transaction {

    @Id
    @Column(name = "ID", length = 36, nullable = false)
    private String id;

    @Column(name = "USER_ID", length = 36, nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "TYPE", length = 30, nullable = false)
    private TransactionType type;

    @Column(name = "AMOUNT", nullable = false)
    private double amount;

    @Column(name = "DESCRIPTION", length = 500)
    private String description;

    @Column(name = "ORDER_ID", length = 36)
    private String orderId;

    @Column(name = "BALANCE_AFTER", nullable = false)
    private double balanceAfter;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    public Transaction() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public TransactionType getType() { return type; }
    public void setType(TransactionType type) { this.type = type; }
    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public double getBalanceAfter() { return balanceAfter; }
    public void setBalanceAfter(double balanceAfter) { this.balanceAfter = balanceAfter; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
