package com.bookstore.model;

public class OrderItem {
    private String bookId;
    private String title;
    private int quantity;
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
