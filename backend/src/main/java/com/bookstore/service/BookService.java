package com.bookstore.service;

import com.bookstore.dto.request.CreateBookRequest;
import com.bookstore.dto.request.UpdateBookRequest;
import com.bookstore.dto.response.PagedBooksResponse;
import com.bookstore.model.Book;
import com.bookstore.store.InMemoryStore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class BookService {

    private final InMemoryStore store;

    public BookService(InMemoryStore store) {
        this.store = store;
    }

    public PagedBooksResponse listBooks(String title, String author, String genre,
                                        Double minPrice, Double maxPrice, int page, int limit) {
        Stream<Book> stream = store.books.values().stream();

        if (title != null && !title.isBlank()) {
            String t = title.toLowerCase();
            stream = stream.filter(b -> b.getTitle().toLowerCase().contains(t));
        }
        if (author != null && !author.isBlank()) {
            String a = author.toLowerCase();
            stream = stream.filter(b -> b.getAuthor().toLowerCase().contains(a));
        }
        if (genre != null && !genre.isBlank()) {
            String g = genre.toLowerCase();
            stream = stream.filter(b -> b.getGenre().toLowerCase().contains(g));
        }
        if (minPrice != null) {
            stream = stream.filter(b -> b.getPrice() >= minPrice);
        }
        if (maxPrice != null) {
            stream = stream.filter(b -> b.getPrice() <= maxPrice);
        }

        List<Book> all = stream.sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                               .toList();
        long total = all.size();
        int clampedLimit = Math.min(limit, 100);
        int skip = (page - 1) * clampedLimit;

        List<Book> paged = all.stream().skip(skip).limit(clampedLimit).toList();
        return new PagedBooksResponse(paged, total, page, clampedLimit);
    }

    public Book getBook(String id) {
        Book book = store.books.get(id);
        if (book == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found");
        }
        return book;
    }

    public Book createBook(CreateBookRequest req) {
        String normalizedIsbn = req.getIsbn().trim();
        if (store.isbnIndex.containsKey(normalizedIsbn)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ISBN already exists");
        }

        Instant now = Instant.now();
        Book book = new Book(
            UUID.randomUUID().toString(),
            req.getTitle().trim(),
            req.getAuthor().trim(),
            req.getGenre().trim(),
            req.getPrice(),
            req.getStock(),
            req.getDescription() != null ? req.getDescription() : "",
            normalizedIsbn,
            now, now
        );

        store.books.put(book.getId(), book);
        store.isbnIndex.put(normalizedIsbn, book.getId());
        return book;
    }

    public Book updateBook(String id, UpdateBookRequest req) {
        Book book = store.books.get(id);
        if (book == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found");
        }

        if (req.getTitle() != null) book.setTitle(req.getTitle().trim());
        if (req.getAuthor() != null) book.setAuthor(req.getAuthor().trim());
        if (req.getGenre() != null) book.setGenre(req.getGenre().trim());
        if (req.getPrice() != null) book.setPrice(req.getPrice());
        if (req.getStock() != null) book.setStock(req.getStock());
        if (req.getDescription() != null) book.setDescription(req.getDescription());
        if (req.getIsbn() != null) {
            String newIsbn = req.getIsbn().trim();
            if (!newIsbn.equals(book.getIsbn())) {
                if (store.isbnIndex.containsKey(newIsbn)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "ISBN already exists");
                }
                store.isbnIndex.remove(book.getIsbn());
                store.isbnIndex.put(newIsbn, book.getId());
                book.setIsbn(newIsbn);
            }
        }
        book.setUpdatedAt(Instant.now());
        return book;
    }

    public void deleteBook(String id) {
        Book book = store.books.remove(id);
        if (book == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found");
        }
        store.isbnIndex.remove(book.getIsbn());
    }
}
