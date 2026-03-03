package com.bookstore.service;

import com.bookstore.dto.request.AddToCartRequest;
import com.bookstore.dto.request.ApplyCouponRequest;
import com.bookstore.dto.request.UpdateCartItemRequest;
import com.bookstore.dto.response.EnrichedCartResponse;
import com.bookstore.model.Book;
import com.bookstore.model.CouponType;
import com.bookstore.store.InMemoryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.*;

class CartServiceTest {

    private InMemoryStore store;
    private CartService service;
    private String userId;
    private Book book;

    @BeforeEach
    void setUp() {
        store = TestFixtures.freshStore();
        PasswordEncoder encoder = TestFixtures.passwordEncoder();
        service = new CartService(store, new CouponHelper(store));

        var user = TestFixtures.addUser(store, encoder, "user@test.com", "pass", "User", "customer");
        userId = user.getId();
        book = TestFixtures.addBook(store, "Test Book", "Author", "Genre", 10.0, 5, "9780000000001");
    }

    // ── getCart ───────────────────────────────────────────────────────────────

    @Test
    void getCart_noExistingCart_createsEmpty() {
        EnrichedCartResponse resp = service.getCart(userId);
        assertThat(resp.getItems()).isEmpty();
        assertThat(resp.getSubtotal()).isEqualTo(0.0);
        assertThat(resp.getFinalTotal()).isEqualTo(0.0);
    }

    // ── addItem ───────────────────────────────────────────────────────────────

    @Test
    void addItem_validBook_addsToCart() {
        service.addItem(userId, addReq(book.getId(), 2));
        EnrichedCartResponse cart = service.getCart(userId);
        assertThat(cart.getItems()).hasSize(1);
        assertThat(cart.getItems().get(0).getQuantity()).isEqualTo(2);
    }

    @Test
    void addItem_snapshotsPriceAtAddTime() {
        service.addItem(userId, addReq(book.getId(), 1));
        // Change the book price after adding
        book.setPrice(99.99);
        EnrichedCartResponse cart = service.getCart(userId);
        // Price in cart should still be the original 10.0
        assertThat(cart.getItems().get(0).getPriceAtAdd()).isEqualTo(10.0);
    }

    @Test
    void addItem_addingSameBookTwice_accumulatesQuantity() {
        service.addItem(userId, addReq(book.getId(), 2));
        service.addItem(userId, addReq(book.getId(), 1));
        EnrichedCartResponse cart = service.getCart(userId);
        assertThat(cart.getItems()).hasSize(1);
        assertThat(cart.getItems().get(0).getQuantity()).isEqualTo(3);
    }

    @Test
    void addItem_exceedsStock_throws400() {
        assertThatThrownBy(() -> service.addItem(userId, addReq(book.getId(), 10)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Insufficient stock");
    }

    @Test
    void addItem_unknownBook_throws404() {
        assertThatThrownBy(() -> service.addItem(userId, addReq("no-such-id", 1)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void addItem_calculatesSubtotalCorrectly() {
        service.addItem(userId, addReq(book.getId(), 3));
        EnrichedCartResponse cart = service.getCart(userId);
        assertThat(cart.getSubtotal()).isEqualTo(30.0);
        assertThat(cart.getFinalTotal()).isEqualTo(30.0);
    }

    // ── updateItem ────────────────────────────────────────────────────────────

    @Test
    void updateItem_setQuantity_updatesCart() {
        service.addItem(userId, addReq(book.getId(), 3));
        service.updateItem(userId, book.getId(), updateReq(1));
        EnrichedCartResponse cart = service.getCart(userId);
        assertThat(cart.getItems().get(0).getQuantity()).isEqualTo(1);
    }

    @Test
    void updateItem_quantityZero_removesItem() {
        service.addItem(userId, addReq(book.getId(), 2));
        service.updateItem(userId, book.getId(), updateReq(0));
        assertThat(service.getCart(userId).getItems()).isEmpty();
    }

    @Test
    void updateItem_exceedsStock_throws400() {
        service.addItem(userId, addReq(book.getId(), 1));
        assertThatThrownBy(() -> service.updateItem(userId, book.getId(), updateReq(100)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Insufficient stock");
    }

    @Test
    void updateItem_itemNotInCart_throws404() {
        assertThatThrownBy(() -> service.updateItem(userId, book.getId(), updateReq(1)))
                .isInstanceOf(ResponseStatusException.class);
    }

    // ── clearCart ─────────────────────────────────────────────────────────────

    @Test
    void clearCart_removesAllItems() {
        service.addItem(userId, addReq(book.getId(), 2));
        service.clearCart(userId);
        assertThat(service.getCart(userId).getItems()).isEmpty();
    }

    @Test
    void clearCart_removesAppliedCoupon() {
        TestFixtures.addCoupon(store, "SAVE10", CouponType.percentage, 10, 0);
        service.addItem(userId, addReq(book.getId(), 1));
        service.applyCoupon(userId, couponReq("SAVE10"));
        service.clearCart(userId);
        assertThat(service.getCart(userId).getCouponCode()).isNull();
    }

    // ── applyCoupon ───────────────────────────────────────────────────────────

    @Test
    void applyCoupon_validCode_appliesDiscount() {
        TestFixtures.addCoupon(store, "SAVE10", CouponType.percentage, 10, 0);
        service.addItem(userId, addReq(book.getId(), 2));  // subtotal = 20.0
        EnrichedCartResponse cart = service.applyCoupon(userId, couponReq("SAVE10"));

        assertThat(cart.getCouponCode()).isEqualTo("SAVE10");
        assertThat(cart.getDiscountAmount()).isEqualTo(2.0);
        assertThat(cart.getFinalTotal()).isEqualTo(18.0);
    }

    @Test
    void applyCoupon_invalidCode_throws() {
        assertThatThrownBy(() -> service.applyCoupon(userId, couponReq("BOGUS")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void applyCoupon_belowMinOrder_throws() {
        TestFixtures.addCoupon(store, "MIN50", CouponType.fixed, 5, 50.0);
        service.addItem(userId, addReq(book.getId(), 1));  // subtotal = 10.0
        assertThatThrownBy(() -> service.applyCoupon(userId, couponReq("MIN50")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Minimum order amount");
    }

    // ── removeCoupon ──────────────────────────────────────────────────────────

    @Test
    void removeCoupon_clearsCouponAndRestoresTotal() {
        TestFixtures.addCoupon(store, "SAVE10", CouponType.percentage, 10, 0);
        service.addItem(userId, addReq(book.getId(), 1));
        service.applyCoupon(userId, couponReq("SAVE10"));
        EnrichedCartResponse cart = service.removeCoupon(userId);

        assertThat(cart.getCouponCode()).isNull();
        assertThat(cart.getDiscountAmount()).isEqualTo(0.0);
        assertThat(cart.getFinalTotal()).isEqualTo(10.0);
    }

    // ── enrich — expired coupon silently cleared ───────────────────────────────

    @Test
    void enrichCart_expiredCoupon_silentlyClearedOnGet() {
        var coupon = TestFixtures.addCoupon(store, "OLD", CouponType.percentage, 10, 0);
        service.addItem(userId, addReq(book.getId(), 1));
        service.applyCoupon(userId, couponReq("OLD"));

        // Expire the coupon after it was applied
        coupon.setExpiresAt(java.time.Instant.now().minusSeconds(1));

        EnrichedCartResponse cart = service.getCart(userId);
        assertThat(cart.getCouponCode()).isNull();
        assertThat(cart.getDiscountAmount()).isEqualTo(0.0);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static AddToCartRequest addReq(String bookId, int qty) {
        AddToCartRequest r = new AddToCartRequest();
        r.setBookId(bookId);
        r.setQuantity(qty);
        return r;
    }

    private static UpdateCartItemRequest updateReq(int qty) {
        UpdateCartItemRequest r = new UpdateCartItemRequest();
        r.setQuantity(qty);
        return r;
    }

    private static ApplyCouponRequest couponReq(String code) {
        ApplyCouponRequest r = new ApplyCouponRequest();
        r.setCode(code);
        return r;
    }
}
