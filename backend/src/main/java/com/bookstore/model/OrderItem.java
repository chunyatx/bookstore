package com.bookstore.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class OrderItem {

    @Column(name = "BOOK_ID", length = 36, nullable = false)
    private String bookId;

    @Column(name = "TITLE", length = 500, nullable = false)
    private String title;

    @Column(name = "QUANTITY", nullable = false)
    private int quantity;

    @Column(name = "PRICE_AT_ORDER", nullable = false)
    private double priceAtOrder;

    public OrderItem() {}

    public OrderItem(String bookId, String title, int quantity, double priceAtOrder) {
        this.bookId = bookId;
        this.title = title;
        this.quantity = quantity;
        this.priceAtOrder = priceAtOrder;
    }

    public String getBookId() { return bookId; }
    public void setBookId(String bookId) { this.bookId = bookId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public double getPriceAtOrder() { return priceAtOrder; }
    public void setPriceAtOrder(double priceAtOrder) { this.priceAtOrder = priceAtOrder; }
}
