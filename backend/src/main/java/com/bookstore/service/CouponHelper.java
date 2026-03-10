package com.bookstore.service;

import com.bookstore.model.Coupon;
import com.bookstore.model.CouponType;
import com.bookstore.repository.CouponRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class CouponHelper {

    private final CouponRepository couponRepository;

    public CouponHelper(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    /**
     * Looks up and validates a coupon by code. Throws IllegalArgumentException if invalid.
     *
     * @param userId the ID of the user attempting to use the coupon
     */
    public Coupon validateCoupon(String code, double subtotal, Instant userRegisteredAt, String userId) {
        Coupon coupon = couponRepository.findByCode(code.toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Coupon code not found: " + code));
        if (!coupon.isActive()) {
            throw new IllegalArgumentException("Coupon is not active");
        }
        if (coupon.getExpiresAt() != null && Instant.now().isAfter(coupon.getExpiresAt())) {
            throw new IllegalArgumentException("Coupon has expired");
        }
        if (coupon.getMaxUses() != null && coupon.getUsedCount() >= coupon.getMaxUses()) {
            throw new IllegalArgumentException("Coupon has reached its usage limit");
        }
        if (subtotal < coupon.getMinOrderAmount()) {
            throw new IllegalArgumentException(
                    "Minimum order amount of " + coupon.getMinOrderAmount() + " not met");
        }
        if (coupon.getNewUserOnlyDays() != null) {
            if (userRegisteredAt == null) {
                throw new IllegalArgumentException(
                        "Coupon is only available to new users within "
                        + coupon.getNewUserOnlyDays() + " days of registration");
            }
            Instant eligibleUntil = userRegisteredAt.plus(coupon.getNewUserOnlyDays(), ChronoUnit.DAYS);
            if (Instant.now().isAfter(eligibleUntil)) {
                throw new IllegalArgumentException(
                        "Coupon is only available to new users within "
                        + coupon.getNewUserOnlyDays() + " days of registration");
            }
        }
        if (coupon.getAllowedUserId() != null) {
            if (!coupon.getAllowedUserId().equals(userId)) {
                throw new IllegalArgumentException("Coupon is not valid for this account");
            }
        }
        return coupon;
    }

    /**
     * Attempts validation — returns coupon or null if invalid (silent, for checkout).
     *
     * @param userId the ID of the user attempting to use the coupon
     */
    public Coupon tryValidateCoupon(String code, double subtotal, Instant userRegisteredAt, String userId) {
        try {
            return validateCoupon(code, subtotal, userRegisteredAt, userId);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Computes discount amount (rounded to 2 decimal places).
     */
    public double computeDiscount(Coupon coupon, double subtotal) {
        if (coupon.getType() == CouponType.percentage) {
            double disc = subtotal * (coupon.getValue() / 100.0);
            return Math.round(disc * 100.0) / 100.0;
        } else {
            double fixed = Math.round(coupon.getValue() * 100.0) / 100.0;
            return Math.min(fixed, subtotal);
        }
    }
}
