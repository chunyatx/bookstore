package com.bookstore.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class EnrichedCartResponse {
    private String userId;
    private List<EnrichedCartItem> items;
    private String couponCode;
    private double subtotal;
    private double discountAmount;
    private double finalTotal;
    private Map<String, Object> coupon; // null if no coupon
    private Instant updatedAt;

    // Nested enriched cart item (includes book title + author)
    public static class EnrichedCartItem {
        private String bookId;
        private String title;
        private String author;
        private int quantity;
        private double priceAtAdd;

        public EnrichedCartItem(String bookId, String title, String author, int quantity, double priceAtAdd) {
            this.bookId = bookId;
            this.title = title;
            this.author = author;
            this.quantity = quantity;
            this.priceAtAdd = priceAtAdd;
        }

        public String getBookId() { return bookId; }
        public String getTitle() { return title; }
        public String getAuthor() { return author; }
        public int getQuantity() { return quantity; }
        public double getPriceAtAdd() { return priceAtAdd; }
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public List<EnrichedCartItem> getItems() { return items; }
    public void setItems(List<EnrichedCartItem> items) { this.items = items; }
    public String getCouponCode() { return couponCode; }
    public void setCouponCode(String couponCode) { this.couponCode = couponCode; }
    public double getSubtotal() { return subtotal; }
    public void setSubtotal(double subtotal) { this.subtotal = subtotal; }
    public double getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(double discountAmount) { this.discountAmount = discountAmount; }
    public double getFinalTotal() { return finalTotal; }
    public void setFinalTotal(double finalTotal) { this.finalTotal = finalTotal; }
    public Map<String, Object> getCoupon() { return coupon; }
    public void setCoupon(Map<String, Object> coupon) { this.coupon = coupon; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
