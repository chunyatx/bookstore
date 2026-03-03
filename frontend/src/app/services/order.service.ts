import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Order } from '../models';

@Injectable({ providedIn: 'root' })
export class OrderService {
  constructor(private http: HttpClient) {}

  placeOrder(): Observable<Order> {
    return this.http.post<Order>('/api/orders', {});
  }

  listOrders(): Observable<Order[]> {
    return this.http.get<Order[]>('/api/orders');
  }

  getOrder(id: string): Observable<Order> {
    return this.http.get<Order>(`/api/orders/${id}`);
  }

  cancelOrder(id: string): Observable<Order> {
    return this.http.patch<Order>(`/api/orders/${id}/cancel`, {});
  }

  listAllOrders(status?: string): Observable<Order[]> {
    let params = new HttpParams();
    if (status) params = params.set('status', status);
    return this.http.get<Order[]>('/api/orders/admin/all', { params });
  }
}
