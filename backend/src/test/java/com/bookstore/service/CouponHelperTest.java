package com.bookstore.service;

import com.bookstore.model.Coupon;
import com.bookstore.model.CouponType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class CouponHelperTest {

    private MockRepositories mr;
    private CouponHelper helper;

    @BeforeEach
    void setUp() {
        mr = new MockRepositories();
        helper = new CouponHelper(mr.couponRepo);
    }

    // ── validateCoupon ────────────────────────────────────────────────────────

    @Test
    void validate_validPercentageCoupon_returnsIt() {
        TestFixtures.addCoupon(mr, "SAVE10", CouponType.percentage, 10, 0);
        Coupon result = helper.validateCoupon("SAVE10", 50.0, null);
        assertThat(result.getCode()).isEqualTo("SAVE10");
    }

    @Test
    void validate_codeNotFound_throws() {
        assertThatThrownBy(() -> helper.validateCoupon("GHOST", 50.0, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void validate_inactiveCoupon_throws() {
        Coupon c = TestFixtures.addCoupon(mr, "OFF", CouponType.fixed, 5, 0);
        c.setActive(false);
        assertThatThrownBy(() -> helper.validateCoupon("OFF", 50.0, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void validate_expiredCoupon_throws() {
        Coupon c = TestFixtures.addCoupon(mr, "OLD", CouponType.percentage, 10, 0);
        c.setExpiresAt(Instant.now().minusSeconds(3600));
        assertThatThrownBy(() -> helper.validateCoupon("OLD", 50.0, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void validate_maxUsesReached_throws() {
        Coupon c = TestFixtures.addCoupon(mr, "LIMITED", CouponType.percentage, 10, 0);
        c.setMaxUses(5);
        c.setUsedCount(5);
        assertThatThrownBy(() -> helper.validateCoupon("LIMITED", 50.0, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("usage limit");
    }

    @Test
    void validate_belowMinOrderAmount_throws() {
        TestFixtures.addCoupon(mr, "BIG", CouponType.fixed, 5, 100.0);
        assertThatThrownBy(() -> helper.validateCoupon("BIG", 30.0, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Minimum order amount");
    }

    @Test
    void validate_caseInsensitiveCode() {
        TestFixtures.addCoupon(mr, "UPPER", CouponType.percentage, 10, 0);
        assertThatCode(() -> helper.validateCoupon("upper", 50.0, null)).doesNotThrowAnyException();
    }

    @Test
    void validate_newUserOnly_userTooOld_throws() {
        Coupon c = TestFixtures.addCoupon(mr, "NEWUSER", CouponType.percentage, 10, 0);
        c.setNewUserOnlyDays(7);
        Instant registeredAt = Instant.now().minusSeconds(8 * 24 * 3600L);
        assertThatThrownBy(() -> helper.validateCoupon("NEWUSER", 50.0, registeredAt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("new users");
    }

    @Test
    void validate_newUserOnly_recentUser_succeeds() {
        Coupon c = TestFixtures.addCoupon(mr, "NEWUSER", CouponType.percentage, 10, 0);
        c.setNewUserOnlyDays(7);
        Instant registeredAt = Instant.now().minusSeconds(3 * 24 * 3600L);
        assertThatCode(() -> helper.validateCoupon("NEWUSER", 50.0, registeredAt))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_newUserOnly_nullUser_throws() {
        Coupon c = TestFixtures.addCoupon(mr, "NEWUSER", CouponType.percentage, 10, 0);
        c.setNewUserOnlyDays(7);
        assertThatThrownBy(() -> helper.validateCoupon("NEWUSER", 50.0, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("new users");
    }

    // ── tryValidateCoupon ─────────────────────────────────────────────────────

    @Test
    void tryValidate_validCoupon_returnsCoupon() {
        TestFixtures.addCoupon(mr, "OK", CouponType.fixed, 5, 0);
        assertThat(helper.tryValidateCoupon("OK", 50.0, null)).isNotNull();
    }

    @Test
    void tryValidate_invalidCode_returnsNull() {
        assertThat(helper.tryValidateCoupon("NOPE", 50.0, null)).isNull();
    }

    // ── computeDiscount ───────────────────────────────────────────────────────

    @Test
    void computeDiscount_percentage() {
        Coupon c = TestFixtures.addCoupon(mr, "P", CouponType.percentage, 10, 0);
        assertThat(helper.computeDiscount(c, 50.0)).isEqualTo(5.0);
    }

    @Test
    void computeDiscount_fixed_doesNotExceedSubtotal() {
        Coupon c = TestFixtures.addCoupon(mr, "F", CouponType.fixed, 100, 0);
        assertThat(helper.computeDiscount(c, 30.0)).isEqualTo(30.0);
    }

    @Test
    void computeDiscount_fixed_smallerThanSubtotal() {
        Coupon c = TestFixtures.addCoupon(mr, "F2", CouponType.fixed, 5, 0);
        assertThat(helper.computeDiscount(c, 30.0)).isEqualTo(5.0);
    }
}
