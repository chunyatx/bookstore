package com.bookstore.service;

import com.bookstore.model.Account;
import com.bookstore.model.Transaction;
import com.bookstore.store.InMemoryStore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AccountService {

    private final InMemoryStore store;

    public AccountService(InMemoryStore store) {
        this.store = store;
    }

    public Map<String, Object> getAccount(String userId) {
        Account account = store.accounts.get(userId);
        if (account == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
        }

        List<String> txIds = store.getUserTransactions(userId);
        List<Transaction> transactions = txIds.stream()
                .map(store.transactions::get)
                .filter(t -> t != null)
                .sorted(Comparator.comparing(Transaction::getCreatedAt).reversed())
                .limit(20)
                .collect(Collectors.toList());

        return Map.of("balance", account.getBalance(), "transactions", transactions);
    }
}
