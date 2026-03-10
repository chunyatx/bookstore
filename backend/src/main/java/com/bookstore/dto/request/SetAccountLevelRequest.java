package com.bookstore.dto.request;

import jakarta.validation.constraints.Size;

public class SetAccountLevelRequest {

    @Size(max = 50)
    private String level; // null or blank clears the account level

    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
}
