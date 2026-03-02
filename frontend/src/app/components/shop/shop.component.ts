import { Component, OnInit, signal } from '@angular/core';
import { NgFor, NgIf, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Book } from '../../models';
import { BookService, BookQueryParams } from '../../services/book.service';
import { CartService } from '../../services/cart.service';
import { AuthService } from '../../services/auth.service';
import { ToastService } from '../../services/toast.service';

const GENRES = ['All', 'Fiction', 'Science Fiction', 'Fantasy', 'Mystery', 'Literary Fiction',
                 'Non-Fiction', 'Biography', 'Business', 'Memoir', 'Dystopian Fiction', 'History'];

@Component({
  selector: 'app-shop',
  standalone: true,
  imports: [NgFor, NgIf, DecimalPipe, FormsModule],
  styles: [`
    .page { display: flex; gap: 24px; padding: 24px; max-width: 1400px; margin: 0 auto; }
    .sidebar { width: 220px; flex-shrink: 0; }
    .main { flex: 1; min-width: 0; }
    .filter-card { background: white; border: 1px solid #e5e7eb; border-radius: 8px; padding: 16px; margin-bottom: 16px; }
    .filter-card h3 { font-size: 13px; font-weight: 600; color: #374151; margin-bottom: 10px; text-transform: uppercase; }
    .filter-input {
      width: 100%; padding: 7px 10px; border: 1px solid #d1d5db;
      border-radius: 6px; font-size: 14px; margin-bottom: 8px; outline: none;
    }
    .filter-input:focus { border-color: #2563eb; }
    .genre-btn {
      display: block; width: 100%; padding: 6px 10px; margin-bottom: 4px;
      border: 1px solid #e5e7eb; border-radius: 6px; background: white;
      text-align: left; font-size: 13px; cursor: pointer; color: #374151;
    }
    .genre-btn.active { background: #eff6ff; border-color: #2563eb; color: #2563eb; font-weight: 500; }
    .genre-btn:hover:not(.active) { background: #f9fafb; }
    .search-bar {
      display: flex; gap: 8px; margin-bottom: 20px;
    }
    .search-input {
      flex: 1; padding: 10px 14px; border: 1px solid #d1d5db;
      border-radius: 8px; font-size: 15px; outline: none;
    }
    .search-input:focus { border-color: #2563eb; }
    .grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
      gap: 16px;
    }
    .book-card {
      background: white; border: 1px solid #e5e7eb; border-radius: 10px;
      padding: 16px; display: flex; flex-direction: column;
    }
    .book-title { font-weight: 600; font-size: 15px; margin-bottom: 4px; line-height: 1.3; }
    .book-author { font-size: 13px; color: #6b7280; margin-bottom: 6px; }
    .book-genre { font-size: 11px; background: #f3f4f6; color: #4b5563; padding: 2px 8px; border-radius: 12px; display: inline-block; margin-bottom: 10px; }
    .book-desc { font-size: 12px; color: #9ca3af; line-height: 1.4; flex: 1; margin-bottom: 12px; overflow: hidden; display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; }
    .book-footer { display: flex; align-items: center; justify-content: space-between; }
    .book-price { font-size: 18px; font-weight: 700; color: #111; }
    .book-stock { font-size: 11px; color: #9ca3af; }
    .add-btn {
      background: #2563eb; color: white; border: none;
      padding: 7px 14px; border-radius: 6px; font-size: 13px; cursor: pointer;
    }
    .add-btn:hover { background: #1d4ed8; }
    .add-btn:disabled { background: #93c5fd; cursor: not-allowed; }
    .pagination {
      display: flex; align-items: center; gap: 12px; justify-content: center;
      margin-top: 24px; padding: 12px;
    }
    .page-btn {
      padding: 6px 14px; border: 1px solid #d1d5db; border-radius: 6px;
      background: white; cursor: pointer; font-size: 14px;
    }
    .page-btn:disabled { opacity: 0.4; cursor: not-allowed; }
    .loading { text-align: center; padding: 40px; color: #6b7280; }
    .no-results { text-align: center; padding: 40px; color: #9ca3af; }
  `],
  template: `
    <div class="page">
      <!-- Sidebar Filters -->
      <div class="sidebar">
        <div class="filter-card">
          <h3>Genre</h3>
          <button class="genre-btn"
                  *ngFor="let g of genres"
                  [class.active]="selectedGenre() === g"
                  (click)="setGenre(g)">{{ g }}</button>
        </div>
        <div class="filter-card">
          <h3>Author</h3>
          <input class="filter-input" [(ngModel)]="authorFilter" placeholder="Filter by author"
                 (input)="applyFilters()">
        </div>
        <div class="filter-card">
          <h3>Price Range</h3>
          <input class="filter-input" [(ngModel)]="minPriceFilter" type="number" placeholder="Min price"
                 (change)="applyFilters()">
          <input class="filter-input" [(ngModel)]="maxPriceFilter" type="number" placeholder="Max price"
                 (change)="applyFilters()">
        </div>
      </div>

      <!-- Main Content -->
      <div class="main">
        <!-- Search Bar -->
        <div class="search-bar">
          <input class="search-input" [(ngModel)]="searchQuery" placeholder="Search books by title..."
                 (input)="applyFilters()">
          <button class="btn btn-secondary" (click)="clearFilters()">Clear</button>
        </div>

        <div *ngIf="loading()" class="loading">Loading books...</div>

        <div class="grid" *ngIf="!loading()">
          <div class="book-card" *ngFor="let book of books()">
            <div class="book-title">{{ book.title }}</div>
            <div class="book-author">{{ book.author }}</div>
            <span class="book-genre">{{ book.genre }}</span>
            <div class="book-desc">{{ book.description }}</div>
            <div class="book-footer">
              <div>
                <div class="book-price">${{ book.price | number:'1.2-2' }}</div>
                <div class="book-stock" *ngIf="book.stock > 0; else oosLabel">{{ book.stock }} in stock</div>
                <ng-template #oosLabel><div class="book-stock" style="color:#dc2626;">Out of stock</div></ng-template>
              </div>
              <button class="add-btn" [disabled]="book.stock === 0" (click)="addToCart(book)">
                Add to Cart
              </button>
            </div>
          </div>
        </div>

        <div *ngIf="!loading() && books().length === 0" class="no-results">
          No books found. Try different filters.
        </div>

        <!-- Pagination -->
        <div class="pagination" *ngIf="total() > pageSize">
          <button class="page-btn" [disabled]="currentPage() <= 1" (click)="goPage(currentPage() - 1)">← Prev</button>
          <span style="font-size:14px;color:#6b7280;">Page {{ currentPage() }} of {{ totalPages() }}</span>
          <button class="page-btn" [disabled]="currentPage() >= totalPages()" (click)="goPage(currentPage() + 1)">Next →</button>
        </div>
      </div>
    </div>
  `
})
export class ShopComponent implements OnInit {
  genres = GENRES;
  books = signal<Book[]>([]);
  loading = signal(true);
  total = signal(0);
  currentPage = signal(1);
  pageSize = 20;

  selectedGenre = signal('All');
  searchQuery = '';
  authorFilter = '';
  minPriceFilter: number | undefined;
  maxPriceFilter: number | undefined;

  totalPages = () => Math.ceil(this.total() / this.pageSize);

  constructor(
    private bookService: BookService,
    private cart: CartService,
    public auth: AuthService,
    private toast: ToastService
  ) {}

  ngOnInit(): void {
    this.loadBooks();
  }

  loadBooks(): void {
    this.loading.set(true);
    const params: BookQueryParams = {
      page: this.currentPage(),
      limit: this.pageSize
    };
    if (this.searchQuery.trim()) params.title = this.searchQuery.trim();
    if (this.authorFilter.trim()) params.author = this.authorFilter.trim();
    if (this.selectedGenre() !== 'All') params.genre = this.selectedGenre();
    if (this.minPriceFilter != null) params.minPrice = this.minPriceFilter;
    if (this.maxPriceFilter != null) params.maxPrice = this.maxPriceFilter;

    this.bookService.listBooks(params).subscribe({
      next: (res) => {
        this.books.set(res.data);
        this.total.set(res.total);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  applyFilters(): void {
    this.currentPage.set(1);
    this.loadBooks();
  }

  setGenre(genre: string): void {
    this.selectedGenre.set(genre);
    this.applyFilters();
  }

  clearFilters(): void {
    this.searchQuery = '';
    this.authorFilter = '';
    this.minPriceFilter = undefined;
    this.maxPriceFilter = undefined;
    this.selectedGenre.set('All');
    this.currentPage.set(1);
    this.loadBooks();
  }

  goPage(page: number): void {
    this.currentPage.set(page);
    this.loadBooks();
  }

  addToCart(book: Book): void {
    if (!this.auth.isLoggedIn()) {
      this.toast.show('Please login to add items to cart', 'info');
      return;
    }
    this.cart.addItem(book.id, 1).subscribe({
      next: () => this.toast.show(`"${book.title}" added to cart`, 'success'),
      error: (err) => this.toast.show(err.error?.error ?? 'Could not add to cart', 'error')
    });
  }
}
