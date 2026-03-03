import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, Subject } from 'rxjs';
import { AccountInfo } from '../models';

@Injectable({ providedIn: 'root' })
export class AccountService {
  private refreshTrigger$ = new Subject<void>();
  readonly balanceRefresh$ = this.refreshTrigger$.asObservable();

  constructor(private http: HttpClient) {}

  getAccount(): Observable<AccountInfo> {
    return this.http.get<AccountInfo>('/api/account');
  }

  triggerRefresh(): void {
    this.refreshTrigger$.next();
  }
}
