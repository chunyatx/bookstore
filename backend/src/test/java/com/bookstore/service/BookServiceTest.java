package com.bookstore.service;

import com.bookstore.dto.request.CreateBookRequest;
import com.bookstore.dto.request.UpdateBookRequest;
import com.bookstore.dto.response.PagedBooksResponse;
import com.bookstore.model.Book;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.*;

class BookServiceTest {

    private MockRepositories mr;
    private BookService service;

    @BeforeEach
    void setUp() {
        mr = new MockRepositories();
        service = new BookService(mr.bookRepo);
    }

    // ── createBook ────────────────────────────────────────────────────────────

    @Test
    void createBook_valid_storesAndReturns() {
        Book book = service.createBook(createReq("Dune", "Herbert", "Sci-Fi", 14.99, 10, "9780441013593"));
        assertThat(book.getId()).isNotBlank();
        assertThat(book.getTitle()).isEqualTo("Dune");
        assertThat(mr.books).containsKey(book.getId());
    }

    @Test
    void createBook_duplicateIsbn_throws409() {
        service.createBook(createReq("Book A", "Author", "Genre", 10.0, 5, "9781111111111"));
        assertThatThrownBy(() ->
                service.createBook(createReq("Book B", "Author", "Genre", 10.0, 5, "9781111111111")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ISBN already exists");
    }

    // ── getBook ───────────────────────────────────────────────────────────────

    @Test
    void getBook_existingId_returnsBook() {
        Book b = TestFixtures.addBook(mr, "1984", "Orwell", "Dystopia", 11.99, 20, "9780451524935");
        assertThat(service.getBook(b.getId()).getTitle()).isEqualTo("1984");
    }

    @Test
    void getBook_unknownId_throws404() {
        assertThatThrownBy(() -> service.getBook("no-such-id"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not found");
    }

    // ── updateBook ────────────────────────────────────────────────────────────

    @Test
    void updateBook_changesSuppliedFields() {
        Book b = TestFixtures.addBook(mr, "Old Title", "A", "G", 9.99, 5, "9780000000001");
        UpdateBookRequest req = new UpdateBookRequest();
        req.setTitle("New Title");
        req.setPrice(14.99);

        Book updated = service.updateBook(b.getId(), req);
        assertThat(updated.getTitle()).isEqualTo("New Title");
        assertThat(updated.getPrice()).isEqualTo(14.99);
        assertThat(updated.getAuthor()).isEqualTo("A");
    }

    @Test
    void updateBook_changeIsbn_updatesIndex() {
        Book b = TestFixtures.addBook(mr, "T", "A", "G", 9.99, 5, "9780000000001");
        UpdateBookRequest req = new UpdateBookRequest();
        req.setIsbn("9780000000002");

        service.updateBook(b.getId(), req);
        assertThat(mr.books.get(b.getId()).getIsbn()).isEqualTo("9780000000002");
    }

    @Test
    void updateBook_changeIsbnToExisting_throws409() {
        TestFixtures.addBook(mr, "A", "X", "G", 9.99, 5, "9780000000001");
        Book b2 = TestFixtures.addBook(mr, "B", "X", "G", 9.99, 5, "9780000000002");
        UpdateBookRequest req = new UpdateBookRequest();
        req.setIsbn("9780000000001");
        assertThatThrownBy(() -> service.updateBook(b2.getId(), req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ISBN already exists");
    }

    @Test
    void updateBook_unknownId_throws404() {
        assertThatThrownBy(() -> service.updateBook("ghost", new UpdateBookRequest()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not found");
    }

    // ── deleteBook ────────────────────────────────────────────────────────────

    @Test
    void deleteBook_removesFromStore() {
        Book b = TestFixtures.addBook(mr, "T", "A", "G", 9.99, 5, "9780000000001");
        service.deleteBook(b.getId());
        assertThat(mr.books).doesNotContainKey(b.getId());
    }

    @Test
    void deleteBook_unknownId_throws404() {
        assertThatThrownBy(() -> service.deleteBook("ghost"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not found");
    }

    // ── listBooks / search / pagination ───────────────────────────────────────

    @Test
    void listBooks_noFilters_returnsAll() {
        TestFixtures.addBook(mr, "Alpha", "A", "Fiction", 10.0, 5, "9780000000001");
        TestFixtures.addBook(mr, "Beta",  "B", "Non-Fiction", 20.0, 3, "9780000000002");
        PagedBooksResponse resp = service.listBooks(null, null, null, null, null, 1, 20);
        assertThat(resp.getTotal()).isEqualTo(2);
        assertThat(resp.getData()).hasSize(2);
    }

    @Test
    void listBooks_filterByTitle_caseInsensitive() {
        TestFixtures.addBook(mr, "Dune Messiah", "Herbert", "Sci-Fi", 12.0, 5, "9780000000001");
        TestFixtures.addBook(mr, "Foundation",   "Asimov",  "Sci-Fi", 11.0, 5, "9780000000002");
        PagedBooksResponse resp = service.listBooks("dune", null, null, null, null, 1, 20);
        assertThat(resp.getTotal()).isEqualTo(1);
        assertThat(resp.getData().get(0).getTitle()).isEqualTo("Dune Messiah");
    }

    @Test
    void listBooks_filterByGenre() {
        TestFixtures.addBook(mr, "A", "X", "Fantasy", 10.0, 5, "9780000000001");
        TestFixtures.addBook(mr, "B", "Y", "Mystery", 10.0, 5, "9780000000002");
        PagedBooksResponse resp = service.listBooks(null, null, "Fantasy", null, null, 1, 20);
        assertThat(resp.getTotal()).isEqualTo(1);
        assertThat(resp.getData().get(0).getGenre()).isEqualTo("Fantasy");
    }

    @Test
    void listBooks_priceRange_filtersCorrectly() {
        TestFixtures.addBook(mr, "Cheap",  "A", "G", 5.0,  5, "9780000000001");
        TestFixtures.addBook(mr, "Mid",    "A", "G", 15.0, 5, "9780000000002");
        TestFixtures.addBook(mr, "Pricey", "A", "G", 50.0, 5, "9780000000003");
        PagedBooksResponse resp = service.listBooks(null, null, null, 10.0, 20.0, 1, 20);
        assertThat(resp.getTotal()).isEqualTo(1);
        assertThat(resp.getData().get(0).getTitle()).isEqualTo("Mid");
    }

    @Test
    void listBooks_pagination_returnsCorrectPage() {
        for (int i = 1; i <= 5; i++) {
            TestFixtures.addBook(mr, "Book " + i, "A", "G", 10.0, 5, "978000000000" + i);
        }
        PagedBooksResponse p1 = service.listBooks(null, null, null, null, null, 1, 2);
        PagedBooksResponse p2 = service.listBooks(null, null, null, null, null, 2, 2);
        PagedBooksResponse p3 = service.listBooks(null, null, null, null, null, 3, 2);

        assertThat(p1.getData()).hasSize(2);
        assertThat(p2.getData()).hasSize(2);
        assertThat(p3.getData()).hasSize(1);
        assertThat(p1.getTotal()).isEqualTo(5);
    }

    @Test
    void listBooks_pageZeroOrNegative_treatedAsPageOne() {
        TestFixtures.addBook(mr, "A", "X", "G", 10.0, 5, "9780000000001");
        assertThatCode(() -> service.listBooks(null, null, null, null, null, 0, 20))
                .doesNotThrowAnyException();
        assertThatCode(() -> service.listBooks(null, null, null, null, null, -5, 20))
                .doesNotThrowAnyException();
    }

    @Test
    void listBooks_limitCappedAt100() {
        for (int i = 1; i <= 10; i++) {
            TestFixtures.addBook(mr, "Book " + i, "A", "G", 10.0, 5, "978000000000" + i);
        }
        PagedBooksResponse resp = service.listBooks(null, null, null, null, null, 1, 9999);
        assertThat(resp.getLimit()).isEqualTo(100);
        assertThat(resp.getData()).hasSize(10);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static CreateBookRequest createReq(String title, String author, String genre,
                                               double price, int stock, String isbn) {
        CreateBookRequest r = new CreateBookRequest();
        r.setTitle(title);
        r.setAuthor(author);
        r.setGenre(genre);
        r.setPrice(price);
        r.setStock(stock);
        r.setIsbn(isbn);
        return r;
    }
}
