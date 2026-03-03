package com.bookstore.dto.request;

import jakarta.validation.constraints.NotBlank;

public class ApplyCouponRequest {

    @NotBlank
    private String code;

    public String getCode() { return code != null ? code.toUpperCase().trim() : null; }
    public void setCode(String code) { this.code = code; }
}
