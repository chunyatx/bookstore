package com.bookstore.service;

import com.bookstore.model.*;
import com.bookstore.repository.*;

import java.util.*;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * Provides HashMap-backed mock implementations of all JPA repositories for unit tests.
 * Services are wired with these mocks; the underlying Maps can be directly asserted.
 */
public class MockRepositories {

    // Direct data access for assertions in tests
    public final Map<String, User>        users        = new HashMap<>();
    public final Map<String, Book>        books        = new HashMap<>();
    public final Map<String, Cart>        carts        = new HashMap<>();
    public final Map<String, Order>       orders       = new HashMap<>();
    public final Map<String, Account>     accounts     = new HashMap<>();
    public final Map<String, Transaction> transactions = new HashMap<>();
    public final Map<String, Coupon>      coupons      = new HashMap<>();

    // Mock repository instances
    public final UserRepository        userRepo    = mock(UserRepository.class);
    public final BookRepository        bookRepo    = mock(BookRepository.class);
    public final CartRepository        cartRepo    = mock(CartRepository.class);
    public final OrderRepository       orderRepo   = mock(OrderRepository.class);
    public final AccountRepository     accountRepo = mock(AccountRepository.class);
    public final TransactionRepository txRepo      = mock(TransactionRepository.class);
    public final CouponRepository      couponRepo  = mock(CouponRepository.class);

    public MockRepositories() {
        wireUserRepo();
        wireBookRepo();
        wireCartRepo();
        wireOrderRepo();
        wireAccountRepo();
        wireTxRepo();
        wireCouponRepo();
    }

    private void wireUserRepo() {
        lenient().when(userRepo.existsByEmail(anyString())).thenAnswer(inv ->
                users.values().stream().anyMatch(u -> u.getEmail().equals(inv.getArgument(0))));
        lenient().when(userRepo.findByEmail(anyString())).thenAnswer(inv ->
                users.values().stream().filter(u -> u.getEmail().equals(inv.getArgument(0))).findFirst());
        lenient().when(userRepo.findById(anyString())).thenAnswer(inv ->
                Optional.ofNullable(users.get(inv.getArgument(0))));
        lenient().when(userRepo.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0); users.put(u.getId(), u); return u;
        });
        lenient().when(userRepo.count()).thenAnswer(inv -> (long) users.size());
        lenient().when(userRepo.findByRole(anyString())).thenAnswer(inv ->
                users.values().stream()
                        .filter(u -> u.getRole().equals(inv.getArgument(0)))
                        .collect(Collectors.toList()));
    }

    private void wireBookRepo() {
        lenient().when(bookRepo.existsByIsbn(anyString())).thenAnswer(inv ->
                books.values().stream().anyMatch(b -> b.getIsbn().equals(inv.getArgument(0))));
        lenient().when(bookRepo.findById(anyString())).thenAnswer(inv ->
                Optional.ofNullable(books.get(inv.getArgument(0))));
        lenient().when(bookRepo.save(any(Book.class))).thenAnswer(inv -> {
            Book b = inv.getArgument(0); books.put(b.getId(), b); return b;
        });
        lenient().when(bookRepo.findAllByOrderByCreatedAtAsc()).thenAnswer(inv ->
                books.values().stream()
                        .sorted(Comparator.comparing(Book::getCreatedAt))
                        .collect(Collectors.toList()));
        lenient().doAnswer(inv -> {
            Book b = inv.getArgument(0); books.remove(b.getId()); return null;
        }).when(bookRepo).delete(any(Book.class));
    }

    private void wireCartRepo() {
        lenient().when(cartRepo.findById(anyString())).thenAnswer(inv ->
                Optional.ofNullable(carts.get(inv.getArgument(0))));
        lenient().when(cartRepo.save(any(Cart.class))).thenAnswer(inv -> {
            Cart c = inv.getArgument(0); carts.put(c.getUserId(), c); return c;
        });
    }

    private void wireOrderRepo() {
        lenient().when(orderRepo.findById(anyString())).thenAnswer(inv ->
                Optional.ofNullable(orders.get(inv.getArgument(0))));
        lenient().when(orderRepo.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0); orders.put(o.getId(), o); return o;
        });
        lenient().when(orderRepo.findByUserIdOrderByCreatedAtDesc(anyString())).thenAnswer(inv ->
                orders.values().stream()
                        .filter(o -> o.getUserId().equals(inv.getArgument(0)))
                        .sorted(Comparator.comparing(Order::getCreatedAt).reversed())
                        .collect(Collectors.toList()));
        lenient().when(orderRepo.findAllByOrderByCreatedAtDesc()).thenAnswer(inv ->
                orders.values().stream()
                        .sorted(Comparator.comparing(Order::getCreatedAt).reversed())
                        .collect(Collectors.toList()));
        lenient().when(orderRepo.findByStatusOrderByCreatedAtDesc(any(OrderStatus.class))).thenAnswer(inv ->
                orders.values().stream()
                        .filter(o -> o.getStatus().equals(inv.getArgument(0)))
                        .sorted(Comparator.comparing(Order::getCreatedAt).reversed())
                        .collect(Collectors.toList()));
    }

    private void wireAccountRepo() {
        lenient().when(accountRepo.findById(anyString())).thenAnswer(inv ->
                Optional.ofNullable(accounts.get(inv.getArgument(0))));
        lenient().when(accountRepo.save(any(Account.class))).thenAnswer(inv -> {
            Account a = inv.getArgument(0); accounts.put(a.getUserId(), a); return a;
        });
    }

    private void wireTxRepo() {
        lenient().when(txRepo.save(any(Transaction.class))).thenAnswer(inv -> {
            Transaction t = inv.getArgument(0); transactions.put(t.getId(), t); return t;
        });
        lenient().when(txRepo.findByUserIdOrderByCreatedAtDesc(anyString())).thenAnswer(inv ->
                transactions.values().stream()
                        .filter(t -> t.getUserId().equals(inv.getArgument(0)))
                        .sorted(Comparator.comparing(Transaction::getCreatedAt).reversed())
                        .collect(Collectors.toList()));
    }

    private void wireCouponRepo() {
        lenient().when(couponRepo.findByCode(anyString())).thenAnswer(inv ->
                coupons.values().stream()
                        .filter(c -> c.getCode().equalsIgnoreCase(inv.getArgument(0)))
                        .findFirst());
        lenient().when(couponRepo.existsByCode(anyString())).thenAnswer(inv ->
                coupons.values().stream()
                        .anyMatch(c -> c.getCode().equalsIgnoreCase(inv.getArgument(0))));
        lenient().when(couponRepo.save(any(Coupon.class))).thenAnswer(inv -> {
            Coupon c = inv.getArgument(0); coupons.put(c.getId(), c); return c;
        });
        lenient().when(couponRepo.findAllByOrderByCreatedAtDesc()).thenAnswer(inv ->
                coupons.values().stream()
                        .sorted(Comparator.comparing(Coupon::getCreatedAt).reversed())
                        .collect(Collectors.toList()));
        lenient().when(couponRepo.findById(anyString())).thenAnswer(inv ->
                Optional.ofNullable(coupons.get(inv.getArgument(0))));
    }

    // ── Helper: count transactions for a user ─────────────────────────────────

    public List<Transaction> getUserTransactions(String userId) {
        return transactions.values().stream()
                .filter(t -> t.getUserId().equals(userId))
                .sorted(Comparator.comparing(Transaction::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    public List<Order> getUserOrders(String userId) {
        return orders.values().stream()
                .filter(o -> o.getUserId().equals(userId))
                .collect(Collectors.toList());
    }
}
