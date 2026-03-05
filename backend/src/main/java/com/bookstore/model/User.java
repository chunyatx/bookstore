package com.bookstore.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "BS_USERS")
public class User {

    @Id
    @Column(name = "ID", length = 36, nullable = false)
    private String id;

    @Column(name = "EMAIL", unique = true, nullable = false, length = 255)
    private String email;

    @Column(name = "PASSWORD_HASH", nullable = false, length = 80)
    private String passwordHash;

    @Column(name = "NAME", nullable = false, length = 255)
    private String name;

    @Column(name = "ROLE", nullable = false, length = 20)
    private String role;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    public User() {}

    public User(String id, String email, String passwordHash, String name, String role, Instant createdAt) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.role = role;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
