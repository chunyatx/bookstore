package com.bookstore.service;

import com.bookstore.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.*;

class OrderServiceTest {

    private MockRepositories mr;
    private OrderService service;
    private String userId;
    private Book book;

    @BeforeEach
    void setUp() {
        mr = new MockRepositories();
        PasswordEncoder encoder = TestFixtures.passwordEncoder();
        service = new OrderService(mr.cartRepo, mr.bookRepo, mr.orderRepo, mr.accountRepo,
                mr.txRepo, mr.userRepo, new CouponHelper(mr.couponRepo));

        var user = TestFixtures.addUser(mr, encoder, "user@test.com", "pass", "User", "customer");
        userId = user.getId();
        book = TestFixtures.addBook(mr, "Dune", "Herbert", "Sci-Fi", 10.0, 5, "9780000000001");
        TestFixtures.credit(mr, userId, 200.0);
    }

    // ── placeOrder ────────────────────────────────────────────────────────────

    @Test
    void placeOrder_happyPath_createsOrderAndDeductsWallet() {
        TestFixtures.addToCart(mr, userId, book.getId(), 2, book.getPrice());

        Order order = service.placeOrder(userId);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.pending);
        assertThat(order.getTotalAmount()).isEqualTo(20.0);
        assertThat(order.getItems()).hasSize(1);
        assertThat(order.getItems().get(0).getTitle()).isEqualTo("Dune");

        assertThat(mr.accounts.get(userId).getBalance()).isEqualTo(180.0);
        assertThat(mr.books.get(book.getId()).getStock()).isEqualTo(3);
        assertThat(mr.carts.get(userId).getItems()).isEmpty();
        assertThat(mr.getUserTransactions(userId)).hasSize(1);
    }

    @Test
    void placeOrder_snapshotsPriceFromCart() {
        TestFixtures.addToCart(mr, userId, book.getId(), 1, 10.0);
        book.setPrice(99.99);

        Order order = service.placeOrder(userId);
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
        TestFixtures.addToCart(mr, userId, book.getId(), 100, book.getPrice());
        assertThatThrownBy(() -> service.placeOrder(userId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Insufficient stock");
    }

    @Test
    void placeOrder_insufficientWallet_throws400() {
        TestFixtures.credit(mr, userId, -200.0);  // drain wallet to 0
        TestFixtures.addToCart(mr, userId, book.getId(), 1, 10.0);
        assertThatThrownBy(() -> service.placeOrder(userId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Insufficient wallet balance");
    }

    @Test
    void placeOrder_withValidCoupon_appliesDiscount() {
        TestFixtures.addCoupon(mr, "SAVE10", CouponType.percentage, 10, 0);
        TestFixtures.addToCart(mr, userId, book.getId(), 2, 10.0);
        mr.carts.get(userId).setCouponCode("SAVE10");

        Order order = service.placeOrder(userId);

        assertThat(order.getDiscountAmount()).isEqualTo(2.0);
        assertThat(order.getTotalAmount()).isEqualTo(18.0);
        assertThat(order.getCouponCode()).isEqualTo("SAVE10");
        assertThat(mr.coupons.values().iterator().next().getUsedCount()).isEqualTo(1);
    }

    @Test
    void placeOrder_withInvalidCoupon_ignoresDiscount() {
        TestFixtures.addToCart(mr, userId, book.getId(), 1, 10.0);
        mr.carts.get(userId).setCouponCode("INVALID_CODE");

        Order order = service.placeOrder(userId);

        assertThat(order.getDiscountAmount()).isEqualTo(0.0);
        assertThat(order.getTotalAmount()).isEqualTo(10.0);
    }

    @Test
    void placeOrder_recordsTransaction() {
        TestFixtures.addToCart(mr, userId, book.getId(), 1, 10.0);
        service.placeOrder(userId);

        var txs = mr.getUserTransactions(userId);
        assertThat(txs).hasSize(1);
        assertThat(txs.get(0).getType()).isEqualTo(TransactionType.order_payment);
    }

    // ── listUserOrders ────────────────────────────────────────────────────────

    @Test
    void listUserOrders_returnsOnlyUserOrders() {
        var user2 = TestFixtures.addUser(mr, TestFixtures.passwordEncoder(), "u2@t.com", "p", "U2", "customer");
        TestFixtures.credit(mr, user2.getId(), 100.0);
        Book b2 = TestFixtures.addBook(mr, "B2", "A", "G", 10.0, 5, "9780000000002");
        TestFixtures.addToCart(mr, userId, book.getId(), 1, 10.0);
        TestFixtures.addToCart(mr, user2.getId(), b2.getId(), 1, 10.0);
        service.placeOrder(userId);
        service.placeOrder(user2.getId());

        assertThat(service.listUserOrders(userId)).hasSize(1);
    }

    // ── getOrder ──────────────────────────────────────────────────────────────

    @Test
    void getOrder_existingOrder_returnsIt() {
        TestFixtures.addToCart(mr, userId, book.getId(), 1, 10.0);
        Order placed = service.placeOrder(userId);

        Order found = service.getOrder(placed.getId(), userId, "customer");
        assertThat(found.getId()).isEqualTo(placed.getId());
    }

    @Test
    void getOrder_wrongUser_throws403() {
        TestFixtures.addToCart(mr, userId, book.getId(), 1, 10.0);
        Order placed = service.placeOrder(userId);

        assertThatThrownBy(() -> service.getOrder(placed.getId(), "other-user", "customer"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Access denied");
    }

    @Test
    void getOrder_adminCanAccessAny() {
        TestFixtures.addToCart(mr, userId, book.getId(), 1, 10.0);
        Order placed = service.placeOrder(userId);

        assertThatCode(() -> service.getOrder(placed.getId(), "admin-id", "admin"))
                .doesNotThrowAnyException();
    }

    // ── cancelOrder ───────────────────────────────────────────────────────────

    @Test
    void cancelOrder_pendingOrder_cancelsAndRefunds() {
        TestFixtures.addToCart(mr, userId, book.getId(), 1, 10.0);
        Order placed = service.placeOrder(userId);
        double balanceAfterOrder = mr.accounts.get(userId).getBalance();

        Order cancelled = service.cancelOrder(placed.getId(), userId, "customer");

        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.cancelled);
        assertThat(mr.accounts.get(userId).getBalance()).isEqualTo(balanceAfterOrder + placed.getTotalAmount());
        assertThat(mr.books.get(book.getId()).getStock()).isEqualTo(5); // restored
        assertThat(mr.getUserTransactions(userId)).hasSize(2); // payment + refund
    }

    @Test
    void cancelOrder_nonPendingOrder_throws400() {
        TestFixtures.addToCart(mr, userId, book.getId(), 1, 10.0);
        Order placed = service.placeOrder(userId);
        placed.setStatus(OrderStatus.confirmed);

        assertThatThrownBy(() -> service.cancelOrder(placed.getId(), userId, "customer"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Only pending");
    }

    @Test
    void cancelOrder_unknownOrder_throws404() {
        assertThatThrownBy(() -> service.cancelOrder("ghost", userId, "customer"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not found");
    }
}
