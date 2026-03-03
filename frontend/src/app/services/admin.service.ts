import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Coupon, CustomerDetail, CustomerSummary, Order } from '../models';

@Injectable({ providedIn: 'root' })
export class AdminService {
  constructor(private http: HttpClient) {}

  // Customers
  listCustomers(): Observable<CustomerSummary[]> {
    return this.http.get<CustomerSummary[]>('/api/admin/customers');
  }

  getCustomer(userId: string): Observable<CustomerDetail> {
    return this.http.get<CustomerDetail>(`/api/admin/customers/${userId}`);
  }

  creditCustomer(userId: string, amount: number, description: string): Observable<any> {
    return this.http.post(`/api/admin/customers/${userId}/credit`, { amount, description });
  }

  debitCustomer(userId: string, amount: number, description: string): Observable<any> {
    return this.http.post(`/api/admin/customers/${userId}/debit`, { amount, description });
  }

  // Orders
  listOrders(status?: string): Observable<Order[]> {
    let params = new HttpParams();
    if (status) params = params.set('status', status);
    return this.http.get<Order[]>('/api/admin/orders', { params });
  }

  updateOrderStatus(orderId: string, status: string): Observable<Order> {
    return this.http.patch<Order>(`/api/admin/orders/${orderId}/status`, { status });
  }

  refundOrder(orderId: string): Observable<Order> {
    return this.http.post<Order>(`/api/admin/orders/${orderId}/refund`, {});
  }

  // Coupons
  listCoupons(): Observable<Coupon[]> {
    return this.http.get<Coupon[]>('/api/admin/coupons');
  }

  createCoupon(data: any): Observable<Coupon> {
    return this.http.post<Coupon>('/api/admin/coupons', data);
  }

  activateCoupon(code: string): Observable<Coupon> {
    return this.http.patch<Coupon>(`/api/admin/coupons/${code}/activate`, {});
  }

  deactivateCoupon(code: string): Observable<Coupon> {
    return this.http.patch<Coupon>(`/api/admin/coupons/${code}/deactivate`, {});
  }
}
