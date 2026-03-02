import { Component, OnInit, signal } from '@angular/core';
import { NgFor, NgIf, NgClass, DecimalPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Order } from '../../../models';
import { AdminService } from '../../../services/admin.service';
import { ToastService } from '../../../services/toast.service';

const STATUS_NEXT: Record<string, string> = { pending: 'confirmed', confirmed: 'shipped' };

@Component({
  selector: 'app-admin-orders',
  standalone: true,
  imports: [NgFor, NgIf, NgClass, DecimalPipe, DatePipe, FormsModule],
  styles: [`
    .filter-row { display:flex;gap:12px;margin-bottom:16px;align-items:center; }
    table { width:100%;border-collapse:collapse;font-size:14px; }
    th,td { padding:10px 12px;text-align:left;border-bottom:1px solid #e5e7eb; }
    th { font-weight:600;background:#f9fafb; }
    .actions { display:flex;gap:6px; }
  `],
  template: `
    <div>
      <div class="filter-row">
        <h2 style="font-size:18px;font-weight:600;">Orders</h2>
        <select [(ngModel)]="statusFilter" (change)="loadOrders()" style="padding:6px 10px;border:1px solid #d1d5db;border-radius:6px;font-size:14px;">
          <option value="">All Statuses</option>
          <option value="pending">Pending</option>
          <option value="confirmed">Confirmed</option>
          <option value="shipped">Shipped</option>
          <option value="cancelled">Cancelled</option>
        </select>
      </div>

      <table>
        <thead>
          <tr><th>Order ID</th><th>Date</th><th>Customer</th><th>Total</th><th>Status</th><th>Actions</th></tr>
        </thead>
        <tbody>
          <tr *ngFor="let o of orders()">
            <td>#{{ o.id.slice(0,8) }}</td>
            <td>{{ o.createdAt | date:'short' }}</td>
            <td>{{ o.userId.slice(0,8) }}</td>
            <td>${{ o.totalAmount | number:'1.2-2' }}</td>
            <td><span class="badge" [ngClass]="'badge-' + o.status">{{ o.status }}</span></td>
            <td>
              <div class="actions">
                <button *ngIf="getNextStatus(o.status)"
                        class="btn btn-sm btn-primary"
                        (click)="advanceStatus(o)">
                  → {{ getNextStatus(o.status) }}
                </button>
                <button *ngIf="o.status !== 'cancelled'"
                        class="btn btn-sm btn-danger"
                        (click)="refund(o)">
                  Refund
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  `
})
export class AdminOrdersComponent implements OnInit {
  orders = signal<Order[]>([]);
  statusFilter = '';

  constructor(private adminService: AdminService, private toast: ToastService) {}

  ngOnInit(): void { this.loadOrders(); }

  loadOrders(): void {
    this.adminService.listOrders(this.statusFilter || undefined).subscribe(os => this.orders.set(os));
  }

  getNextStatus(status: string): string | undefined {
    return STATUS_NEXT[status];
  }

  advanceStatus(order: Order): void {
    const next = STATUS_NEXT[order.status];
    if (!next) return;
    this.adminService.updateOrderStatus(order.id, next).subscribe({
      next: () => { this.toast.show('Status updated', 'success'); this.loadOrders(); },
      error: (err) => this.toast.show(err.error?.error ?? 'Update failed', 'error')
    });
  }

  refund(order: Order): void {
    if (!confirm('Refund and cancel this order?')) return;
    this.adminService.refundOrder(order.id).subscribe({
      next: () => { this.toast.show('Order refunded', 'success'); this.loadOrders(); },
      error: (err) => this.toast.show(err.error?.error ?? 'Refund failed', 'error')
    });
  }
}
