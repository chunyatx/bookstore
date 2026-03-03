package com.bookstore.controller;

import com.bookstore.security.BookstorePrincipal;
import com.bookstore.service.AccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAccount(@AuthenticationPrincipal BookstorePrincipal principal) {
        return ResponseEntity.ok(accountService.getAccount(principal.userId()));
    }
}
