package com.bookstore.service;

import com.bookstore.dto.request.AdjustBalanceRequest;
import com.bookstore.dto.request.CreateCouponRequest;
import com.bookstore.dto.request.UpdateOrderStatusRequest;
import com.bookstore.model.*;
import com.bookstore.store.InMemoryStore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AdminService {

    private static final Map<String, String> STATUS_PROGRESSION = Map.of(
            "pending", "confirmed",
            "confirmed", "shipped"
    );

    private final InMemoryStore store;

    public AdminService(InMemoryStore store) {
        this.store = store;
    }

    // ── Customers ───────────────────────────────────────────────────────────

    public List<Map<String, Object>> listCustomers() {
        return store.users.values().stream()
                .filter(u -> "customer".equals(u.getRole()))
                .map(u -> {
                    Account acc = store.accounts.get(u.getId());
                    List<String> orders = store.getUserOrders(u.getId());
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", u.getId());
                    m.put("name", u.getName());
                    m.put("email", u.getEmail());
                    m.put("balance", acc != null ? acc.getBalance() : 0.0);
                    m.put("orderCount", orders.size());
                    m.put("createdAt", u.getCreatedAt());
                    return m;
                })
                .collect(Collectors.toList());
    }

    public Map<String, Object> getCustomerDetail(String userId) {
        User user = store.users.get(userId);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found");
        }
        Account acc = store.accounts.get(userId);
        List<Transaction> transactions = store.getUserTransactions(userId).stream()
                .map(store.transactions::get)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Transaction::getCreatedAt).reversed())
                .collect(Collectors.toList());
        List<Order> orders = store.getUserOrders(userId).stream()
                .map(store.orders::get)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Order::getCreatedAt).reversed())
                .collect(Collectors.toList());

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", user.getId());
        m.put("name", user.getName());
        m.put("email", user.getEmail());
        m.put("role", user.getRole());
        m.put("createdAt", user.getCreatedAt());
        m.put("balance", acc != null ? acc.getBalance() : 0.0);
        m.put("transactions", transactions);
        m.put("orders", orders);
        return m;
    }

    public Map<String, Object> creditCustomer(String userId, AdjustBalanceRequest req) {
        return adjustBalance(userId, req.getAmount(), req.getDescription(), TransactionType.credit, false);
    }

    public Map<String, Object> debitCustomer(String userId, AdjustBalanceRequest req) {
        return adjustBalance(userId, req.getAmount(), req.getDescription(), TransactionType.debit, true);
    }

    private Map<String, Object> adjustBalance(String userId, double amount, String description,
                                               TransactionType type, boolean requireSufficientBalance) {
        User user = store.users.get(userId);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found");
        }
        Account account = store.accounts.get(userId);
        if (account == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
        }

        double newBalance;
        if (type == TransactionType.credit) {
            newBalance = Math.round((account.getBalance() + amount) * 100.0) / 100.0;
        } else {
            if (requireSufficientBalance && account.getBalance() < amount) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient balance");
            }
            newBalance = Math.round((account.getBalance() - amount) * 100.0) / 100.0;
        }

        Instant now = Instant.now();
        account.setBalance(newBalance);
        account.setUpdatedAt(now);

        Transaction tx = new Transaction();
        tx.setId(UUID.randomUUID().toString());
        tx.setUserId(userId);
        tx.setType(type);
        tx.setAmount(amount);
        tx.setDescription(description);
        tx.setBalanceAfter(newBalance);
        tx.setCreatedAt(now);
        store.transactions.put(tx.getId(), tx);
        store.getUserTransactions(userId).add(tx.getId());

        return Map.of("balance", newBalance, "transaction", tx);
    }

    // ── Orders ───────────────────────────────────────────────────────────────

    public List<Order> listAllOrders(String statusFilter) {
        return store.orders.values().stream()
                .filter(o -> statusFilter == null || o.getStatus().name().equals(statusFilter))
                .sorted(Comparator.comparing(Order::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    public Order updateOrderStatus(String orderId, UpdateOrderStatusRequest req) {
        Order order = store.orders.get(orderId);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
        String currentStatus = order.getStatus().name();
        String requiredNext = STATUS_PROGRESSION.get(currentStatus);
        if (requiredNext == null || !requiredNext.equals(req.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot transition from " + currentStatus + " to " + req.getStatus());
        }
        order.setStatus(OrderStatus.valueOf(req.getStatus()));
        order.setUpdatedAt(Instant.now());
        return order;
    }

    public Order refundOrder(String orderId) {
        synchronized (store) {
            Order order = store.orders.get(orderId);
            if (order == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
            }
            if (order.getStatus() == OrderStatus.cancelled) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order already cancelled");
            }
            if (order.getStatus() == OrderStatus.shipped) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot refund shipped orders via this endpoint");
            }

            Instant now = Instant.now();
            Account account = store.accounts.get(order.getUserId());
            if (account == null) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Account not found for user: " + order.getUserId());
            }
            double refund = order.getTotalAmount();
            double newBalance = Math.round((account.getBalance() + refund) * 100.0) / 100.0;
            account.setBalance(newBalance);
            account.setUpdatedAt(now);

            Transaction tx = new Transaction();
            tx.setId(UUID.randomUUID().toString());
            tx.setUserId(order.getUserId());
            tx.setType(TransactionType.refund);
            tx.setAmount(refund);
            tx.setDescription("Admin refund for order " + orderId);
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

    // ── Coupons ──────────────────────────────────────────────────────────────

    public List<Coupon> listCoupons() {
        return store.coupons.values().stream()
                .sorted(Comparator.comparing(Coupon::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    public Coupon createCoupon(CreateCouponRequest req) {
        String code = req.getCode(); // already UPPERCASE via getter
        if (store.couponCodeIndex.containsKey(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Coupon code already exists");
        }
        if (req.getType() == CouponType.percentage && req.getValue() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Percentage value cannot exceed 100");
        }

        Coupon coupon = new Coupon();
        coupon.setId(UUID.randomUUID().toString());
        coupon.setCode(code);
        coupon.setType(req.getType());
        coupon.setValue(req.getValue());
        coupon.setDescription(req.getDescription());
        coupon.setMinOrderAmount(req.getMinOrderAmount());
        coupon.setMaxUses(req.getMaxUses());
        coupon.setUsedCount(0);
        coupon.setActive(true);
        try {
            coupon.setExpiresAt(req.getExpiresAt() != null ? Instant.parse(req.getExpiresAt()) : null);
        } catch (java.time.format.DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid expiresAt format — use ISO-8601 (e.g. 2026-12-31T23:59:59Z)");
        }
        coupon.setCreatedAt(Instant.now());

        store.coupons.put(coupon.getId(), coupon);
        store.couponCodeIndex.put(code, coupon.getId());
        return coupon;
    }

    public Coupon setActiveCoupon(String code, boolean active) {
        String couponId = store.couponCodeIndex.get(code.toUpperCase());
        if (couponId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Coupon not found");
        }
        Coupon coupon = store.coupons.get(couponId);
        coupon.setActive(active);
        return coupon;
    }
}
