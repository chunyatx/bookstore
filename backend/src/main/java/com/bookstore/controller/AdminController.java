package com.bookstore.controller;

import com.bookstore.dto.request.AdjustBalanceRequest;
import com.bookstore.dto.request.CreateCouponRequest;
import com.bookstore.dto.request.SetAccountLevelRequest;
import com.bookstore.dto.request.UpdateOrderStatusRequest;
import com.bookstore.model.Coupon;
import com.bookstore.model.Order;
import com.bookstore.service.AdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    // ── Customers ───────────────────────────────────────────────────────────

    @GetMapping("/customers")
    public ResponseEntity<List<Map<String, Object>>> listCustomers() {
        return ResponseEntity.ok(adminService.listCustomers());
    }

    @GetMapping("/customers/{userId}")
    public ResponseEntity<Map<String, Object>> getCustomer(@PathVariable String userId) {
        return ResponseEntity.ok(adminService.getCustomerDetail(userId));
    }

    @PostMapping("/customers/{userId}/credit")
    public ResponseEntity<Map<String, Object>> creditCustomer(
            @PathVariable String userId,
            @Valid @RequestBody AdjustBalanceRequest req) {
        return ResponseEntity.ok(adminService.creditCustomer(userId, req));
    }

    @PostMapping("/customers/{userId}/debit")
    public ResponseEntity<Map<String, Object>> debitCustomer(
            @PathVariable String userId,
            @Valid @RequestBody AdjustBalanceRequest req) {
        return ResponseEntity.ok(adminService.debitCustomer(userId, req));
    }

    @PatchMapping("/customers/{userId}/level")
    public ResponseEntity<Map<String, Object>> setCustomerLevel(
            @PathVariable String userId,
            @Valid @RequestBody SetAccountLevelRequest req) {
        return ResponseEntity.ok(adminService.setCustomerLevel(userId, req.getLevel()));
    }

    // ── Orders ───────────────────────────────────────────────────────────────

    @GetMapping("/orders")
    public ResponseEntity<List<Order>> listOrders(@RequestParam(required = false) String status) {
        return ResponseEntity.ok(adminService.listAllOrders(status));
    }

    @PatchMapping("/orders/{orderId}/status")
    public ResponseEntity<Order> updateOrderStatus(
            @PathVariable String orderId,
            @Valid @RequestBody UpdateOrderStatusRequest req) {
        return ResponseEntity.ok(adminService.updateOrderStatus(orderId, req));
    }

    @PostMapping("/orders/{orderId}/refund")
    public ResponseEntity<Order> refundOrder(@PathVariable String orderId) {
        return ResponseEntity.ok(adminService.refundOrder(orderId));
    }

    // ── Coupons ──────────────────────────────────────────────────────────────

    @GetMapping("/coupons")
    public ResponseEntity<List<Coupon>> listCoupons() {
        return ResponseEntity.ok(adminService.listCoupons());
    }

    @PostMapping("/coupons")
    public ResponseEntity<Coupon> createCoupon(@Valid @RequestBody CreateCouponRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.createCoupon(req));
    }

    @PatchMapping("/coupons/{code}/activate")
    public ResponseEntity<Coupon> activateCoupon(@PathVariable String code) {
        return ResponseEntity.ok(adminService.setActiveCoupon(code, true));
    }

    @PatchMapping("/coupons/{code}/deactivate")
    public ResponseEntity<Coupon> deactivateCoupon(@PathVariable String code) {
        return ResponseEntity.ok(adminService.setActiveCoupon(code, false));
    }
}
