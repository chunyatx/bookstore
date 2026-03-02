package com.bookstore.service;

import com.bookstore.dto.request.AddToCartRequest;
import com.bookstore.dto.request.ApplyCouponRequest;
import com.bookstore.dto.request.UpdateCartItemRequest;
import com.bookstore.dto.response.EnrichedCartResponse;
import com.bookstore.model.*;
import com.bookstore.store.InMemoryStore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class CartService {

    private final InMemoryStore store;
    private final CouponHelper couponHelper;

    public CartService(InMemoryStore store, CouponHelper couponHelper) {
        this.store = store;
        this.couponHelper = couponHelper;
    }

    public EnrichedCartResponse getCart(String userId) {
        Cart cart = store.carts.computeIfAbsent(userId, Cart::new);
        return enrichCart(cart);
    }

    public EnrichedCartResponse addItem(String userId, AddToCartRequest req) {
        Book book = store.books.get(req.getBookId());
        if (book == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found");
        }

        Cart cart = store.carts.computeIfAbsent(userId, Cart::new);
        Optional<CartItem> existing = cart.getItems().stream()
                .filter(i -> i.getBookId().equals(req.getBookId()))
                .findFirst();

        int newQty = req.getQuantity() + (existing.map(CartItem::getQuantity).orElse(0));
        if (newQty > book.getStock()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Insufficient stock. Available: " + book.getStock());
        }

        if (existing.isPresent()) {
            existing.get().setQuantity(newQty);
        } else {
            cart.getItems().add(new CartItem(book.getId(), newQty, book.getPrice()));
        }
        cart.setUpdatedAt(Instant.now());
        return enrichCart(cart);
    }

    public EnrichedCartResponse updateItem(String userId, String bookId, UpdateCartItemRequest req) {
        Cart cart = store.carts.get(userId);
        if (cart == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart not found");
        }

        if (req.getQuantity() == 0) {
            cart.getItems().removeIf(i -> i.getBookId().equals(bookId));
        } else {
            Book book = store.books.get(bookId);
            if (book == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found");
            }
            if (req.getQuantity() > book.getStock()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Insufficient stock. Available: " + book.getStock());
            }
            Optional<CartItem> item = cart.getItems().stream()
                    .filter(i -> i.getBookId().equals(bookId))
                    .findFirst();
            if (item.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not in cart");
            }
            item.get().setQuantity(req.getQuantity());
        }
        cart.setUpdatedAt(Instant.now());
        return enrichCart(cart);
    }

    public void clearCart(String userId) {
        Cart cart = store.carts.get(userId);
        if (cart != null) {
            cart.setItems(new ArrayList<>());
            cart.setCouponCode(null);
            cart.setUpdatedAt(Instant.now());
        }
    }

    public EnrichedCartResponse applyCoupon(String userId, ApplyCouponRequest req) {
        Cart cart = store.carts.computeIfAbsent(userId, Cart::new);
        double subtotal = computeSubtotal(cart);
        // Validate throws if invalid
        couponHelper.validateCoupon(req.getCode(), subtotal);
        cart.setCouponCode(req.getCode());
        cart.setUpdatedAt(Instant.now());
        return enrichCart(cart);
    }

    public EnrichedCartResponse removeCoupon(String userId) {
        Cart cart = store.carts.computeIfAbsent(userId, Cart::new);
        cart.setCouponCode(null);
        cart.setUpdatedAt(Instant.now());
        return enrichCart(cart);
    }

    public EnrichedCartResponse enrichCart(Cart cart) {
        EnrichedCartResponse resp = new EnrichedCartResponse();
        resp.setUserId(cart.getUserId());
        resp.setUpdatedAt(cart.getUpdatedAt());
        resp.setCouponCode(cart.getCouponCode());

        List<EnrichedCartResponse.EnrichedCartItem> enrichedItems = new ArrayList<>();
        double subtotal = 0;

        for (CartItem item : cart.getItems()) {
            Book book = store.books.get(item.getBookId());
            String title = book != null ? book.getTitle() : "Unknown";
            String author = book != null ? book.getAuthor() : "Unknown";
            enrichedItems.add(new EnrichedCartResponse.EnrichedCartItem(
                    item.getBookId(), title, author, item.getQuantity(), item.getPriceAtAdd()));
            subtotal += item.getPriceAtAdd() * item.getQuantity();
        }
        subtotal = Math.round(subtotal * 100.0) / 100.0;

        resp.setItems(enrichedItems);
        resp.setSubtotal(subtotal);

        double discountAmount = 0;
        Map<String, Object> couponDetails = null;

        if (cart.getCouponCode() != null) {
            Coupon coupon = couponHelper.tryValidateCoupon(cart.getCouponCode(), subtotal);
            if (coupon != null) {
                discountAmount = couponHelper.computeDiscount(coupon, subtotal);
                couponDetails = new HashMap<>();
                couponDetails.put("code", coupon.getCode());
                couponDetails.put("type", coupon.getType().name());
                couponDetails.put("value", coupon.getValue());
                couponDetails.put("description", coupon.getDescription());
            } else {
                // Coupon became invalid (expired, maxed out) — silently clear
                cart.setCouponCode(null);
            }
        }

        resp.setDiscountAmount(discountAmount);
        resp.setFinalTotal(Math.max(0, Math.round((subtotal - discountAmount) * 100.0) / 100.0));
        resp.setCoupon(couponDetails);
        return resp;
    }

    private double computeSubtotal(Cart cart) {
        double subtotal = 0;
        for (CartItem item : cart.getItems()) {
            subtotal += item.getPriceAtAdd() * item.getQuantity();
        }
        return Math.round(subtotal * 100.0) / 100.0;
    }
}
