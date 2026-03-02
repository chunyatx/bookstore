package com.bookstore.model;

public class CartItem {
    private String bookId;
    private int quantity;
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
