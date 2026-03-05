package com.bookstore.service;

import com.bookstore.model.*;
import com.bookstore.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class OrderService {

    private final CartRepository cartRepository;
    private final BookRepository bookRepository;
    private final OrderRepository orderRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final CouponHelper couponHelper;

    public OrderService(CartRepository cartRepository, BookRepository bookRepository,
                        OrderRepository orderRepository, AccountRepository accountRepository,
                        TransactionRepository transactionRepository, UserRepository userRepository,
                        CouponHelper couponHelper) {
        this.cartRepository = cartRepository;
        this.bookRepository = bookRepository;
        this.orderRepository = orderRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.couponHelper = couponHelper;
    }

    public Order placeOrder(String userId) {
        Cart cart = cartRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart is empty"));
        if (cart.getItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart is empty");
        }

        // 1. Validate all items have sufficient stock
        for (CartItem item : cart.getItems()) {
            Book book = bookRepository.findById(item.getBookId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Book no longer available: " + item.getBookId()));
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
            Instant userRegisteredAt = userRepository.findById(userId)
                    .map(User::getCreatedAt).orElse(null);
            coupon = couponHelper.tryValidateCoupon(cart.getCouponCode(), subtotal, userRegisteredAt);
            if (coupon != null) {
                discountAmount = couponHelper.computeDiscount(coupon, subtotal);
                appliedCouponCode = coupon.getCode();
            }
        }

        double totalAmount = Math.max(0, Math.round((subtotal - discountAmount) * 100.0) / 100.0);

        // 4. Check wallet balance
        Account account = accountRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Insufficient wallet balance. Balance: 0, Required: " + totalAmount));
        if (account.getBalance() < totalAmount) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Insufficient wallet balance. Balance: " + account.getBalance() +
                    ", Required: " + totalAmount);
        }

        // 5. All validations passed — begin mutations

        // Decrement stock
        for (CartItem item : cart.getItems()) {
            Book book = bookRepository.findById(item.getBookId()).get();
            book.setStock(book.getStock() - item.getQuantity());
            book.setUpdatedAt(Instant.now());
            bookRepository.save(book);
        }

        // Increment coupon usage
        if (coupon != null) {
            coupon.setUsedCount(coupon.getUsedCount() + 1);
        }

        // Build order items (snapshot title + priceAtAdd)
        List<OrderItem> orderItems = new ArrayList<>();
        for (CartItem item : cart.getItems()) {
            Book book = bookRepository.findById(item.getBookId()).get();
            orderItems.add(new OrderItem(item.getBookId(), book.getTitle(),
                    item.getQuantity(), item.getPriceAtAdd()));
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
        orderRepository.save(order);

        // Deduct wallet
        String desc = "Payment for order " + order.getId() +
                (appliedCouponCode != null ? " (coupon: " + appliedCouponCode + ")" : "");
        double newBalance = Math.round((account.getBalance() - totalAmount) * 100.0) / 100.0;
        account.setBalance(newBalance);
        account.setUpdatedAt(now);
        accountRepository.save(account);

        Transaction tx = new Transaction();
        tx.setId(UUID.randomUUID().toString());
        tx.setUserId(userId);
        tx.setType(TransactionType.order_payment);
        tx.setAmount(totalAmount);
        tx.setDescription(desc);
        tx.setOrderId(order.getId());
        tx.setBalanceAfter(newBalance);
        tx.setCreatedAt(now);
        transactionRepository.save(tx);

        // Clear cart
        cart.setItems(new ArrayList<>());
        cart.setCouponCode(null);
        cart.setUpdatedAt(now);
        cartRepository.save(cart);

        return order;
    }

    @Transactional(readOnly = true)
    public List<Order> listUserOrders(String userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public Order getOrder(String orderId, String userId, String role) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        if (!"admin".equals(role) && !order.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        return order;
    }

    public Order cancelOrder(String orderId, String userId, String role) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
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
            bookRepository.findById(item.getBookId()).ifPresent(book -> {
                book.setStock(book.getStock() + item.getQuantity());
                book.setUpdatedAt(now);
                bookRepository.save(book);
            });
        }

        // Refund wallet
        Account account = accountRepository.findById(order.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Account not found for user: " + order.getUserId()));
        double refundAmount = order.getTotalAmount();
        double newBalance = Math.round((account.getBalance() + refundAmount) * 100.0) / 100.0;
        account.setBalance(newBalance);
        account.setUpdatedAt(now);
        accountRepository.save(account);

        Transaction tx = new Transaction();
        tx.setId(UUID.randomUUID().toString());
        tx.setUserId(order.getUserId());
        tx.setType(TransactionType.refund);
        tx.setAmount(refundAmount);
        tx.setDescription("Refund for cancelled order " + orderId);
        tx.setOrderId(orderId);
        tx.setBalanceAfter(newBalance);
        tx.setCreatedAt(now);
        transactionRepository.save(tx);

        order.setStatus(OrderStatus.cancelled);
        order.setUpdatedAt(now);
        return orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public List<Order> listAllOrders(String statusFilter) {
        if (statusFilter != null) {
            return orderRepository.findByStatusOrderByCreatedAtDesc(OrderStatus.valueOf(statusFilter));
        }
        return orderRepository.findAllByOrderByCreatedAtDesc();
    }
}
