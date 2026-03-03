package com.bookstore.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for /api/books endpoints.
 * Admin operations use the seeded admin@bookstore.com / admin123 credentials.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class BooksIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Logs in as the seeded admin and returns the JWT token. */
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

    /** Registers a customer and returns their JWT token. */
    private String customerToken() throws Exception {
        String email = "cust_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"pass1234","name":"Cust"}
                        """.formatted(email)))
                .andExpect(status().isCreated());

        String resp = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"pass1234"}
                        """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(resp).get("token").asText();
    }

    /** Creates a book as admin and returns its id. */
    private String createBook(String token, String isbn) throws Exception {
        String resp = mvc.perform(post("/api/books")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":"Test Book","author":"Test Author","genre":"Fiction",
                         "price":12.99,"stock":20,"isbn":"%s"}
                        """.formatted(isbn)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(resp).get("id").asText();
    }

    private static String uniqueIsbn() {
        // 13-digit ISBN-like string using random digits
        return "978" + String.format("%010d", (long)(Math.random() * 9_999_999_999L));
    }

    // ── GET /api/books (public) ────────────────────────────────────────────────

    @Test
    void listBooks_public_returns200WithSeededBooks() throws Exception {
        mvc.perform(get("/api/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.total").isNumber())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.limit").value(20));
    }

    @Test
    void listBooks_titleFilter_returnsMatchingBooks() throws Exception {
        mvc.perform(get("/api/books").param("title", "dune"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value(containsStringIgnoringCase("dune")));
    }

    @Test
    void listBooks_pagination_returnsCorrectPage() throws Exception {
        mvc.perform(get("/api/books").param("page", "1").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(lessThanOrEqualTo(5))))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.limit").value(5));
    }

    @Test
    void listBooks_noAuth_stillReturns200() throws Exception {
        mvc.perform(get("/api/books"))
                .andExpect(status().isOk());
    }

    // ── GET /api/books/{id} (public) ──────────────────────────────────────────

    @Test
    void getBook_existingId_returns200() throws Exception {
        // Get first book's id from list
        String listResp = mvc.perform(get("/api/books"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String bookId = mapper.readTree(listResp).get("data").get(0).get("id").asText();

        mvc.perform(get("/api/books/" + bookId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookId));
    }

    @Test
    void getBook_unknownId_returns404() throws Exception {
        mvc.perform(get("/api/books/nonexistent-id"))
                .andExpect(status().isNotFound());
    }

    // ── POST /api/books (admin only) ─────────────────────────────────────────

    @Test
    void createBook_asAdmin_returns201() throws Exception {
        String token = adminToken();
        mvc.perform(post("/api/books")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":"New Novel","author":"New Author","genre":"Fiction",
                         "price":9.99,"stock":15,"isbn":"%s"}
                        """.formatted(uniqueIsbn())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.title").value("New Novel"))
                .andExpect(jsonPath("$.price").value(9.99));
    }

    @Test
    void createBook_asCustomer_returns403() throws Exception {
        String token = customerToken();
        mvc.perform(post("/api/books")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":"Sneaky Book","author":"A","genre":"G",
                         "price":5.0,"stock":5,"isbn":"%s"}
                        """.formatted(uniqueIsbn())))
                .andExpect(status().isForbidden());
    }

    @Test
    void createBook_unauthenticated_returns401() throws Exception {
        mvc.perform(post("/api/books")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":"Ghost Book","author":"A","genre":"G",
                         "price":5.0,"stock":5,"isbn":"%s"}
                        """.formatted(uniqueIsbn())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createBook_duplicateIsbn_returns409() throws Exception {
        String token = adminToken();
        String isbn = uniqueIsbn();

        mvc.perform(post("/api/books")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":"Book A","author":"A","genre":"G","price":9.0,"stock":5,"isbn":"%s"}
                        """.formatted(isbn)))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/books")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":"Book B","author":"B","genre":"G","price":9.0,"stock":5,"isbn":"%s"}
                        """.formatted(isbn)))
                .andExpect(status().isConflict());
    }

    // ── PATCH /api/books/{id} (admin only) ────────────────────────────────────

    @Test
    void updateBook_asAdmin_changesTitle() throws Exception {
        String token = adminToken();
        String bookId = createBook(token, uniqueIsbn());

        mvc.perform(patch("/api/books/" + bookId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":"Updated Title"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Title"));
    }

    @Test
    void updateBook_unknownId_returns404() throws Exception {
        String token = adminToken();
        mvc.perform(patch("/api/books/ghost-id")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":"X"}
                        """))
                .andExpect(status().isNotFound());
    }

    // ── DELETE /api/books/{id} (admin only) ───────────────────────────────────

    @Test
    void deleteBook_asAdmin_returns204() throws Exception {
        String token = adminToken();
        String bookId = createBook(token, uniqueIsbn());

        mvc.perform(delete("/api/books/" + bookId)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Confirm it's gone
        mvc.perform(get("/api/books/" + bookId))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteBook_asCustomer_returns403() throws Exception {
        String adminTok = adminToken();
        String bookId = createBook(adminTok, uniqueIsbn());
        String customerTok = customerToken();

        mvc.perform(delete("/api/books/" + bookId)
                .header("Authorization", "Bearer " + customerTok))
                .andExpect(status().isForbidden());
    }
}
