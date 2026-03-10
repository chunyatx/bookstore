package com.bookstore.dto.request;

import com.bookstore.model.CouponType;
import jakarta.validation.constraints.*;

public class CreateCouponRequest {

    @NotBlank @Size(min = 3, max = 20)
    private String code;

    @NotNull
    private CouponType type;

    @Positive
    private double value;

    @NotBlank
    private String description;

    @Min(0)
    private double minOrderAmount = 0;

    private Integer maxUses; // null = unlimited

    private String expiresAt; // ISO-8601 string, null = no expiry

    private Integer newUserOnlyDays; // null = no restriction; N = only users registered within N days

    @Size(max = 36)
    private String allowedUserId; // null = any user; set to restrict coupon to a specific account

    public String getCode() { return code != null ? code.toUpperCase().trim() : null; }
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
    public String getExpiresAt() { return expiresAt; }
    public void setExpiresAt(String expiresAt) { this.expiresAt = expiresAt; }
    public Integer getNewUserOnlyDays() { return newUserOnlyDays; }
    public void setNewUserOnlyDays(Integer newUserOnlyDays) { this.newUserOnlyDays = newUserOnlyDays; }
    public String getAllowedUserId() { return allowedUserId != null ? allowedUserId.trim() : null; }
    public void setAllowedUserId(String allowedUserId) { this.allowedUserId = allowedUserId; }
}
