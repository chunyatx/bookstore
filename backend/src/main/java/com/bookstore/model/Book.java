package com.bookstore.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "BS_BOOKS")
public class Book {

    @Id
    @Column(name = "ID", length = 36, nullable = false)
    private String id;

    @Column(name = "TITLE", nullable = false, length = 500)
    private String title;

    @Column(name = "AUTHOR", nullable = false, length = 255)
    private String author;

    @Column(name = "GENRE", nullable = false, length = 100)
    private String genre;

    @Column(name = "PRICE", nullable = false)
    private double price;

    @Column(name = "STOCK", nullable = false)
    private int stock;

    @Column(name = "DESCRIPTION", length = 2000)
    private String description;

    @Column(name = "ISBN", unique = true, nullable = false, length = 50)
    private String isbn;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private Instant updatedAt;

    public Book() {}

    public Book(String id, String title, String author, String genre, double price, int stock,
                String description, String isbn, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.genre = genre;
        this.price = price;
        this.stock = stock;
        this.description = description;
        this.isbn = isbn;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getIsbn() { return isbn; }
    public void setIsbn(String isbn) { this.isbn = isbn; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
