package com.bookstore.dto.response;

import com.bookstore.model.User;
import java.time.Instant;

public class UserResponse {
    private String id;
    private String email;
    private String name;
    private String role;
    private Instant createdAt;

    public static UserResponse from(User user) {
        UserResponse r = new UserResponse();
        r.id = user.getId();
        r.email = user.getEmail();
        r.name = user.getName();
        r.role = user.getRole();
        r.createdAt = user.getCreatedAt();
        return r;
    }

    public String getId() { return id; }
    public String getEmail() { return email; }
    public String getName() { return name; }
    public String getRole() { return role; }
    public Instant getCreatedAt() { return createdAt; }
}
