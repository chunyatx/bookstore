import { Component, OnInit, signal } from '@angular/core';
import { NgFor, NgIf, DecimalPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CustomerDetail, CustomerSummary } from '../../../models';
import { AdminService } from '../../../services/admin.service';
import { ToastService } from '../../../services/toast.service';

@Component({
  selector: 'app-admin-customers',
  standalone: true,
  imports: [NgFor, NgIf, DecimalPipe, DatePipe, FormsModule],
  styles: [`
    table { width: 100%; border-collapse: collapse; font-size: 14px; }
    th, td { padding: 10px 12px; text-align: left; border-bottom: 1px solid #e5e7eb; }
    th { font-weight: 600; background: #f9fafb; }
    tr:hover td { background: #f9fafb; }
    .expandable { cursor: pointer; }
    .detail-panel { background: #f9fafb; padding: 16px; border-radius: 8px; margin: 8px 0; }
    .actions { display: flex; gap: 8px; margin-top: 12px; }
    .modal-overlay { position:fixed;inset:0;background:rgba(0,0,0,0.5);display:flex;align-items:center;justify-content:center;z-index:1000; }
    .modal { background:white;border-radius:12px;padding:28px;width:360px; }
    .modal h3 { margin-bottom:16px; }
  `],
  template: `
    <div>
      <h2 style="font-size:18px;font-weight:600;margin-bottom:16px;">Customers</h2>
      <table>
        <thead>
          <tr>
            <th>Name</th>
            <th>Email</th>
            <th>Balance</th>
            <th>Orders</th>
            <th>Level</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let c of customers()">
            <td>{{ c.name }}</td>
            <td>{{ c.email }}</td>
            <td>\${{ c.balance | number:'1.2-2' }}</td>
            <td>{{ c.orderCount }}</td>
            <td>{{ c.accountLevel ?? '—' }}</td>
            <td>
              <button class="btn btn-sm btn-secondary" (click)="viewDetail(c.id)">Details</button>
              <button class="btn btn-sm btn-success" style="margin-left:6px;" (click)="openAdjust(c, 'credit')">Credit</button>
              <button class="btn btn-sm btn-danger" style="margin-left:4px;" (click)="openAdjust(c, 'debit')">Debit</button>
              <button class="btn btn-sm btn-secondary" style="margin-left:4px;" (click)="openSetLevel(c)">Set Level</button>
            </td>
          </tr>
        </tbody>
      </table>

      <!-- Detail panel -->
      <div class="detail-panel" *ngIf="selectedCustomer()">
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px;">
          <strong>{{ selectedCustomer()?.name }} — \${{ selectedCustomer()?.balance | number:'1.2-2' }}</strong>
          <button style="background:none;border:none;cursor:pointer;font-size:18px;" (click)="selectedCustomer.set(null)">×</button>
        </div>
        <div style="font-size:13px;color:#6b7280;margin-bottom:8px;">Recent Transactions</div>
        <div *ngFor="let tx of selectedCustomer()?.transactions?.slice(0,5)"
             style="font-size:13px;padding:6px 0;border-bottom:1px solid #e5e7eb;">
          <span [style.color]="tx.type === 'credit' || tx.type === 'refund' ? '#16a34a' : '#dc2626'">
            {{ tx.type === 'credit' || tx.type === 'refund' ? '+' : '−' }}\${{ tx.amount | number:'1.2-2' }}
          </span>
          — {{ tx.description }}
          <span style="color:#9ca3af;"> ({{ tx.createdAt | date:'short' }})</span>
        </div>
      </div>

      <!-- Set Level modal -->
      <div class="modal-overlay" *ngIf="levelModal()">
        <div class="modal">
          <h3>Set Account Level — {{ levelTarget()?.name }}</h3>
          <div class="form-group">
            <label>Account Level (blank to clear)</label>
            <input [(ngModel)]="levelValue" placeholder="e.g. A00001" style="width:100%;padding:8px 12px;border:1px solid #d1d5db;border-radius:6px;">
          </div>
          <div style="display:flex;gap:8px;margin-top:16px;">
            <button class="btn btn-secondary" (click)="levelModal.set(false)">Cancel</button>
            <button class="btn btn-primary" (click)="submitSetLevel()">Save</button>
          </div>
        </div>
      </div>

      <!-- Adjust modal -->
      <div class="modal-overlay" *ngIf="adjustModal()">
        <div class="modal">
          <h3>{{ adjustMode() === 'credit' ? 'Credit' : 'Debit' }} Wallet — {{ adjustTarget()?.name }}</h3>
          <div class="form-group">
            <label>Amount ($)</label>
            <input type="number" [(ngModel)]="adjustAmount" min="0.01" step="0.01" class="form-group input" style="width:100%;padding:8px 12px;border:1px solid #d1d5db;border-radius:6px;">
          </div>
          <div class="form-group" style="margin-top:10px;">
            <label>Description</label>
            <input [(ngModel)]="adjustDescription" placeholder="Reason..." style="width:100%;padding:8px 12px;border:1px solid #d1d5db;border-radius:6px;">
          </div>
          <div style="display:flex;gap:8px;margin-top:16px;">
            <button class="btn btn-secondary" (click)="adjustModal.set(false)">Cancel</button>
            <button class="btn btn-primary" (click)="submitAdjust()">Confirm</button>
          </div>
        </div>
      </div>
    </div>
  `
})
export class AdminCustomersComponent implements OnInit {
  customers = signal<CustomerSummary[]>([]);
  selectedCustomer = signal<CustomerDetail | null>(null);
  adjustModal = signal(false);
  adjustMode = signal<'credit' | 'debit'>('credit');
  adjustTarget = signal<CustomerSummary | null>(null);
  adjustAmount = 0;
  adjustDescription = '';

  levelModal = signal(false);
  levelTarget = signal<CustomerSummary | null>(null);
  levelValue = '';

  constructor(private adminService: AdminService, private toast: ToastService) {}

  ngOnInit(): void {
    this.adminService.listCustomers().subscribe(cs => this.customers.set(cs));
  }

  viewDetail(userId: string): void {
    this.adminService.getCustomer(userId).subscribe(c => this.selectedCustomer.set(c));
  }

  openAdjust(customer: CustomerSummary, mode: 'credit' | 'debit'): void {
    this.adjustTarget.set(customer);
    this.adjustMode.set(mode);
    this.adjustAmount = 0;
    this.adjustDescription = '';
    this.adjustModal.set(true);
  }

  openSetLevel(customer: CustomerSummary): void {
    this.levelTarget.set(customer);
    this.levelValue = customer.accountLevel ?? '';
    this.levelModal.set(true);
  }

  submitSetLevel(): void {
    const target = this.levelTarget();
    if (!target) return;
    this.adminService.setCustomerLevel(target.id, this.levelValue.trim() || null).subscribe({
      next: () => {
        this.toast.show('Account level updated', 'success');
        this.levelModal.set(false);
        this.adminService.listCustomers().subscribe(cs => this.customers.set(cs));
      },
      error: (err) => this.toast.show(err.error?.error ?? 'Update failed', 'error')
    });
  }

  submitAdjust(): void {
    const target = this.adjustTarget();
    if (!target || !this.adjustAmount || !this.adjustDescription.trim()) {
      this.toast.show('Please fill all fields', 'error');
      return;
    }
    const op = this.adjustMode() === 'credit'
      ? this.adminService.creditCustomer(target.id, this.adjustAmount, this.adjustDescription)
      : this.adminService.debitCustomer(target.id, this.adjustAmount, this.adjustDescription);

    op.subscribe({
      next: () => {
        this.toast.show('Balance updated', 'success');
        this.adjustModal.set(false);
        this.adminService.listCustomers().subscribe(cs => this.customers.set(cs));
      },
      error: (err) => this.toast.show(err.error?.error ?? 'Operation failed', 'error')
    });
  }
}
