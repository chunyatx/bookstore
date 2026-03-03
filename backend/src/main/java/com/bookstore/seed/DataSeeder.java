package com.bookstore.seed;

import com.bookstore.model.*;
import com.bookstore.store.InMemoryStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class DataSeeder implements ApplicationRunner {

    private final InMemoryStore store;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(InMemoryStore store, PasswordEncoder passwordEncoder) {
        this.store = store;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        // Only seed once — check if already seeded
        if (!store.users.isEmpty()) return;

        seedAdmin();
        seedBooks();
        seedCoupons();
        System.out.println("Data seeded: admin, 15 books, 3 coupons");
    }

    private void seedAdmin() {
        String id = UUID.randomUUID().toString();
        String email = "admin@bookstore.com";
        User admin = new User(id, email, passwordEncoder.encode("admin123"), "Admin", "admin", Instant.now());
        store.users.put(id, admin);
        store.emailIndex.put(email, id);
        store.accounts.put(id, new Account(id, 0.0));
    }

    private void seedBooks() {
        addBook("Dune", "Frank Herbert", "Science Fiction", 14.99, 50,
                "A science fiction epic set in a distant future amid a feudal interstellar society.",
                "9780441013593");
        addBook("The Hitchhiker's Guide to the Galaxy", "Douglas Adams", "Science Fiction", 9.99, 75,
                "A comedic science fiction series following Arthur Dent through the universe.",
                "9780345391803");
        addBook("1984", "George Orwell", "Dystopian Fiction", 11.99, 60,
                "A dystopian novel about a totalitarian society where Big Brother watches everyone.",
                "9780451524935");
        addBook("To Kill a Mockingbird", "Harper Lee", "Literary Fiction", 12.99, 45,
                "A classic novel about racial injustice and childhood in the American South.",
                "9780061935466");
        addBook("The Great Gatsby", "F. Scott Fitzgerald", "Literary Fiction", 10.99, 40,
                "A portrait of the Jazz Age and the American Dream in the 1920s.",
                "9780743273565");
        addBook("Harry Potter and the Sorcerer's Stone", "J.K. Rowling", "Fantasy", 13.99, 100,
                "The beginning of Harry Potter's magical journey at Hogwarts School of Witchcraft.",
                "9780439708180");
        addBook("The Lord of the Rings", "J.R.R. Tolkien", "Fantasy", 19.99, 35,
                "An epic high-fantasy novel following the quest to destroy the One Ring.",
                "9780544003415");
        addBook("The Da Vinci Code", "Dan Brown", "Mystery", 12.99, 55,
                "A mystery thriller involving secret societies, the Holy Grail, and Leonardo da Vinci.",
                "9780307474278");
        addBook("Gone Girl", "Gillian Flynn", "Mystery", 13.99, 42,
                "A psychological thriller about a woman who disappears on her fifth wedding anniversary.",
                "9780307588364");
        addBook("The Alchemist", "Paulo Coelho", "Fiction", 11.99, 65,
                "A philosophical novel about following your dreams and listening to your heart.",
                "9780062315007");
        addBook("Sapiens: A Brief History of Humankind", "Yuval Noah Harari", "Non-Fiction", 16.99, 38,
                "A sweeping history of humankind from the Stone Age to the twenty-first century.",
                "9780062316110");
        addBook("Becoming", "Michelle Obama", "Biography", 17.99, 30,
                "The memoir of former First Lady Michelle Obama.",
                "9781524763138");
        addBook("The Lean Startup", "Eric Ries", "Business", 14.99, 25,
                "How today's entrepreneurs use continuous innovation to create radically successful businesses.",
                "9780307887894");
        addBook("Educated", "Tara Westover", "Memoir", 15.99, 33,
                "A memoir about a young girl who, kept out of school, leaves her survivalist family.",
                "9780399590504");
        addBook("Where the Crawdads Sing", "Delia Owens", "Mystery", 13.99, 48,
                "A coming-of-age story set in the marshes of North Carolina.",
                "9780735224292");
    }

    private void addBook(String title, String author, String genre, double price, int stock,
                         String description, String isbn) {
        Instant now = Instant.now();
        Book book = new Book(UUID.randomUUID().toString(), title, author, genre, price, stock,
                description, isbn, now, now);
        store.books.put(book.getId(), book);
        store.isbnIndex.put(isbn, book.getId());
    }

    private void seedCoupons() {
        addCoupon("WELCOME10", CouponType.percentage, 10, "10% off your first order", 0, null, null);
        addCoupon("SAVE5", CouponType.fixed, 5, "$5 off orders over $20", 20, null, null);
        addCoupon("HALFOFF", CouponType.percentage, 50, "50% off — limited to 10 uses", 0, 10, null);
    }

    private void addCoupon(String code, CouponType type, double value, String description,
                            double minOrderAmount, Integer maxUses, Instant expiresAt) {
        Coupon coupon = new Coupon();
        coupon.setId(UUID.randomUUID().toString());
        coupon.setCode(code);
        coupon.setType(type);
        coupon.setValue(value);
        coupon.setDescription(description);
        coupon.setMinOrderAmount(minOrderAmount);
        coupon.setMaxUses(maxUses);
        coupon.setUsedCount(0);
        coupon.setActive(true);
        coupon.setExpiresAt(expiresAt);
        coupon.setCreatedAt(Instant.now());
        store.coupons.put(coupon.getId(), coupon);
        store.couponCodeIndex.put(code, coupon.getId());
    }
}
