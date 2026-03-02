import { Component, OnInit, signal } from '@angular/core';
import { NgFor, NgIf, DecimalPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Coupon } from '../../../models';
import { AdminService } from '../../../services/admin.service';
import { ToastService } from '../../../services/toast.service';

@Component({
  selector: 'app-admin-coupons',
  standalone: true,
  imports: [NgFor, NgIf, DecimalPipe, DatePipe, FormsModule],
  styles: [`
    .header-row { display:flex;justify-content:space-between;align-items:center;margin-bottom:16px; }
    table { width:100%;border-collapse:collapse;font-size:14px; }
    th,td { padding:10px 12px;text-align:left;border-bottom:1px solid #e5e7eb; }
    th { font-weight:600;background:#f9fafb; }
    .modal-overlay { position:fixed;inset:0;background:rgba(0,0,0,0.5);display:flex;align-items:center;justify-content:center;z-index:1000; }
    .modal { background:white;border-radius:12px;padding:28px;width:420px;max-height:90vh;overflow-y:auto; }
    .modal h3 { margin-bottom:16px; }
    .form-row { display:grid;grid-template-columns:1fr 1fr;gap:12px; }
  `],
  template: `
    <div>
      <div class="header-row">
        <h2 style="font-size:18px;font-weight:600;">Coupons</h2>
        <button class="btn btn-primary btn-sm" (click)="createModal.set(true)">+ New Coupon</button>
      </div>

      <table>
        <thead>
          <tr><th>Code</th><th>Type</th><th>Value</th><th>Used/Max</th><th>Min Order</th><th>Status</th><th>Actions</th></tr>
        </thead>
        <tbody>
          <tr *ngFor="let c of coupons()">
            <td><strong>{{ c.code }}</strong></td>
            <td>{{ c.type }}</td>
            <td>{{ c.type === 'percentage' ? c.value + '%' : ('$' + (c.value | number:'1.2-2')) }}</td>
            <td>{{ c.usedCount }} / {{ c.maxUses ?? '∞' }}</td>
            <td>${{ c.minOrderAmount | number:'1.2-2' }}</td>
            <td>
              <span [style.color]="c.active ? '#16a34a' : '#dc2626'" [style.fontWeight]="600">
                {{ c.active ? 'Active' : 'Inactive' }}
              </span>
            </td>
            <td>
              <button *ngIf="c.active" class="btn btn-sm btn-danger" (click)="toggle(c, false)">Deactivate</button>
              <button *ngIf="!c.active" class="btn btn-sm btn-success" (click)="toggle(c, true)">Activate</button>
            </td>
          </tr>
        </tbody>
      </table>

      <!-- Create Coupon Modal -->
      <div class="modal-overlay" *ngIf="createModal()">
        <div class="modal">
          <h3>Create Coupon</h3>
          <div class="form-group">
            <label>Code</label>
            <input [(ngModel)]="newCode" placeholder="PROMO10" style="text-transform:uppercase;width:100%;padding:8px 12px;border:1px solid #d1d5db;border-radius:6px;">
          </div>
          <div class="form-row" style="margin-top:10px;">
            <div class="form-group">
              <label>Type</label>
              <select [(ngModel)]="newType" style="width:100%;padding:8px 12px;border:1px solid #d1d5db;border-radius:6px;">
                <option value="percentage">Percentage</option>
                <option value="fixed">Fixed ($)</option>
              </select>
            </div>
            <div class="form-group">
              <label>Value</label>
              <input type="number" [(ngModel)]="newValue" min="0.01" step="0.01" style="width:100%;padding:8px 12px;border:1px solid #d1d5db;border-radius:6px;">
            </div>
          </div>
          <div class="form-group" style="margin-top:10px;">
            <label>Description</label>
            <input [(ngModel)]="newDescription" placeholder="Describe the discount" style="width:100%;padding:8px 12px;border:1px solid #d1d5db;border-radius:6px;">
          </div>
          <div class="form-row" style="margin-top:10px;">
            <div class="form-group">
              <label>Min Order ($)</label>
              <input type="number" [(ngModel)]="newMinOrder" min="0" step="0.01" style="width:100%;padding:8px 12px;border:1px solid #d1d5db;border-radius:6px;">
            </div>
            <div class="form-group">
              <label>Max Uses (blank=∞)</label>
              <input type="number" [(ngModel)]="newMaxUses" min="1" placeholder="unlimited" style="width:100%;padding:8px 12px;border:1px solid #d1d5db;border-radius:6px;">
            </div>
          </div>
          <div class="form-group" style="margin-top:10px;">
            <label>Expires At (optional)</label>
            <input type="datetime-local" [(ngModel)]="newExpiresAt" style="width:100%;padding:8px 12px;border:1px solid #d1d5db;border-radius:6px;">
          </div>
          <div style="display:flex;gap:8px;margin-top:16px;">
            <button class="btn btn-secondary" (click)="createModal.set(false)">Cancel</button>
            <button class="btn btn-primary" (click)="submitCreate()">Create</button>
          </div>
        </div>
      </div>
    </div>
  `
})
export class AdminCouponsComponent implements OnInit {
  coupons = signal<Coupon[]>([]);
  createModal = signal(false);

  newCode = '';
  newType: 'percentage' | 'fixed' = 'percentage';
  newValue = 10;
  newDescription = '';
  newMinOrder = 0;
  newMaxUses: number | null = null;
  newExpiresAt = '';

  constructor(private adminService: AdminService, private toast: ToastService) {}

  ngOnInit(): void {
    this.adminService.listCoupons().subscribe(cs => this.coupons.set(cs));
  }

  toggle(coupon: Coupon, activate: boolean): void {
    const op = activate
      ? this.adminService.activateCoupon(coupon.code)
      : this.adminService.deactivateCoupon(coupon.code);
    op.subscribe({
      next: () => {
        this.toast.show(`Coupon ${activate ? 'activated' : 'deactivated'}`, 'success');
        this.adminService.listCoupons().subscribe(cs => this.coupons.set(cs));
      },
      error: (err) => this.toast.show(err.error?.error ?? 'Failed', 'error')
    });
  }

  submitCreate(): void {
    const body: any = {
      code: this.newCode.toUpperCase().trim(),
      type: this.newType,
      value: this.newValue,
      description: this.newDescription.trim(),
      minOrderAmount: this.newMinOrder || 0,
      maxUses: this.newMaxUses || null,
      expiresAt: this.newExpiresAt ? new Date(this.newExpiresAt).toISOString() : null
    };
    if (!body.code || !body.description || !body.value) {
      this.toast.show('Please fill required fields', 'error');
      return;
    }
    this.adminService.createCoupon(body).subscribe({
      next: () => {
        this.toast.show('Coupon created', 'success');
        this.createModal.set(false);
        this.adminService.listCoupons().subscribe(cs => this.coupons.set(cs));
      },
      error: (err) => this.toast.show(err.error?.error ?? 'Create failed', 'error')
    });
  }
}
