package com.bookstore.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class CartItem {

    @Column(name = "BOOK_ID", length = 36, nullable = false)
    private String bookId;

    @Column(name = "QUANTITY", nullable = false)
    private int quantity;

    @Column(name = "PRICE_AT_ADD", nullable = false)
    private double priceAtAdd;

    public CartItem() {}

    public CartItem(String bookId, int quantity, double priceAtAdd) {
        this.bookId = bookId;
        this.quantity = quantity;
        this.priceAtAdd = priceAtAdd;
    }

    public String getBookId() { return bookId; }
    public void setBookId(String bookId) { this.bookId = bookId; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public double getPriceAtAdd() { return priceAtAdd; }
    public void setPriceAtAdd(double priceAtAdd) { this.priceAtAdd = priceAtAdd; }
}
