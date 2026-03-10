package com.bookstore.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "BS_COUPONS")
public class Coupon {

    @Id
    @Column(name = "ID", length = 36, nullable = false)
    private String id;

    @Column(name = "CODE", unique = true, nullable = false, length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "TYPE", length = 20, nullable = false)
    private CouponType type;

    @Column(name = "COUPON_VALUE", nullable = false)
    private double value;

    @Column(name = "DESCRIPTION", length = 500)
    private String description;

    @Column(name = "MIN_ORDER_AMOUNT", nullable = false)
    private double minOrderAmount;

    @Column(name = "MAX_USES")
    private Integer maxUses;

    @Column(name = "USED_COUNT", nullable = false)
    private int usedCount;

    @Column(name = "IS_ACTIVE", nullable = false)
    private boolean isActive;

    @Column(name = "EXPIRES_AT")
    private Instant expiresAt;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    @Column(name = "NEW_USER_ONLY_DAYS")
    private Integer newUserOnlyDays;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "BS_COUPON_ALLOWED_USERS",
            joinColumns = @JoinColumn(name = "COUPON_ID"))
    @Column(name = "USER_ID", length = 36)
    private Set<String> allowedUserIds = new HashSet<>();

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
    public Set<String> getAllowedUserIds() { return allowedUserIds; }
    public void setAllowedUserIds(Set<String> allowedUserIds) { this.allowedUserIds = allowedUserIds != null ? allowedUserIds : new HashSet<>(); }
}
