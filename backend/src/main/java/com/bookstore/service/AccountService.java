package com.bookstore.service;

import com.bookstore.model.Account;
import com.bookstore.model.Transaction;
import com.bookstore.repository.AccountRepository;
import com.bookstore.repository.TransactionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class AccountService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public AccountService(AccountRepository accountRepository, TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    public Map<String, Object> getAccount(String userId) {
        Account account = accountRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));

        List<Transaction> transactions = transactionRepository
                .findByUserIdOrderByCreatedAtDesc(userId)
                .stream().limit(20).toList();

        return Map.of("balance", account.getBalance(), "transactions", transactions);
    }
}
