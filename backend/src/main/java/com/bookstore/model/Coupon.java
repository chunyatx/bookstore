package com.bookstore.model;

import java.time.Instant;

public class Coupon {
    private String id;
    private String code;
    private CouponType type;
    private double value;
    private String description;
    private double minOrderAmount;
    private Integer maxUses; // null = unlimited
    private int usedCount;
    private boolean isActive;
    private Instant expiresAt; // null = no expiry
    private Instant createdAt;
    private Integer newUserOnlyDays; // null = no restriction; N = only users registered within N days

    public Coupon() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public CouponType getType() { return type; }
    public void setType(CouponType type) { this.type = type; }
    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public double getMinOrderAmount() { return minOrderAmount; }
    public void setMinOrderAmount(double minOrderAmount) { this.minOrderAmount = minOrderAmount; }
    public Integer getMaxUses() { return maxUses; }
    public void setMaxUses(Integer maxUses) { this.maxUses = maxUses; }
    public int getUsedCount() { return usedCount; }
    public void setUsedCount(int usedCount) { this.usedCount = usedCount; }
    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Integer getNewUserOnlyDays() { return newUserOnlyDays; }
    public void setNewUserOnlyDays(Integer newUserOnlyDays) { this.newUserOnlyDays = newUserOnlyDays; }
}
