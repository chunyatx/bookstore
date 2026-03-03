import { Component, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { NavbarComponent } from './components/navbar/navbar.component';
import { CartPanelComponent } from './components/cart/cart-panel.component';
import { AuthModalComponent } from './components/auth/auth-modal.component';
import { ToastService } from './services/toast.service';
import { NgClass, NgIf } from '@angular/common';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, NavbarComponent, CartPanelComponent, AuthModalComponent, NgClass, NgIf],
  template: `
    <app-navbar
      (openCart)="cartOpen.set(true)"
      (openAuth)="authOpen.set(true)">
    </app-navbar>

    <main style="padding-top: 64px;">
      <router-outlet></router-outlet>
    </main>

    <app-cart-panel
      [open]="cartOpen()"
      (close)="cartOpen.set(false)">
    </app-cart-panel>

    <app-auth-modal
      *ngIf="authOpen()"
      (close)="authOpen.set(false)">
    </app-auth-modal>

    <!-- Toast -->
    <div *ngIf="toastService.toast() as t"
         class="toast"
         [ngClass]="'toast-' + t.type">
      {{ t.text }}
    </div>
  `
})
export class AppComponent {
  cartOpen = signal(false);
  authOpen = signal(false);
  constructor(public toastService: ToastService) {}
}
