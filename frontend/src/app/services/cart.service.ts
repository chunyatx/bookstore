import { Injectable, computed, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { EnrichedCart } from '../models';

@Injectable({ providedIn: 'root' })
export class CartService {
  private _cart = signal<EnrichedCart | null>(null);
  readonly cart = this._cart.asReadonly();
  readonly itemCount = computed(() => this._cart()?.items.length ?? 0);

  constructor(private http: HttpClient) {}

  loadCart(): Observable<EnrichedCart> {
    return this.http.get<EnrichedCart>('/api/cart').pipe(
      tap(cart => this._cart.set(cart))
    );
  }

  addItem(bookId: string, quantity: number): Observable<EnrichedCart> {
    return this.http.post<EnrichedCart>('/api/cart/items', { bookId, quantity }).pipe(
      tap(cart => this._cart.set(cart))
    );
  }

  updateItem(bookId: string, quantity: number): Observable<EnrichedCart> {
    return this.http.patch<EnrichedCart>(`/api/cart/items/${bookId}`, { quantity }).pipe(
      tap(cart => this._cart.set(cart))
    );
  }

  clearCart(): Observable<void> {
    return this.http.delete<void>('/api/cart').pipe(
      tap(() => this._cart.set(null))
    );
  }

  applyCoupon(code: string): Observable<EnrichedCart> {
    return this.http.post<EnrichedCart>('/api/cart/coupon', { code }).pipe(
      tap(cart => this._cart.set(cart))
    );
  }

  removeCoupon(): Observable<EnrichedCart> {
    return this.http.delete<EnrichedCart>('/api/cart/coupon').pipe(
      tap(cart => this._cart.set(cart))
    );
  }

  clearLocal(): void {
    this._cart.set(null);
  }
}
