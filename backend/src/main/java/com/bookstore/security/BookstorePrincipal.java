package com.bookstore.security;

public record BookstorePrincipal(String userId, String email, String role) {}
