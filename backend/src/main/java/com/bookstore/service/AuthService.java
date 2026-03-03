package com.bookstore.service;

import com.bookstore.dto.request.LoginRequest;
import com.bookstore.dto.request.RegisterRequest;
import com.bookstore.dto.response.AuthResponse;
import com.bookstore.dto.response.UserResponse;
import com.bookstore.model.Account;
import com.bookstore.model.User;
import com.bookstore.security.BookstorePrincipal;
import com.bookstore.security.JwtUtil;
import com.bookstore.store.InMemoryStore;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuthService {

    private final InMemoryStore store;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(InMemoryStore store, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.store = store;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public UserResponse register(RegisterRequest req) {
        String normalizedEmail = req.getEmail().toLowerCase().trim();
        if (store.emailIndex.containsKey(normalizedEmail)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }

        String id = UUID.randomUUID().toString();
        String passwordHash = passwordEncoder.encode(req.getPassword());
        User user = new User(id, normalizedEmail, passwordHash, req.getName().trim(), "customer", Instant.now());
        store.users.put(id, user);
        store.emailIndex.put(normalizedEmail, id);

        // Create wallet account with $0 balance
        store.accounts.put(id, new Account(id, 0.0));

        return UserResponse.from(user);
    }

    public AuthResponse login(LoginRequest req) {
        String normalizedEmail = req.getEmail().toLowerCase().trim();
        String userId = store.emailIndex.get(normalizedEmail);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        User user = store.users.get(userId);
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole());
        return new AuthResponse(token, UserResponse.from(user));
    }

    public UserResponse getMe(BookstorePrincipal principal) {
        User user = store.users.get(principal.userId());
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        return UserResponse.from(user);
    }
}
