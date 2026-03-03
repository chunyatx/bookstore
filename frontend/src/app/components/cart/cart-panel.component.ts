import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, signal } from '@angular/core';
import { NgClass, NgFor, NgIf, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CartService } from '../../services/cart.service';
import { OrderService } from '../../services/order.service';
import { AuthService } from '../../services/auth.service';
import { ToastService } from '../../services/toast.service';

@Component({
  selector: 'app-cart-panel',
  standalone: true,
  imports: [NgClass, NgFor, NgIf, DecimalPipe, FormsModule],
  styles: [`
    .overlay {
      position: fixed; inset: 0;
      background: rgba(0,0,0,0.4);
      z-index: 899;
    }
    .panel {
      position: fixed; top: 0; right: 0;
      height: 100%; width: 420px; max-width: 95vw;
      background: white;
      box-shadow: -4px 0 24px rgba(0,0,0,0.15);
      z-index: 900;
      display: flex; flex-direction: column;
      transform: translateX(100%);
      transition: transform 0.25s ease;
    }
    .panel.open { transform: translateX(0); }
    .panel-header {
      padding: 20px;
      border-bottom: 1px solid #e5e7eb;
      display: flex; align-items: center; justify-content: space-between;
    }
    .panel-header h2 { font-size: 18px; }
    .close-btn { background: none; border: none; font-size: 22px; cursor: pointer; color: #6b7280; }
    .panel-body { flex: 1; overflow-y: auto; padding: 16px; }
    .item {
      display: flex; gap: 12px;
      padding: 12px 0;
      border-bottom: 1px solid #f3f4f6;
    }
    .item-info { flex: 1; }
    .item-title { font-weight: 500; font-size: 14px; margin-bottom: 2px; }
    .item-price { font-size: 13px; color: #6b7280; }
    .qty-control { display: flex; align-items: center; gap: 8px; margin-top: 8px; }
    .qty-btn {
      width: 28px; height: 28px; border: 1px solid #d1d5db;
      border-radius: 4px; background: white; cursor: pointer;
      display: flex; align-items: center; justify-content: center; font-size: 16px;
    }
    .qty-btn:hover { background: #f3f4f6; }
    .panel-footer {
      border-top: 1px solid #e5e7eb;
      padding: 16px;
    }
    .totals { margin-bottom: 12px; font-size: 14px; }
    .totals > div { display: flex; justify-content: space-between; padding: 4px 0; }
    .totals .total { font-weight: 700; font-size: 16px; }
    .coupon-row { display: flex; gap: 8px; margin-bottom: 12px; }
    .coupon-input {
      flex: 1; padding: 8px 12px; border: 1px solid #d1d5db;
      border-radius: 6px; font-size: 14px; outline: none; text-transform: uppercase;
    }
    .coupon-input:focus { border-color: #2563eb; }
    .empty { text-align: center; color: #9ca3af; padding: 40px 20px; }
  `],
  template: `
    <div class="overlay" *ngIf="open" (click)="close.emit()"></div>
    <div class="panel" [ngClass]="{open: open}">
      <div class="panel-header">
        <h2>🛒 Your Cart</h2>
        <button class="close-btn" (click)="close.emit()">×</button>
      </div>

      <div class="panel-body">
        <ng-container *ngIf="cart.cart() as c; else emptyTpl">
          <ng-container *ngIf="c.items.length > 0; else emptyTpl">
            <div class="item" *ngFor="let item of c.items">
              <div class="item-info">
                <div class="item-title">{{ item.title }}</div>
                <div class="item-price">\${{ item.priceAtAdd | number:'1.2-2' }} each</div>
                <div class="qty-control">
                  <button class="qty-btn" (click)="changeQty(item.bookId, item.quantity - 1)">−</button>
                  <span>{{ item.quantity }}</span>
                  <button class="qty-btn" (click)="changeQty(item.bookId, item.quantity + 1)">+</button>
                </div>
              </div>
              <div style="font-weight:600;">
                \${{ (item.priceAtAdd * item.quantity) | number:'1.2-2' }}
              </div>
            </div>
          </ng-container>
        </ng-container>
        <ng-template #emptyTpl>
          <div class="empty">Your cart is empty</div>
        </ng-template>
      </div>

      <div class="panel-footer" *ngIf="cart.cart()?.items?.length">
        <!-- Coupon row -->
        <div class="coupon-row" *ngIf="!cart.cart()?.couponCode; else couponApplied">
          <input class="coupon-input" [(ngModel)]="couponCode" placeholder="COUPON CODE">
          <button class="btn btn-secondary btn-sm" (click)="applyCoupon()">Apply</button>
        </div>
        <ng-template #couponApplied>
          <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px;font-size:13px;">
            <span style="color:#16a34a;">✓ {{ cart.cart()?.couponCode }} applied</span>
            <button class="btn btn-sm" style="background:none;color:#dc2626;border:none;" (click)="removeCoupon()">Remove</button>
          </div>
        </ng-template>

        <!-- Totals -->
        <div class="totals">
          <div><span>Subtotal</span><span>\${{ cart.cart()?.subtotal | number:'1.2-2' }}</span></div>
          <div *ngIf="cart.cart()?.discountAmount" style="color:#16a34a;">
            <span>Discount ({{ cart.cart()?.couponCode }})</span>
            <span>−\${{ cart.cart()?.discountAmount | number:'1.2-2' }}</span>
          </div>
          <div class="total"><span>Total</span><span>\${{ cart.cart()?.finalTotal | number:'1.2-2' }}</span></div>
        </div>

        <button class="btn btn-primary" style="width:100%;" [disabled]="checkoutLoading()" (click)="checkout()">
          {{ checkoutLoading() ? 'Processing...' : 'Checkout' }}
        </button>

        <div *ngIf="checkoutError()" class="error-msg" style="margin-top:8px;">{{ checkoutError() }}</div>
      </div>
    </div>
  `
})
export class CartPanelComponent implements OnChanges {
  @Input() open = false;
  @Output() close = new EventEmitter<void>();

  couponCode = '';
  checkoutLoading = signal(false);
  checkoutError = signal('');

  constructor(
    public cart: CartService,
    private auth: AuthService,
    private orderService: OrderService,
    private toast: ToastService
  ) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['open']?.currentValue === true && this.auth.isLoggedIn()) {
      this.cart.loadCart().subscribe();
    }
  }

  changeQty(bookId: string, qty: number): void {
    this.cart.updateItem(bookId, qty).subscribe({
      error: (err) => this.toast.show(err.error?.error ?? 'Update failed', 'error')
    });
  }

  applyCoupon(): void {
    if (!this.couponCode.trim()) return;
    this.cart.applyCoupon(this.couponCode.trim()).subscribe({
      next: () => { this.couponCode = ''; this.toast.show('Coupon applied!', 'success'); },
      error: (err) => this.toast.show(err.error?.error ?? 'Invalid coupon', 'error')
    });
  }

  removeCoupon(): void {
    this.cart.removeCoupon().subscribe();
  }

  checkout(): void {
    this.checkoutError.set('');
    this.checkoutLoading.set(true);
    this.orderService.placeOrder().subscribe({
      next: (order) => {
        this.checkoutLoading.set(false);
        this.toast.show('Order placed! #' + order.id.slice(0, 8), 'success');
        this.close.emit();
      },
      error: (err) => {
        this.checkoutLoading.set(false);
        this.checkoutError.set(err.error?.error ?? 'Checkout failed');
      }
    });
  }
}
