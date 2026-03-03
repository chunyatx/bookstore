import { Component, EventEmitter, OnDestroy, Output, effect, signal } from '@angular/core';
import { Subscription } from 'rxjs';
import { RouterLink } from '@angular/router';
import { DecimalPipe, NgIf } from '@angular/common';
import { AuthService } from '../../services/auth.service';
import { CartService } from '../../services/cart.service';
import { AccountService } from '../../services/account.service';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [RouterLink, NgIf, DecimalPipe],
  styles: [`
    nav {
      position: fixed;
      top: 0; left: 0; right: 0;
      height: 64px;
      background: #1e3a5f;
      color: white;
      display: flex;
      align-items: center;
      padding: 0 24px;
      z-index: 800;
      gap: 16px;
    }
    .brand {
      font-size: 20px;
      font-weight: 700;
      color: white;
      text-decoration: none;
      margin-right: auto;
    }
    .nav-btn {
      background: none;
      border: 1px solid rgba(255,255,255,0.4);
      color: white;
      padding: 6px 14px;
      border-radius: 6px;
      font-size: 14px;
      cursor: pointer;
    }
    .nav-btn:hover { background: rgba(255,255,255,0.1); }
    .cart-btn {
      position: relative;
      background: #2563eb;
      border: none;
      color: white;
      padding: 8px 16px;
      border-radius: 6px;
      font-size: 14px;
      cursor: pointer;
    }
    .cart-badge {
      position: absolute;
      top: -6px; right: -6px;
      background: #ef4444;
      color: white;
      width: 18px; height: 18px;
      border-radius: 50%;
      font-size: 11px;
      display: flex; align-items: center; justify-content: center;
    }
    .user-menu { position: relative; }
    .dropdown {
      position: absolute;
      top: calc(100% + 8px);
      right: 0;
      background: white;
      color: #333;
      border-radius: 8px;
      box-shadow: 0 4px 20px rgba(0,0,0,0.15);
      min-width: 180px;
      overflow: hidden;
    }
    .dropdown-item {
      display: block;
      padding: 10px 16px;
      font-size: 14px;
      cursor: pointer;
      border: none;
      background: none;
      width: 100%;
      text-align: left;
      color: #333;
    }
    .dropdown-item:hover { background: #f3f4f6; }
    .dropdown-sep { border: none; border-top: 1px solid #e5e7eb; margin: 4px 0; }
    .balance-info {
      padding: 10px 16px;
      font-size: 12px;
      color: #6b7280;
      border-bottom: 1px solid #e5e7eb;
    }
  `],
  template: `
    <nav>
      <a class="brand" routerLink="/">📚 Bookstore</a>

      <a routerLink="/orders" *ngIf="auth.isLoggedIn()" style="color:white;font-size:14px;">My Orders</a>
      <a routerLink="/admin" *ngIf="auth.currentUser()?.role === 'admin'" style="color:#93c5fd;font-size:14px;">Admin</a>

      <button class="cart-btn" (click)="openCart.emit()">
        🛒 Cart
        <span class="cart-badge" *ngIf="cart.itemCount() > 0">{{ cart.itemCount() }}</span>
      </button>

      <div class="user-menu" *ngIf="auth.isLoggedIn(); else loginBtn">
        <button class="nav-btn" (click)="menuOpen.set(!menuOpen())">
          {{ auth.currentUser()?.name }} ▾
        </button>
        <div class="dropdown" *ngIf="menuOpen()">
          <div class="balance-info">Balance: \${{ balance() | number:'1.2-2' }}</div>
          <button class="dropdown-item" (click)="loadBalance()">🔄 Refresh Balance</button>
          <hr class="dropdown-sep">
          <button class="dropdown-item" (click)="doLogout()">🚪 Logout</button>
        </div>
      </div>
      <ng-template #loginBtn>
        <button class="nav-btn" (click)="openAuth.emit()">Login / Register</button>
      </ng-template>
    </nav>
  `
})
export class NavbarComponent implements OnDestroy {
  @Output() openCart = new EventEmitter<void>();
  @Output() openAuth = new EventEmitter<void>();

  menuOpen = signal(false);
  balance = signal(0);

  private sub: Subscription;

  constructor(
    public auth: AuthService,
    public cart: CartService,
    private accountService: AccountService
  ) {
    effect(() => {
      if (this.auth.isLoggedIn()) {
        this.loadBalance();
      } else {
        this.balance.set(0);
      }
    });
    this.sub = this.accountService.balanceRefresh$.subscribe(() => this.loadBalance());
  }

  ngOnDestroy(): void {
    this.sub.unsubscribe();
  }

  doLogout(): void {
    this.auth.logout();
    this.cart.clearLocal();
    this.menuOpen.set(false);
    this.balance.set(0);
  }

  loadBalance(): void {
    if (!this.auth.isLoggedIn()) return;
    this.accountService.getAccount().subscribe(a => this.balance.set(a.balance));
  }
}
