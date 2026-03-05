package com.bookstore.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "BS_CARTS")
public class Cart {

    @Id
    @Column(name = "USER_ID", length = 36, nullable = false)
    private String userId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "BS_CART_ITEMS", joinColumns = @JoinColumn(name = "CART_USER_ID"))
    private List<CartItem> items = new ArrayList<>();

    @Column(name = "COUPON_CODE", length = 50)
    private String couponCode;

    @Column(name = "UPDATED_AT", nullable = false)
    private Instant updatedAt;

    public Cart() {}

    public Cart(String userId) {
        this.userId = userId;
        this.items = new ArrayList<>();
        this.updatedAt = Instant.now();
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public List<CartItem> getItems() { return items; }
    public void setItems(List<CartItem> items) { this.items = items; }
    public String getCouponCode() { return couponCode; }
    public void setCouponCode(String couponCode) { this.couponCode = couponCode; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
