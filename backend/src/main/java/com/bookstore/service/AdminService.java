package com.bookstore.service;

import com.bookstore.dto.request.AdjustBalanceRequest;
import com.bookstore.dto.request.CreateCouponRequest;
import com.bookstore.dto.request.UpdateOrderStatusRequest;
import com.bookstore.model.*;
import com.bookstore.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

@Service
@Transactional
public class AdminService {

    private static final Map<String, String> STATUS_PROGRESSION = Map.of(
            "pending", "confirmed",
            "confirmed", "shipped"
    );

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final OrderRepository orderRepository;
    private final CouponRepository couponRepository;

    public AdminService(UserRepository userRepository, AccountRepository accountRepository,
                        TransactionRepository transactionRepository, OrderRepository orderRepository,
                        CouponRepository couponRepository) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.orderRepository = orderRepository;
        this.couponRepository = couponRepository;
    }

    // ── Customers ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listCustomers() {
        return userRepository.findByRole("customer").stream()
                .map(u -> {
                    Account acc = accountRepository.findById(u.getId()).orElse(null);
                    long orderCount = orderRepository.findByUserIdOrderByCreatedAtDesc(u.getId()).size();
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", u.getId());
                    m.put("name", u.getName());
                    m.put("email", u.getEmail());
                    m.put("balance", acc != null ? acc.getBalance() : 0.0);
                    m.put("orderCount", (int) orderCount);
                    m.put("createdAt", u.getCreatedAt());
                    return m;
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getCustomerDetail(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
        Account acc = accountRepository.findById(userId).orElse(null);
        List<Transaction> transactions = transactionRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<Order> orders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId);

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
        userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
        Account account = accountRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));

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
        accountRepository.save(account);

        Transaction tx = new Transaction();
        tx.setId(UUID.randomUUID().toString());
        tx.setUserId(userId);
        tx.setType(type);
        tx.setAmount(amount);
        tx.setDescription(description);
        tx.setBalanceAfter(newBalance);
        tx.setCreatedAt(now);
        transactionRepository.save(tx);

        return Map.of("balance", newBalance, "transaction", tx);
    }

    // ── Orders ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Order> listAllOrders(String statusFilter) {
        if (statusFilter != null) {
            return orderRepository.findByStatusOrderByCreatedAtDesc(OrderStatus.valueOf(statusFilter));
        }
        return orderRepository.findAllByOrderByCreatedAtDesc();
    }

    public Order updateOrderStatus(String orderId, UpdateOrderStatusRequest req) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        String currentStatus = order.getStatus().name();
        String requiredNext = STATUS_PROGRESSION.get(currentStatus);
        if (requiredNext == null || !requiredNext.equals(req.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot transition from " + currentStatus + " to " + req.getStatus());
        }
        order.setStatus(OrderStatus.valueOf(req.getStatus()));
        order.setUpdatedAt(Instant.now());
        return orderRepository.save(order);
    }

    public Order refundOrder(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        if (order.getStatus() == OrderStatus.cancelled) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order already cancelled");
        }
        if (order.getStatus() == OrderStatus.shipped) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot refund shipped orders via this endpoint");
        }

        Instant now = Instant.now();
        Account account = accountRepository.findById(order.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Account not found for user: " + order.getUserId()));
        double refund = order.getTotalAmount();
        double newBalance = Math.round((account.getBalance() + refund) * 100.0) / 100.0;
        account.setBalance(newBalance);
        account.setUpdatedAt(now);
        accountRepository.save(account);

        Transaction tx = new Transaction();
        tx.setId(UUID.randomUUID().toString());
        tx.setUserId(order.getUserId());
        tx.setType(TransactionType.refund);
        tx.setAmount(refund);
        tx.setDescription("Admin refund for order " + orderId);
        tx.setOrderId(orderId);
        tx.setBalanceAfter(newBalance);
        tx.setCreatedAt(now);
        transactionRepository.save(tx);

        order.setStatus(OrderStatus.cancelled);
        order.setUpdatedAt(now);
        return orderRepository.save(order);
    }

    // ── Coupons ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Coupon> listCoupons() {
        return couponRepository.findAllByOrderByCreatedAtDesc();
    }

    public Coupon createCoupon(CreateCouponRequest req) {
        String code = req.getCode();
        if (couponRepository.existsByCode(code)) {
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
        coupon.setNewUserOnlyDays(req.getNewUserOnlyDays());
        coupon.setAllowedUserId(req.getAllowedUserId());
        try {
            coupon.setExpiresAt(req.getExpiresAt() != null ? Instant.parse(req.getExpiresAt()) : null);
        } catch (java.time.format.DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid expiresAt format — use ISO-8601 (e.g. 2026-12-31T23:59:59Z)");
        }
        coupon.setCreatedAt(Instant.now());
        return couponRepository.save(coupon);
    }

    public Coupon setActiveCoupon(String code, boolean active) {
        Coupon coupon = couponRepository.findByCode(code.toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Coupon not found"));
        coupon.setActive(active);
        return couponRepository.save(coupon);
    }
}
