package com.bookstore.service;

import com.bookstore.dto.request.AdjustBalanceRequest;
import com.bookstore.dto.request.CreateCouponRequest;
import com.bookstore.dto.request.UpdateOrderStatusRequest;
import com.bookstore.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class AdminServiceTest {

    private MockRepositories mr;
    private AdminService service;
    private OrderService orderService;
    private String customerId;
    private Book book;

    @BeforeEach
    void setUp() {
        mr = new MockRepositories();
        PasswordEncoder encoder = TestFixtures.passwordEncoder();
        service = new AdminService(mr.userRepo, mr.accountRepo, mr.txRepo, mr.orderRepo, mr.couponRepo);
        orderService = new OrderService(mr.cartRepo, mr.bookRepo, mr.orderRepo, mr.accountRepo,
                mr.txRepo, mr.userRepo, new CouponHelper(mr.couponRepo));

        var customer = TestFixtures.addUser(mr, encoder, "cust@test.com", "p", "Customer", "customer");
        customerId = customer.getId();
        TestFixtures.addUser(mr, encoder, "admin@test.com", "p", "Admin", "admin");

        book = TestFixtures.addBook(mr, "Book", "Author", "Genre", 10.0, 10, "9780000000001");
        TestFixtures.credit(mr, customerId, 100.0);
    }

    // ── listCustomers ─────────────────────────────────────────────────────────

    @Test
    void listCustomers_excludesAdmins() {
        List<Map<String, Object>> customers = service.listCustomers();
        assertThat(customers).hasSize(1);
        assertThat(customers.get(0).get("email")).isEqualTo("cust@test.com");
    }

    @Test
    void listCustomers_includesBalanceAndOrderCount() {
        Map<String, Object> c = service.listCustomers().get(0);
        assertThat((Double) c.get("balance")).isEqualTo(100.0);
        assertThat((Integer) c.get("orderCount")).isEqualTo(0);
    }

    // ── getCustomerDetail ─────────────────────────────────────────────────────

    @Test
    void getCustomerDetail_returnsUserWithTransactionsAndOrders() {
        TestFixtures.addToCart(mr, customerId, book.getId(), 1, 10.0);
        orderService.placeOrder(customerId);

        Map<String, Object> detail = service.getCustomerDetail(customerId);
        assertThat(detail.get("email")).isEqualTo("cust@test.com");
        assertThat((List<?>) detail.get("orders")).hasSize(1);
        assertThat((List<?>) detail.get("transactions")).hasSize(1);
    }

    @Test
    void getCustomerDetail_unknownUser_throws404() {
        assertThatThrownBy(() -> service.getCustomerDetail("ghost"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not found");
    }

    // ── creditCustomer ────────────────────────────────────────────────────────

    @Test
    void creditCustomer_increasesBalance() {
        service.creditCustomer(customerId, adjustReq(50.0, "Bonus"));
        assertThat(mr.accounts.get(customerId).getBalance()).isEqualTo(150.0);
    }

    @Test
    void creditCustomer_recordsTransaction() {
        service.creditCustomer(customerId, adjustReq(50.0, "Bonus"));
        long credits = mr.getUserTransactions(customerId).stream()
                .filter(tx -> tx.getType() == TransactionType.credit)
                .count();
        assertThat(credits).isEqualTo(1);
    }

    @Test
    void creditCustomer_unknownUser_throws404() {
        assertThatThrownBy(() -> service.creditCustomer("ghost", adjustReq(10.0, "x")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not found");
    }

    // ── debitCustomer ─────────────────────────────────────────────────────────

    @Test
    void debitCustomer_decreasesBalance() {
        service.debitCustomer(customerId, adjustReq(30.0, "Fee"));
        assertThat(mr.accounts.get(customerId).getBalance()).isEqualTo(70.0);
    }

    @Test
    void debitCustomer_insufficientBalance_throws400() {
        assertThatThrownBy(() -> service.debitCustomer(customerId, adjustReq(999.0, "Fee")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Insufficient balance");
    }

    // ── updateOrderStatus ─────────────────────────────────────────────────────

    @Test
    void updateOrderStatus_pendingToConfirmed_succeeds() {
        Order order = placeTestOrder();
        Order updated = service.updateOrderStatus(order.getId(), statusReq("confirmed"));
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.confirmed);
    }

    @Test
    void updateOrderStatus_confirmedToShipped_succeeds() {
        Order order = placeTestOrder();
        service.updateOrderStatus(order.getId(), statusReq("confirmed"));
        Order updated = service.updateOrderStatus(order.getId(), statusReq("shipped"));
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.shipped);
    }

    @Test
    void updateOrderStatus_pendingToShipped_illegalSkip_throws400() {
        Order order = placeTestOrder();
        assertThatThrownBy(() -> service.updateOrderStatus(order.getId(), statusReq("shipped")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Cannot transition");
    }

    @Test
    void updateOrderStatus_cancelledOrder_throws400() {
        Order order = placeTestOrder();
        order.setStatus(OrderStatus.cancelled);
        assertThatThrownBy(() -> service.updateOrderStatus(order.getId(), statusReq("confirmed")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Cannot transition");
    }

    @Test
    void updateOrderStatus_unknownOrder_throws404() {
        assertThatThrownBy(() -> service.updateOrderStatus("ghost", statusReq("confirmed")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not found");
    }

    // ── refundOrder ───────────────────────────────────────────────────────────

    @Test
    void refundOrder_pendingOrder_refundsWalletAndCancels() {
        Order order = placeTestOrder();
        double balanceBefore = mr.accounts.get(customerId).getBalance();

        Order refunded = service.refundOrder(order.getId());

        assertThat(refunded.getStatus()).isEqualTo(OrderStatus.cancelled);
        assertThat(mr.accounts.get(customerId).getBalance())
                .isEqualTo(balanceBefore + order.getTotalAmount());
    }

    @Test
    void refundOrder_doesNotRestoreStock() {
        Order order = placeTestOrder();
        int stockAfterOrder = mr.books.get(book.getId()).getStock();

        service.refundOrder(order.getId());

        assertThat(mr.books.get(book.getId()).getStock()).isEqualTo(stockAfterOrder);
    }

    @Test
    void refundOrder_alreadyCancelled_throws400() {
        Order order = placeTestOrder();
        order.setStatus(OrderStatus.cancelled);
        assertThatThrownBy(() -> service.refundOrder(order.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already cancelled");
    }

    @Test
    void refundOrder_shippedOrder_throws400() {
        Order order = placeTestOrder();
        order.setStatus(OrderStatus.shipped);
        assertThatThrownBy(() -> service.refundOrder(order.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Cannot refund shipped");
    }

    // ── createCoupon ──────────────────────────────────────────────────────────

    @Test
    void createCoupon_valid_storesAndReturns() {
        Coupon c = service.createCoupon(couponReq("PROMO", CouponType.percentage, 20, 0, null, null));
        assertThat(c.getCode()).isEqualTo("PROMO");
        assertThat(mr.coupons.values().stream().anyMatch(x -> x.getCode().equals("PROMO"))).isTrue();
    }

    @Test
    void createCoupon_duplicateCode_throws409() {
        service.createCoupon(couponReq("DUP", CouponType.fixed, 5, 0, null, null));
        assertThatThrownBy(() ->
                service.createCoupon(couponReq("DUP", CouponType.fixed, 5, 0, null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void createCoupon_percentageOver100_throws400() {
        assertThatThrownBy(() ->
                service.createCoupon(couponReq("OVER", CouponType.percentage, 101, 0, null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("cannot exceed 100");
    }

    @Test
    void createCoupon_invalidExpiresAt_throws400() {
        assertThatThrownBy(() ->
                service.createCoupon(couponReq("EXP", CouponType.percentage, 10, 0, null, "not-a-date")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid expiresAt format");
    }

    @Test
    void createCoupon_validExpiresAt_parsedCorrectly() {
        Coupon c = service.createCoupon(
                couponReq("TIMED", CouponType.percentage, 10, 0, null, "2030-01-01T00:00:00Z"));
        assertThat(c.getExpiresAt()).isNotNull();
    }

    // ── setActiveCoupon ───────────────────────────────────────────────────────

    @Test
    void deactivateCoupon_setsInactive() {
        TestFixtures.addCoupon(mr, "CODE", CouponType.fixed, 5, 0);
        Coupon c = service.setActiveCoupon("CODE", false);
        assertThat(c.isActive()).isFalse();
    }

    @Test
    void activateCoupon_setsActive() {
        Coupon c = TestFixtures.addCoupon(mr, "OFF", CouponType.fixed, 5, 0);
        c.setActive(false);
        service.setActiveCoupon("OFF", true);
        assertThat(mr.coupons.get(c.getId()).isActive()).isTrue();
    }

    @Test
    void setActiveCoupon_unknownCode_throws404() {
        assertThatThrownBy(() -> service.setActiveCoupon("GHOST", true))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not found");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Order placeTestOrder() {
        TestFixtures.addToCart(mr, customerId, book.getId(), 1, book.getPrice());
        return orderService.placeOrder(customerId);
    }

    private static AdjustBalanceRequest adjustReq(double amount, String desc) {
        AdjustBalanceRequest r = new AdjustBalanceRequest();
        r.setAmount(amount);
        r.setDescription(desc);
        return r;
    }

    private static UpdateOrderStatusRequest statusReq(String status) {
        UpdateOrderStatusRequest r = new UpdateOrderStatusRequest();
        r.setStatus(status);
        return r;
    }

    private static CreateCouponRequest couponReq(String code, CouponType type, double value,
                                                  double minOrder, Integer maxUses, String expiresAt) {
        CreateCouponRequest r = new CreateCouponRequest();
        r.setCode(code);
        r.setType(type);
        r.setValue(value);
        r.setDescription("Test");
        r.setMinOrderAmount(minOrder);
        r.setMaxUses(maxUses);
        r.setExpiresAt(expiresAt);
        return r;
    }
}
