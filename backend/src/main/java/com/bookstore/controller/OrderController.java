package com.bookstore.controller;

import com.bookstore.model.Order;
import com.bookstore.security.BookstorePrincipal;
import com.bookstore.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<Order> placeOrder(@AuthenticationPrincipal BookstorePrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.placeOrder(principal.userId()));
    }

    @GetMapping
    public ResponseEntity<List<Order>> listOrders(@AuthenticationPrincipal BookstorePrincipal principal) {
        return ResponseEntity.ok(orderService.listUserOrders(principal.userId()));
    }

    // Must be declared before /{id} to avoid routing ambiguity
    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Order>> listAllOrders(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(orderService.listAllOrders(status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrder(
            @AuthenticationPrincipal BookstorePrincipal principal,
            @PathVariable String id) {
        return ResponseEntity.ok(orderService.getOrder(id, principal.userId(), principal.role()));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<Order> cancelOrder(
            @AuthenticationPrincipal BookstorePrincipal principal,
            @PathVariable String id) {
        return ResponseEntity.ok(orderService.cancelOrder(id, principal.userId(), principal.role()));
    }
}
