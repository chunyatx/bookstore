package com.bookstore.service;

import com.bookstore.model.*;
import com.bookstore.store.InMemoryStore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private final InMemoryStore store;
    private final CouponHelper couponHelper;

    public OrderService(InMemoryStore store, CouponHelper couponHelper) {
        this.store = store;
        this.couponHelper = couponHelper;
    }

    public Order placeOrder(String userId) {
        synchronized (store) {
            Cart cart = store.carts.get(userId);
            if (cart == null || cart.getItems().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart is empty");
            }

            // 1. Validate all items have sufficient stock
            for (CartItem item : cart.getItems()) {
                Book book = store.books.get(item.getBookId());
                if (book == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Book no longer available: " + item.getBookId());
                }
                if (book.getStock() < item.getQuantity()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Insufficient stock for: " + book.getTitle());
                }
            }

            // 2. Compute subtotal
            double subtotal = 0;
            for (CartItem item : cart.getItems()) {
                subtotal += item.getPriceAtAdd() * item.getQuantity();
            }
            subtotal = Math.round(subtotal * 100.0) / 100.0;

            // 3. Re-validate coupon (silently ignore if invalid)
            double discountAmount = 0;
            String appliedCouponCode = null;
            Coupon coupon = null;
            if (cart.getCouponCode() != null) {
                coupon = couponHelper.tryValidateCoupon(cart.getCouponCode(), subtotal);
                if (coupon != null) {
                    discountAmount = couponHelper.computeDiscount(coupon, subtotal);
                    appliedCouponCode = coupon.getCode();
                }
            }

            double totalAmount = Math.max(0, Math.round((subtotal - discountAmount) * 100.0) / 100.0);

            // 4. Check wallet balance
            Account account = store.accounts.get(userId);
            if (account == null || account.getBalance() < totalAmount) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Insufficient wallet balance. Balance: " +
                        (account != null ? account.getBalance() : 0) +
                        ", Required: " + totalAmount);
            }

            // 5. All validations passed — begin mutations

            // Decrement stock
            for (CartItem item : cart.getItems()) {
                Book book = store.books.get(item.getBookId());
                book.setStock(book.getStock() - item.getQuantity());
                book.setUpdatedAt(Instant.now());
            }

            // Increment coupon usage
            if (coupon != null) {
                coupon.setUsedCount(coupon.getUsedCount() + 1);
            }

            // Build order items (snapshot title + priceAtAdd)
            List<OrderItem> orderItems = new ArrayList<>();
            for (CartItem item : cart.getItems()) {
                Book book = store.books.get(item.getBookId());
                orderItems.add(new OrderItem(
                        item.getBookId(),
                        book.getTitle(),
                        item.getQuantity(),
                        item.getPriceAtAdd()
                ));
            }

            // Create order
            Instant now = Instant.now();
            Order order = new Order();
            order.setId(UUID.randomUUID().toString());
            order.setUserId(userId);
            order.setItems(orderItems);
            order.setSubtotal(subtotal);
            order.setDiscountAmount(discountAmount);
            order.setTotalAmount(totalAmount);
            order.setCouponCode(appliedCouponCode);
            order.setStatus(OrderStatus.pending);
            order.setCreatedAt(now);
            order.setUpdatedAt(now);

            store.orders.put(order.getId(), order);
            store.getUserOrders(userId).add(order.getId());

            // Deduct wallet
            String desc = "Payment for order " + order.getId() +
                    (appliedCouponCode != null ? " (coupon: " + appliedCouponCode + ")" : "");
            double newBalance = Math.round((account.getBalance() - totalAmount) * 100.0) / 100.0;
            account.setBalance(newBalance);
            account.setUpdatedAt(now);

            Transaction tx = new Transaction();
            tx.setId(UUID.randomUUID().toString());
            tx.setUserId(userId);
            tx.setType(TransactionType.order_payment);
            tx.setAmount(totalAmount);
            tx.setDescription(desc);
            tx.setOrderId(order.getId());
            tx.setBalanceAfter(newBalance);
            tx.setCreatedAt(now);
            store.transactions.put(tx.getId(), tx);
            store.getUserTransactions(userId).add(tx.getId());

            // Clear cart
            cart.setItems(new ArrayList<>());
            cart.setCouponCode(null);
            cart.setUpdatedAt(now);

            return order;
        }
    }

    public List<Order> listUserOrders(String userId) {
        List<String> orderIds = store.getUserOrders(userId);
        return orderIds.stream()
                .map(store.orders::get)
                .filter(o -> o != null)
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .collect(Collectors.toList());
    }

    public Order getOrder(String orderId, String userId, String role) {
        Order order = store.orders.get(orderId);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
        if (!"admin".equals(role) && !order.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        return order;
    }

    public Order cancelOrder(String orderId, String userId, String role) {
        synchronized (store) {
            Order order = store.orders.get(orderId);
            if (order == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
            }
            if (!"admin".equals(role) && !order.getUserId().equals(userId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
            }
            if (order.getStatus() != OrderStatus.pending) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Only pending orders can be cancelled");
            }

            Instant now = Instant.now();

            // Restore stock
            for (OrderItem item : order.getItems()) {
                Book book = store.books.get(item.getBookId());
                if (book != null) {
                    book.setStock(book.getStock() + item.getQuantity());
                    book.setUpdatedAt(now);
                }
            }

            // Refund wallet
            Account account = store.accounts.get(order.getUserId());
            if (account == null) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Account not found for user: " + order.getUserId());
            }
            double refundAmount = order.getTotalAmount();
            double newBalance = Math.round((account.getBalance() + refundAmount) * 100.0) / 100.0;
            account.setBalance(newBalance);
            account.setUpdatedAt(now);

            Transaction tx = new Transaction();
            tx.setId(UUID.randomUUID().toString());
            tx.setUserId(order.getUserId());
            tx.setType(TransactionType.refund);
            tx.setAmount(refundAmount);
            tx.setDescription("Refund for cancelled order " + orderId);
            tx.setOrderId(orderId);
            tx.setBalanceAfter(newBalance);
            tx.setCreatedAt(now);
            store.transactions.put(tx.getId(), tx);
            store.getUserTransactions(order.getUserId()).add(tx.getId());

            order.setStatus(OrderStatus.cancelled);
            order.setUpdatedAt(now);
            return order;
        }
    }

    public List<Order> listAllOrders(String statusFilter) {
        return store.orders.values().stream()
                .filter(o -> statusFilter == null || o.getStatus().name().equals(statusFilter))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .collect(Collectors.toList());
    }
}
