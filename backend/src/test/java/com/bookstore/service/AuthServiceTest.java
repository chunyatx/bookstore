package com.bookstore.service;

import com.bookstore.dto.request.LoginRequest;
import com.bookstore.dto.request.RegisterRequest;
import com.bookstore.dto.response.AuthResponse;
import com.bookstore.dto.response.UserResponse;
import com.bookstore.model.User;
import com.bookstore.security.BookstorePrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.*;

class AuthServiceTest {

    private MockRepositories mr;
    private PasswordEncoder encoder;
    private AuthService service;

    @BeforeEach
    void setUp() {
        mr = new MockRepositories();
        encoder = TestFixtures.passwordEncoder();
        service = new AuthService(mr.userRepo, mr.accountRepo, encoder, TestFixtures.jwtUtil());
    }

    // ── register ──────────────────────────────────────────────────────────────

    @Test
    void register_newUser_storesUserAndAccount() {
        RegisterRequest req = req("alice@example.com", "password1", "Alice");
        UserResponse resp = service.register(req);

        assertThat(resp.getEmail()).isEqualTo("alice@example.com");
        assertThat(resp.getName()).isEqualTo("Alice");
        assertThat(resp.getRole()).isEqualTo("customer");
        assertThat(mr.users).hasSize(1);
        assertThat(mr.accounts.get(resp.getId()).getBalance()).isEqualTo(0.0);
    }

    @Test
    void register_normalizesEmailToLowercase() {
        service.register(req("Alice@EXAMPLE.COM", "password1", "Alice"));
        assertThat(mr.users.values().iterator().next().getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void register_passwordIsHashed() {
        RegisterRequest req = req("alice@example.com", "password1", "Alice");
        UserResponse resp = service.register(req);
        User stored = mr.users.get(resp.getId());
        assertThat(stored.getPasswordHash()).isNotEqualTo("password1");
        assertThat(encoder.matches("password1", stored.getPasswordHash())).isTrue();
    }

    @Test
    void register_duplicateEmail_throws409() {
        service.register(req("alice@example.com", "password1", "Alice"));
        assertThatThrownBy(() -> service.register(req("alice@example.com", "other", "Alice2")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    void register_duplicateEmail_caseInsensitive_throws409() {
        service.register(req("alice@example.com", "password1", "Alice"));
        assertThatThrownBy(() -> service.register(req("ALICE@EXAMPLE.COM", "other", "Alice2")))
                .isInstanceOf(ResponseStatusException.class);
    }

    // ── login ─────────────────────────────────────────────────────────────────

    @Test
    void login_correctCredentials_returnsToken() {
        service.register(req("bob@example.com", "secret123", "Bob"));

        LoginRequest login = loginReq("bob@example.com", "secret123");
        AuthResponse resp = service.login(login);

        assertThat(resp.getToken()).isNotBlank();
        assertThat(resp.getUser().getEmail()).isEqualTo("bob@example.com");
    }

    @Test
    void login_wrongPassword_throws401() {
        service.register(req("bob@example.com", "secret123", "Bob"));
        assertThatThrownBy(() -> service.login(loginReq("bob@example.com", "wrong")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid credentials");
    }

    @Test
    void login_unknownEmail_throws401() {
        assertThatThrownBy(() -> service.login(loginReq("nobody@example.com", "x")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid credentials");
    }

    @Test
    void login_emailCaseInsensitive_succeeds() {
        service.register(req("carol@example.com", "pass", "Carol"));
        assertThatCode(() -> service.login(loginReq("CAROL@EXAMPLE.COM", "pass")))
                .doesNotThrowAnyException();
    }

    // ── getMe ─────────────────────────────────────────────────────────────────

    @Test
    void getMe_knownUser_returnsProfile() {
        UserResponse reg = service.register(req("dave@example.com", "pass", "Dave"));
        BookstorePrincipal principal = new BookstorePrincipal(reg.getId(), reg.getEmail(), reg.getRole());
        UserResponse me = service.getMe(principal);
        assertThat(me.getId()).isEqualTo(reg.getId());
        assertThat(me.getEmail()).isEqualTo("dave@example.com");
    }

    @Test
    void getMe_unknownUserId_throws404() {
        BookstorePrincipal ghost = new BookstorePrincipal("nonexistent-id", "x@y.com", "customer");
        assertThatThrownBy(() -> service.getMe(ghost))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not found");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static RegisterRequest req(String email, String password, String name) {
        RegisterRequest r = new RegisterRequest();
        r.setEmail(email);
        r.setPassword(password);
        r.setName(name);
        return r;
    }

    private static LoginRequest loginReq(String email, String password) {
        LoginRequest r = new LoginRequest();
        r.setEmail(email);
        r.setPassword(password);
        return r;
    }
}
