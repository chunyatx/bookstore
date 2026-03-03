import { Component, OnInit, signal } from '@angular/core';
import { NgFor, NgIf, NgClass, DecimalPipe, DatePipe } from '@angular/common';
import { Order } from '../../models';
import { OrderService } from '../../services/order.service';
import { ToastService } from '../../services/toast.service';

@Component({
  selector: 'app-orders',
  standalone: true,
  imports: [NgFor, NgIf, NgClass, DecimalPipe, DatePipe],
  styles: [`
    .page { max-width: 900px; margin: 0 auto; padding: 32px 24px; }
    h1 { font-size: 24px; margin-bottom: 24px; }
    .order-card {
      background: white; border: 1px solid #e5e7eb; border-radius: 10px;
      padding: 20px; margin-bottom: 16px;
    }
    .order-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; flex-wrap: wrap; gap: 8px; }
    .order-id { font-weight: 600; font-size: 15px; }
    .order-date { font-size: 13px; color: #6b7280; }
    .items-list { margin: 12px 0; }
    .order-item { display: flex; justify-content: space-between; font-size: 14px; padding: 4px 0; border-bottom: 1px solid #f3f4f6; }
    .order-item:last-child { border-bottom: none; }
    .order-footer { display: flex; justify-content: space-between; align-items: center; margin-top: 12px; flex-wrap: wrap; gap: 8px; }
    .totals { font-size: 14px; }
    .totals .total { font-weight: 700; }
    .empty { text-align: center; padding: 60px; color: #9ca3af; font-size: 16px; }
  `],
  template: `
    <div class="page">
      <h1>My Orders</h1>

      <div *ngIf="loading()" style="text-align:center;padding:40px;color:#6b7280;">Loading orders...</div>

      <div *ngIf="!loading() && orders().length === 0" class="empty">
        <div style="font-size:40px;margin-bottom:16px;">📦</div>
        <div>No orders yet. Start shopping!</div>
      </div>

      <div class="order-card" *ngFor="let order of orders()">
        <div class="order-header">
          <div>
            <div class="order-id">Order #{{ order.id.slice(0, 8) }}</div>
            <div class="order-date">{{ order.createdAt | date:'medium' }}</div>
          </div>
          <span class="badge" [ngClass]="'badge-' + order.status">{{ order.status }}</span>
        </div>

        <div class="items-list">
          <div class="order-item" *ngFor="let item of order.items">
            <span>{{ item.title }} × {{ item.quantity }}</span>
            <span>\${{ (item.priceAtOrder * item.quantity) | number:'1.2-2' }}</span>
          </div>
        </div>

        <div class="order-footer">
          <div class="totals">
            <div>Subtotal: \${{ order.subtotal | number:'1.2-2' }}</div>
            <div *ngIf="order.discountAmount > 0" style="color:#16a34a;">
              Discount ({{ order.couponCode }}): −\${{ order.discountAmount | number:'1.2-2' }}
            </div>
            <div class="total">Total: \${{ order.totalAmount | number:'1.2-2' }}</div>
          </div>
          <button *ngIf="order.status === 'pending'"
                  class="btn btn-sm btn-danger"
                  (click)="cancelOrder(order)">
            Cancel Order
          </button>
        </div>
      </div>
    </div>
  `
})
export class OrdersComponent implements OnInit {
  orders = signal<Order[]>([]);
  loading = signal(true);

  constructor(
    private orderService: OrderService,
    private toast: ToastService
  ) {}

  ngOnInit(): void {
    this.loadOrders();
  }

  loadOrders(): void {
    this.loading.set(true);
    this.orderService.listOrders().subscribe({
      next: (orders) => {
        this.orders.set(orders);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  cancelOrder(order: Order): void {
    if (!confirm('Cancel this order?')) return;
    this.orderService.cancelOrder(order.id).subscribe({
      next: () => {
        this.toast.show('Order cancelled. Wallet refunded.', 'success');
        this.loadOrders();
      },
      error: (err) => this.toast.show(err.error?.error ?? 'Cancel failed', 'error')
    });
  }
}
