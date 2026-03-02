import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AccountInfo } from '../models';

@Injectable({ providedIn: 'root' })
export class AccountService {
  constructor(private http: HttpClient) {}

  getAccount(): Observable<AccountInfo> {
    return this.http.get<AccountInfo>('/api/account');
  }
}
