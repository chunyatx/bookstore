package com.bookstore.controller;

import com.bookstore.dto.request.AddToCartRequest;
import com.bookstore.dto.request.ApplyCouponRequest;
import com.bookstore.dto.request.UpdateCartItemRequest;
import com.bookstore.dto.response.EnrichedCartResponse;
import com.bookstore.security.BookstorePrincipal;
import com.bookstore.service.CartService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public ResponseEntity<EnrichedCartResponse> getCart(@AuthenticationPrincipal BookstorePrincipal principal) {
        return ResponseEntity.ok(cartService.getCart(principal.userId()));
    }

    @PostMapping("/items")
    public ResponseEntity<EnrichedCartResponse> addItem(
            @AuthenticationPrincipal BookstorePrincipal principal,
            @Valid @RequestBody AddToCartRequest req) {
        return ResponseEntity.ok(cartService.addItem(principal.userId(), req));
    }

    @PatchMapping("/items/{bookId}")
    public ResponseEntity<EnrichedCartResponse> updateItem(
            @AuthenticationPrincipal BookstorePrincipal principal,
            @PathVariable String bookId,
            @Valid @RequestBody UpdateCartItemRequest req) {
        return ResponseEntity.ok(cartService.updateItem(principal.userId(), bookId, req));
    }

    @DeleteMapping
    public ResponseEntity<Void> clearCart(@AuthenticationPrincipal BookstorePrincipal principal) {
        cartService.clearCart(principal.userId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/coupon")
    public ResponseEntity<EnrichedCartResponse> applyCoupon(
            @AuthenticationPrincipal BookstorePrincipal principal,
            @Valid @RequestBody ApplyCouponRequest req) {
        return ResponseEntity.ok(cartService.applyCoupon(principal.userId(), req));
    }

    @DeleteMapping("/coupon")
    public ResponseEntity<EnrichedCartResponse> removeCoupon(@AuthenticationPrincipal BookstorePrincipal principal) {
        return ResponseEntity.ok(cartService.removeCoupon(principal.userId()));
    }
}
