package com.bookstore.dto.request;

import jakarta.validation.constraints.Min;

public class UpdateCartItemRequest {

    @Min(0)
    private int quantity;

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}
