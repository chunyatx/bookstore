import { Component, EventEmitter, Output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgIf } from '@angular/common';
import { AuthService } from '../../services/auth.service';
import { CartService } from '../../services/cart.service';
import { ToastService } from '../../services/toast.service';

@Component({
  selector: 'app-auth-modal',
  standalone: true,
  imports: [FormsModule, NgIf],
  styles: [`
    .tabs { display: flex; border-bottom: 1px solid #e5e7eb; margin-bottom: 20px; }
    .tab {
      padding: 8px 16px; cursor: pointer; font-size: 14px; font-weight: 500;
      border: none; background: none; color: #6b7280;
      border-bottom: 2px solid transparent; margin-bottom: -1px;
    }
    .tab.active { color: #2563eb; border-bottom-color: #2563eb; }
    .close-btn {
      float: right; background: none; border: none;
      font-size: 20px; cursor: pointer; color: #6b7280; margin-top: -4px;
    }
  `],
  template: `
    <div class="modal-overlay" (click)="onOverlayClick($event)">
      <div class="modal">
        <button class="close-btn" (click)="close.emit()">×</button>
        <h2>{{ mode() === 'login' ? 'Welcome back' : 'Create account' }}</h2>

        <div class="tabs">
          <button class="tab" [class.active]="mode()==='login'" (click)="mode.set('login')">Login</button>
          <button class="tab" [class.active]="mode()==='register'" (click)="mode.set('register')">Register</button>
        </div>

        <div *ngIf="error()" class="error-msg" style="margin-bottom:12px;">{{ error() }}</div>

        <form (ngSubmit)="submit()" #form="ngForm">
          <div class="form-group" *ngIf="mode()==='register'">
            <label>Name</label>
            <input [(ngModel)]="name" name="name" required minlength="1" placeholder="Your name">
          </div>
          <div class="form-group">
            <label>Email</label>
            <input [(ngModel)]="email" name="email" type="email" required placeholder="you@example.com">
          </div>
          <div class="form-group">
            <label>Password</label>
            <input [(ngModel)]="password" name="password" type="password" required minlength="6" placeholder="••••••••">
          </div>
          <button type="submit" class="btn btn-primary" style="width:100%;" [disabled]="loading()">
            {{ loading() ? 'Please wait...' : (mode() === 'login' ? 'Login' : 'Create Account') }}
          </button>
        </form>
      </div>
    </div>
  `
})
export class AuthModalComponent {
  @Output() close = new EventEmitter<void>();

  mode = signal<'login' | 'register'>('login');
  loading = signal(false);
  error = signal('');

  email = '';
  password = '';
  name = '';

  constructor(
    private auth: AuthService,
    private cart: CartService,
    private toast: ToastService
  ) {}

  onOverlayClick(e: MouseEvent): void {
    if ((e.target as HTMLElement).classList.contains('modal-overlay')) {
      this.close.emit();
    }
  }

  submit(): void {
    this.error.set('');
    this.loading.set(true);

    if (this.mode() === 'login') {
      this.auth.login(this.email, this.password).subscribe({
        next: () => {
          this.cart.loadCart().subscribe();
          this.toast.show('Welcome back!', 'success');
          this.close.emit();
        },
        error: (err) => {
          this.error.set(err.error?.error ?? 'Login failed');
          this.loading.set(false);
        }
      });
    } else {
      this.auth.register(this.email, this.password, this.name).subscribe({
        next: () => {
          this.mode.set('login');
          this.toast.show('Account created! Please login.', 'success');
          this.loading.set(false);
        },
        error: (err) => {
          this.error.set(err.error?.error ?? 'Registration failed');
          this.loading.set(false);
        }
      });
    }
  }
}
