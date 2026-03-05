package com.bookstore.service;

import com.bookstore.dto.request.CreateBookRequest;
import com.bookstore.dto.request.UpdateBookRequest;
import com.bookstore.dto.response.PagedBooksResponse;
import com.bookstore.model.Book;
import com.bookstore.repository.BookRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@Transactional
public class BookService {

    private final BookRepository bookRepository;

    public BookService(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    @Transactional(readOnly = true)
    public PagedBooksResponse listBooks(String title, String author, String genre,
                                        Double minPrice, Double maxPrice, int page, int limit) {
        Stream<Book> stream = bookRepository.findAllByOrderByCreatedAtAsc().stream();

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

        List<Book> all = stream.toList();
        long total = all.size();
        int clampedLimit = Math.min(Math.max(limit, 1), 100);
        int safePage = Math.max(page, 1);
        int skip = (safePage - 1) * clampedLimit;

        List<Book> paged = all.stream().skip(skip).limit(clampedLimit).toList();
        return new PagedBooksResponse(paged, total, page, clampedLimit);
    }

    @Transactional(readOnly = true)
    public Book getBook(String id) {
        return bookRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found"));
    }

    public Book createBook(CreateBookRequest req) {
        String normalizedIsbn = req.getIsbn().trim();
        if (bookRepository.existsByIsbn(normalizedIsbn)) {
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
        return bookRepository.save(book);
    }

    public Book updateBook(String id, UpdateBookRequest req) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found"));

        if (req.getTitle() != null) book.setTitle(req.getTitle().trim());
        if (req.getAuthor() != null) book.setAuthor(req.getAuthor().trim());
        if (req.getGenre() != null) book.setGenre(req.getGenre().trim());
        if (req.getPrice() != null) book.setPrice(req.getPrice());
        if (req.getStock() != null) book.setStock(req.getStock());
        if (req.getDescription() != null) book.setDescription(req.getDescription());
        if (req.getIsbn() != null) {
            String newIsbn = req.getIsbn().trim();
            if (!newIsbn.equals(book.getIsbn())) {
                if (bookRepository.existsByIsbn(newIsbn)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "ISBN already exists");
                }
                book.setIsbn(newIsbn);
            }
        }
        book.setUpdatedAt(Instant.now());
        return bookRepository.save(book);
    }

    public void deleteBook(String id) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found"));
        bookRepository.delete(book);
    }
}
