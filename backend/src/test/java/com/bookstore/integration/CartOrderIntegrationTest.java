package com.bookstore.integration;

import com.bookstore.repository.AccountRepository;
import com.bookstore.repository.BookRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests covering the full cart → order flow.
 *
 * Each test creates isolated users (unique emails) and books (unique ISBNs),
 * so tests do not interfere with each other in the shared H2 database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CartOrderIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired BookRepository bookRepository;
    @Autowired AccountRepository accountRepository;

    // ── helpers ───────────────────────────────────────────────────────────────

    private String adminToken() throws Exception {
        String resp = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"admin@bookstore.com","password":"admin123"}
                        """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(resp).get("token").asText();
    }

    /** Registers a fresh customer and returns {id, email, token} as a JsonNode. */
    private JsonNode registerCustomer() throws Exception {
        String email = "cust_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";

        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"pass1234","name":"Customer"}
                        """.formatted(email)))
                .andExpect(status().isCreated());

        String loginResp = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"pass1234"}
                        """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode loginNode = mapper.readTree(loginResp);
        String token = loginNode.get("token").asText();
        String userId = loginNode.get("user").get("id").asText();

        return mapper.createObjectNode()
                .put("id", userId)
                .put("email", email)
                .put("token", token);
    }

    /** Creates a book as admin and returns its id. */
    private String createBook(String adminToken, int stock) throws Exception {
        String isbn = "978" + String.format("%010d", (long) (Math.random() * 9_000_000_000L) + 1_000_000_000L);
        String resp = mvc.perform(post("/api/books")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":"Test Book","author":"Test Author","genre":"Fiction",
                         "price":20.00,"stock":%d,"isbn":"%s"}
                        """.formatted(stock, isbn)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(resp).get("id").asText();
    }

    /** Admin credits a customer's wallet. */
    private void creditWallet(String adminToken, String userId, double amount) throws Exception {
        mvc.perform(post("/api/admin/customers/" + userId + "/credit")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"amount":%.2f,"description":"Test credit"}
                        """.formatted(amount)))
                .andExpect(status().isOk());
    }

    private int getStock(String bookId) {
        return bookRepository.findById(bookId).orElseThrow().getStock();
    }

    private double getBalance(String userId) {
        return accountRepository.findById(userId).orElseThrow().getBalance();
    }

    // ── cart operations ───────────────────────────────────────────────────────

    @Test
    void getCart_empty_returnsEmptyCart() throws Exception {
        JsonNode customer = registerCustomer();
        String token = customer.get("token").asText();

        mvc.perform(get("/api/cart")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.finalTotal").value(0.0));
    }

    @Test
    void addToCart_validBook_returnsUpdatedCart() throws Exception {
        String adminTok = adminToken();
        String bookId = createBook(adminTok, 10);
        JsonNode customer = registerCustomer();
        String token = customer.get("token").asText();

        mvc.perform(post("/api/cart/items")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"bookId":"%s","quantity":2}
                        """.formatted(bookId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].bookId").value(bookId))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.subtotal").value(40.0));
    }

    @Test
    void addToCart_exceedsStock_returns400() throws Exception {
        String adminTok = adminToken();
        String bookId = createBook(adminTok, 2);
        JsonNode customer = registerCustomer();
        String token = customer.get("token").asText();

        mvc.perform(post("/api/cart/items")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"bookId":"%s","quantity":5}
                        """.formatted(bookId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addToCart_unauthenticated_returns401() throws Exception {
        mvc.perform(post("/api/cart/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"bookId":"some-id","quantity":1}
                        """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateCartItem_quantityZero_removesItem() throws Exception {
        String adminTok = adminToken();
        String bookId = createBook(adminTok, 10);
        JsonNode customer = registerCustomer();
        String token = customer.get("token").asText();

        mvc.perform(post("/api/cart/items")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"bookId":"%s","quantity":2}
                        """.formatted(bookId)))
                .andExpect(status().isOk());

        mvc.perform(patch("/api/cart/items/" + bookId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"quantity":0}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void clearCart_removesAllItems() throws Exception {
        String adminTok = adminToken();
        String bookId = createBook(adminTok, 10);
        JsonNode customer = registerCustomer();
        String token = customer.get("token").asText();

        mvc.perform(post("/api/cart/items")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"bookId":"%s","quantity":1}
                        """.formatted(bookId)))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/cart")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/cart")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    // ── coupon ────────────────────────────────────────────────────────────────

    @Test
    void applyCoupon_validCode_appliesDiscount() throws Exception {
        String adminTok = adminToken();
        String bookId = createBook(adminTok, 10);
        JsonNode customer = registerCustomer();
        String token = customer.get("token").asText();

        mvc.perform(post("/api/cart/items")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"bookId":"%s","quantity":1}
                        """.formatted(bookId)))
                .andExpect(status().isOk());

        // WELCOME10 = 10% off, no min order (seeded)
        mvc.perform(post("/api/cart/coupon")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"code":"WELCOME10"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discountAmount").value(greaterThan(0.0)))
                .andExpect(jsonPath("$.finalTotal").value(lessThan(20.0)));
    }

    @Test
    void applyCoupon_invalidCode_returns400() throws Exception {
        JsonNode customer = registerCustomer();
        String token = customer.get("token").asText();

        mvc.perform(post("/api/cart/coupon")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"code":"DOESNOTEXIST"}
                        """))
                .andExpect(status().isBadRequest());
    }

    // ── order placement ───────────────────────────────────────────────────────

    @Test
    void placeOrder_fullHappyPath_deductsWalletAndStock() throws Exception {
        String adminTok = adminToken();
        String bookId = createBook(adminTok, 10);
        int stockBefore = getStock(bookId);

        JsonNode customer = registerCustomer();
        String customerId = customer.get("id").asText();
        String customerTok = customer.get("token").asText();

        creditWallet(adminTok, customerId, 100.0);
        double balanceBefore = getBalance(customerId);

        mvc.perform(post("/api/cart/items")
                .header("Authorization", "Bearer " + customerTok)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"bookId":"%s","quantity":2}
                        """.formatted(bookId)))
                .andExpect(status().isOk());

        String orderResp = mvc.perform(post("/api/orders")
                .header("Authorization", "Bearer " + customerTok))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.status").value("pending"))
                .andExpect(jsonPath("$.totalAmount").value(40.0))
                .andReturn().getResponse().getContentAsString();

        String orderId = mapper.readTree(orderResp).get("id").asText();

        assertThat(getStock(bookId)).isEqualTo(stockBefore - 2);
        assertThat(getBalance(customerId)).isEqualTo(balanceBefore - 40.0);

        mvc.perform(get("/api/cart")
                .header("Authorization", "Bearer " + customerTok))
                .andExpect(jsonPath("$.items", hasSize(0)));

        mvc.perform(get("/api/orders")
                .header("Authorization", "Bearer " + customerTok))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(orderId));
    }

    @Test
    void placeOrder_emptyCart_returns400() throws Exception {
        JsonNode customer = registerCustomer();
        String token = customer.get("token").asText();

        mvc.perform(post("/api/orders")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("empty")));
    }

    @Test
    void placeOrder_insufficientWallet_returns400() throws Exception {
        String adminTok = adminToken();
        String bookId = createBook(adminTok, 10);
        JsonNode customer = registerCustomer();
        String token = customer.get("token").asText();

        mvc.perform(post("/api/cart/items")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"bookId":"%s","quantity":1}
                        """.formatted(bookId)))
                .andExpect(status().isOk());

        mvc.perform(post("/api/orders")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("Insufficient wallet balance")));
    }

    // ── cancel order ──────────────────────────────────────────────────────────

    @Test
    void cancelOrder_pendingOrder_refundsWalletAndRestoresStock() throws Exception {
        String adminTok = adminToken();
        String bookId = createBook(adminTok, 10);
        int stockBefore = getStock(bookId);

        JsonNode customer = registerCustomer();
        String customerId = customer.get("id").asText();
        String customerTok = customer.get("token").asText();

        creditWallet(adminTok, customerId, 100.0);

        mvc.perform(post("/api/cart/items")
                .header("Authorization", "Bearer " + customerTok)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"bookId":"%s","quantity":1}
                        """.formatted(bookId)))
                .andExpect(status().isOk());

        String orderResp = mvc.perform(post("/api/orders")
                .header("Authorization", "Bearer " + customerTok))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String orderId = mapper.readTree(orderResp).get("id").asText();
        double balanceAfterOrder = getBalance(customerId);

        mvc.perform(patch("/api/orders/" + orderId + "/cancel")
                .header("Authorization", "Bearer " + customerTok))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("cancelled"));

        assertThat(getStock(bookId)).isEqualTo(stockBefore);
        assertThat(getBalance(customerId)).isEqualTo(balanceAfterOrder + 20.0);
    }

    @Test
    void cancelOrder_nonPendingOrder_returns400() throws Exception {
        String adminTok = adminToken();
        String bookId = createBook(adminTok, 10);

        JsonNode customer = registerCustomer();
        String customerId = customer.get("id").asText();
        String customerTok = customer.get("token").asText();

        creditWallet(adminTok, customerId, 100.0);

        mvc.perform(post("/api/cart/items")
                .header("Authorization", "Bearer " + customerTok)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"bookId":"%s","quantity":1}
                        """.formatted(bookId)))
                .andExpect(status().isOk());

        String orderResp = mvc.perform(post("/api/orders")
                .header("Authorization", "Bearer " + customerTok))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String orderId = mapper.readTree(orderResp).get("id").asText();

        mvc.perform(patch("/api/admin/orders/" + orderId + "/status")
                .header("Authorization", "Bearer " + adminTok)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status":"confirmed"}
                        """))
                .andExpect(status().isOk());

        mvc.perform(patch("/api/orders/" + orderId + "/cancel")
                .header("Authorization", "Bearer " + customerTok))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("pending")));
    }

    @Test
    void cancelOrder_differentUser_returns403() throws Exception {
        String adminTok = adminToken();
        String bookId = createBook(adminTok, 10);

        JsonNode owner = registerCustomer();
        creditWallet(adminTok, owner.get("id").asText(), 100.0);

        mvc.perform(post("/api/cart/items")
                .header("Authorization", "Bearer " + owner.get("token").asText())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"bookId":"%s","quantity":1}
                        """.formatted(bookId)))
                .andExpect(status().isOk());

        String orderResp = mvc.perform(post("/api/orders")
                .header("Authorization", "Bearer " + owner.get("token").asText()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String orderId = mapper.readTree(orderResp).get("id").asText();

        JsonNode attacker = registerCustomer();

        mvc.perform(patch("/api/orders/" + orderId + "/cancel")
                .header("Authorization", "Bearer " + attacker.get("token").asText()))
                .andExpect(status().isForbidden());
    }

    // ── admin order operations ────────────────────────────────────────────────

    @Test
    void adminAdvanceStatus_pendingToConfirmedToShipped_succeeds() throws Exception {
        String adminTok = adminToken();
        String bookId = createBook(adminTok, 10);

        JsonNode customer = registerCustomer();
        String customerId = customer.get("id").asText();
        String customerTok = customer.get("token").asText();

        creditWallet(adminTok, customerId, 100.0);

        mvc.perform(post("/api/cart/items")
                .header("Authorization", "Bearer " + customerTok)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"bookId":"%s","quantity":1}
                        """.formatted(bookId)))
                .andExpect(status().isOk());

        String orderResp = mvc.perform(post("/api/orders")
                .header("Authorization", "Bearer " + customerTok))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String orderId = mapper.readTree(orderResp).get("id").asText();

        mvc.perform(patch("/api/admin/orders/" + orderId + "/status")
                .header("Authorization", "Bearer " + adminTok)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status":"confirmed"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("confirmed"));

        mvc.perform(patch("/api/admin/orders/" + orderId + "/status")
                .header("Authorization", "Bearer " + adminTok)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status":"shipped"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("shipped"));
    }
}
