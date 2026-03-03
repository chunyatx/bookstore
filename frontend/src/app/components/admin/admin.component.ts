import { Component, signal } from '@angular/core';
import { NgIf, NgClass } from '@angular/common';
import { AdminCustomersComponent } from './admin-customers/admin-customers.component';
import { AdminOrdersComponent } from './admin-orders/admin-orders.component';
import { AdminCouponsComponent } from './admin-coupons/admin-coupons.component';

type Tab = 'customers' | 'orders' | 'coupons';

@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [NgIf, NgClass, AdminCustomersComponent, AdminOrdersComponent, AdminCouponsComponent],
  styles: [`
    .page { max-width: 1200px; margin: 0 auto; padding: 32px 24px; }
    h1 { font-size: 24px; margin-bottom: 24px; }
    .tabs { display: flex; gap: 8px; margin-bottom: 24px; border-bottom: 2px solid #e5e7eb; padding-bottom: 0; }
    .tab {
      padding: 10px 20px; border: none; background: none; cursor: pointer;
      font-size: 14px; font-weight: 500; color: #6b7280;
      border-bottom: 2px solid transparent; margin-bottom: -2px;
    }
    .tab.active { color: #2563eb; border-bottom-color: #2563eb; }
    .tab:hover:not(.active) { color: #374151; }
    .tab-content { background: white; border: 1px solid #e5e7eb; border-radius: 8px; padding: 20px; }
  `],
  template: `
    <div class="page">
      <h1>🛠️ Admin Panel</h1>
      <div class="tabs">
        <button class="tab" [class.active]="activeTab() === 'customers'" (click)="activeTab.set('customers')">
          👥 Customers
        </button>
        <button class="tab" [class.active]="activeTab() === 'orders'" (click)="activeTab.set('orders')">
          📦 Orders
        </button>
        <button class="tab" [class.active]="activeTab() === 'coupons'" (click)="activeTab.set('coupons')">
          🏷️ Coupons
        </button>
      </div>
      <div class="tab-content">
        <app-admin-customers *ngIf="activeTab() === 'customers'"></app-admin-customers>
        <app-admin-orders *ngIf="activeTab() === 'orders'"></app-admin-orders>
        <app-admin-coupons *ngIf="activeTab() === 'coupons'"></app-admin-coupons>
      </div>
    </div>
  `
})
export class AdminComponent {
  activeTab = signal<Tab>('customers');
}
