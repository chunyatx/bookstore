package com.bookstore.service;

import com.bookstore.model.*;
import com.bookstore.store.InMemoryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.*;

class OrderServiceTest {

    private InMemoryStore store;
    private OrderService service;
    private String userId;
    private Book book;

    @BeforeEach
    void setUp() {
        store = TestFixtures.freshStore();
        PasswordEncoder encoder = TestFixtures.passwordEncoder();
        service = new OrderService(store, new CouponHelper(store));

        var user = TestFixtures.addUser(store, encoder, "user@test.com", "pass", "User", "customer");
        userId = user.getId();
        book = TestFixtures.addBook(store, "Dune", "Herbert", "Sci-Fi", 10.0, 5, "9780000000001");
        TestFixtures.credit(store, userId, 200.0);
    }

    // ── placeOrder ────────────────────────────────────────────────────────────

    @Test
    void placeOrder_happyPath_createsOrderAndDeductsWallet() {
        TestFixtures.addToCart(store, userId, book.getId(), 2, book.getPrice());

        Order order = service.placeOrder(userId);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.pending);
        assertThat(order.getTotalAmount()).isEqualTo(20.0);
        assertThat(order.getItems()).hasSize(1);
        assertThat(order.getItems().get(0).getTitle()).isEqualTo("Dune");

        // Wallet deducted
        assertThat(store.accounts.get(userId).getBalance()).isEqualTo(180.0);
        // Stock decremented
        assertThat(store.books.get(book.getId()).getStock()).isEqualTo(3);
        // Cart cleared
        assertThat(store.carts.get(userId).getItems()).isEmpty();
        // Transaction recorded
        assertThat(store.getUserTransactions(userId)).hasSize(1);
    }

    @Test
    void placeOrder_snapshotsPriceFromCart() {
        TestFixtures.addToCart(store, userId, book.getId(), 1, 10.0);
        // Change price after adding to cart
        book.setPrice(99.99);

        Order order = service.placeOrder(userId);
        // Order should use 10.0 (the snapshot), not 99.99
        assertThat(order.getItems().get(0).getPriceAtOrder()).isEqualTo(10.0);
        assertThat(order.getTotalAmount()).isEqualTo(10.0);
    }

    @Test
    void placeOrder_emptyCart_throws400() {
        assertThatThrownBy(() -> service.placeOrder(userId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void placeOrder_insufficientStock_throws400() {
        TestFixtures.addToCart(store, userId, book.getId(), 100, book.getPrice());
        assertThatThrownBy(() -> service.placeOrder(userId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Insufficient stock");
    }

    @Test
    void placeOrder_insufficientWallet_throws400() {
        TestFixtures.credit(store, userId, -200.0);  // drain wallet to 0
        TestFixtures.addToCart(store, userId, book.getId(), 1, 10.0);
        assertThatThrownBy(() -> service.placeOrder(userId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Insufficient wallet balance");
    }

    @Test
    void placeOrder_withValidCoupon_appliesDiscount() {
        Coupon coupon = TestFixtures.addCoupon(store, "SAVE10", CouponType.percentage, 10, 0);
        TestFixtures.addToCart(store, userId, book.getId(), 2, 10.0);
        Cart cart = store.carts.get(userId);
        cart.setCouponCode("SAVE10");

        Order order = service.placeOrder(userId);

        assertThat(order.getDiscountAmount()).isEqualTo(2.0);   // 10% of 20
        assertThat(order.getTotalAmount()).isEqualTo(18.0);
        assertThat(order.getCouponCode()).isEqualTo("SAVE10");
        assertThat(coupon.getUsedCount()).isEqualTo(1);
        assertThat(store.accounts.get(userId).getBalance()).isEqualTo(182.0);
    }

    @Test
    void placeOrder_withExpiredCoupon_silentlyIgnoresCoupon() {
        Coupon coupon = TestFixtures.addCoupon(store, "OLD", CouponType.percentage, 10, 0);
        coupon.setExpiresAt(java.time.Instant.now().minusSeconds(1));
        TestFixtures.addToCart(store, userId, book.getId(), 2, 10.0);
        store.carts.get(userId).setCouponCode("OLD");

        Order order = service.placeOrder(userId);
        // Full price charged, no discount applied
        assertThat(order.getTotalAmount()).isEqualTo(20.0);
        assertThat(order.getDiscountAmount()).isEqualTo(0.0);
        assertThat(coupon.getUsedCount()).isEqualTo(0);
    }

    @Test
    void placeOrder_stockValidatedBeforeAnyMutation_atomicRejection() {
        // Two books: book1 ok, book2 out of stock
        Book book2 = TestFixtures.addBook(store, "Rare", "A", "G", 5.0, 1, "9780000000002");
        TestFixtures.addToCart(store, userId, book.getId(), 1, 10.0);
        TestFixtures.addToCart(store, userId, book2.getId(), 5, 5.0);  // exceeds stock of 1

        assertThatThrownBy(() -> service.placeOrder(userId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Insufficient stock");

        // book1 stock must NOT have been decremented (atomic rejection)
        assertThat(store.books.get(book.getId()).getStock()).isEqualTo(5);
    }

    @Test
    void placeOrder_storesOrderAndUserOrderLink() {
        TestFixtures.addToCart(store, userId, book.getId(), 1, 10.0);
        Order order = service.placeOrder(userId);

        assertThat(store.orders).containsKey(order.getId());
        assertThat(store.getUserOrders(userId)).contains(order.getId());
    }

    // ── cancelOrder ───────────────────────────────────────────────────────────

    @Test
    void cancelOrder_pendingOrder_refundsAndRestoresStock() {
        TestFixtures.addToCart(store, userId, book.getId(), 2, 10.0);
        Order order = service.placeOrder(userId);
        double balanceAfterOrder = store.accounts.get(userId).getBalance();

        Order cancelled = service.cancelOrder(order.getId(), userId, "customer");

        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.cancelled);
        // Stock restored
        assertThat(store.books.get(book.getId()).getStock()).isEqualTo(5);
        // Wallet refunded
        assertThat(store.accounts.get(userId).getBalance())
                .isEqualTo(balanceAfterOrder + order.getTotalAmount());
        // Refund transaction recorded
        long refundTxCount = store.getUserTransactions(userId).stream()
                .map(store.transactions::get)
                .filter(tx -> tx.getType() == TransactionType.refund)
                .count();
        assertThat(refundTxCount).isEqualTo(1);
    }

    @Test
    void cancelOrder_byAdmin_succeeds() {
        TestFixtures.addToCart(store, userId, book.getId(), 1, 10.0);
        Order order = service.placeOrder(userId);
        assertThatCode(() -> service.cancelOrder(order.getId(), "admin-user-id", "admin"))
                .doesNotThrowAnyException();
    }

    @Test
    void cancelOrder_notPending_throws400() {
        TestFixtures.addToCart(store, userId, book.getId(), 1, 10.0);
        Order order = service.placeOrder(userId);
        order.setStatus(OrderStatus.confirmed);

        assertThatThrownBy(() -> service.cancelOrder(order.getId(), userId, "customer"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("pending");
    }

    @Test
    void cancelOrder_differentUser_throws403() {
        TestFixtures.addToCart(store, userId, book.getId(), 1, 10.0);
        Order order = service.placeOrder(userId);

        assertThatThrownBy(() -> service.cancelOrder(order.getId(), "other-user", "customer"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Access denied");
    }

    @Test
    void cancelOrder_unknownOrder_throws404() {
        assertThatThrownBy(() -> service.cancelOrder("ghost", userId, "customer"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not found");
    }

    // ── listUserOrders / listAllOrders ────────────────────────────────────────

    @Test
    void listUserOrders_returnsOnlyUserOrders() {
        // Another user with their own order
        var encoder = TestFixtures.passwordEncoder();
        var other = TestFixtures.addUser(store, encoder, "other@test.com", "p", "O", "customer");
        TestFixtures.credit(store, other.getId(), 100.0);
        TestFixtures.addToCart(store, other.getId(), book.getId(), 1, 10.0);
        service.placeOrder(other.getId());

        TestFixtures.addToCart(store, userId, book.getId(), 1, 10.0);
        service.placeOrder(userId);

        assertThat(service.listUserOrders(userId)).hasSize(1);
    }

    @Test
    void listAllOrders_statusFilter() {
        TestFixtures.addToCart(store, userId, book.getId(), 1, 10.0);
        Order order = service.placeOrder(userId);
        order.setStatus(OrderStatus.confirmed);

        assertThat(service.listAllOrders("confirmed")).hasSize(1);
        assertThat(service.listAllOrders("pending")).isEmpty();
        assertThat(service.listAllOrders(null)).hasSize(1);
    }
}
