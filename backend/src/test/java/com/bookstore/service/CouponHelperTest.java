package com.bookstore.service;

import com.bookstore.model.Coupon;
import com.bookstore.model.CouponType;
import com.bookstore.store.InMemoryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class CouponHelperTest {

    private InMemoryStore store;
    private CouponHelper helper;

    @BeforeEach
    void setUp() {
        store = TestFixtures.freshStore();
        helper = new CouponHelper(store);
    }

    // ── validateCoupon ────────────────────────────────────────────────────────

    @Test
    void validate_validPercentageCoupon_returnsIt() {
        TestFixtures.addCoupon(store, "SAVE10", CouponType.percentage, 10, 0);
        Coupon result = helper.validateCoupon("SAVE10", 50.0);
        assertThat(result.getCode()).isEqualTo("SAVE10");
    }

    @Test
    void validate_codeNotFound_throws() {
        assertThatThrownBy(() -> helper.validateCoupon("GHOST", 50.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void validate_inactiveCoupon_throws() {
        Coupon c = TestFixtures.addCoupon(store, "OFF", CouponType.fixed, 5, 0);
        c.setActive(false);
        assertThatThrownBy(() -> helper.validateCoupon("OFF", 50.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void validate_expiredCoupon_throws() {
        Coupon c = TestFixtures.addCoupon(store, "OLD", CouponType.percentage, 10, 0);
        c.setExpiresAt(Instant.now().minusSeconds(3600));
        assertThatThrownBy(() -> helper.validateCoupon("OLD", 50.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void validate_maxUsesReached_throws() {
        Coupon c = TestFixtures.addCoupon(store, "LIMITED", CouponType.percentage, 10, 0);
        c.setMaxUses(5);
        c.setUsedCount(5);
        assertThatThrownBy(() -> helper.validateCoupon("LIMITED", 50.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("usage limit");
    }

    @Test
    void validate_belowMinOrderAmount_throws() {
        TestFixtures.addCoupon(store, "BIG", CouponType.fixed, 5, 100.0);
        assertThatThrownBy(() -> helper.validateCoupon("BIG", 30.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Minimum order amount");
    }

    @Test
    void validate_caseInsensitiveCode() {
        TestFixtures.addCoupon(store, "UPPER", CouponType.percentage, 10, 0);
        assertThatCode(() -> helper.validateCoupon("upper", 50.0)).doesNotThrowAnyException();
    }

    @Test
    void validate_notYetExpired_succeeds() {
        Coupon c = TestFixtures.addCoupon(store, "FUTURE", CouponType.percentage, 10, 0);
        c.setExpiresAt(Instant.now().plusSeconds(3600));
        assertThatCode(() -> helper.validateCoupon("FUTURE", 50.0)).doesNotThrowAnyException();
    }

    // ── tryValidateCoupon ─────────────────────────────────────────────────────

    @Test
    void tryValidate_invalidCoupon_returnsNull() {
        assertThat(helper.tryValidateCoupon("NONE", 50.0)).isNull();
    }

    @Test
    void tryValidate_validCoupon_returnsCoupon() {
        TestFixtures.addCoupon(store, "GOOD", CouponType.percentage, 20, 0);
        assertThat(helper.tryValidateCoupon("GOOD", 50.0)).isNotNull();
    }

    // ── computeDiscount ───────────────────────────────────────────────────────

    @Test
    void computeDiscount_percentage_calculatesCorrectly() {
        Coupon c = TestFixtures.addCoupon(store, "P10", CouponType.percentage, 10, 0);
        assertThat(helper.computeDiscount(c, 50.0)).isEqualTo(5.0);
    }

    @Test
    void computeDiscount_percentage_roundsToTwoDecimals() {
        Coupon c = TestFixtures.addCoupon(store, "P15", CouponType.percentage, 15, 0);
        // 15% of 33.33 = 4.9995 → rounds to 5.0
        assertThat(helper.computeDiscount(c, 33.33)).isEqualTo(5.0);
    }

    @Test
    void computeDiscount_fixed_returnsFixedAmount() {
        Coupon c = TestFixtures.addCoupon(store, "F5", CouponType.fixed, 5, 0);
        assertThat(helper.computeDiscount(c, 50.0)).isEqualTo(5.0);
    }

    @Test
    void computeDiscount_fixedExceedsSubtotal_cappedAtSubtotal() {
        Coupon c = TestFixtures.addCoupon(store, "BIG", CouponType.fixed, 100, 0);
        assertThat(helper.computeDiscount(c, 30.0)).isEqualTo(30.0);
    }

    @Test
    void computeDiscount_100percent_equalsSubtotal() {
        Coupon c = TestFixtures.addCoupon(store, "FREE", CouponType.percentage, 100, 0);
        assertThat(helper.computeDiscount(c, 49.99)).isEqualTo(49.99);
    }
}
