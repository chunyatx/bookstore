import { Injectable, computed, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { User } from '../models';

interface AuthResponse {
  token: string;
  user: User;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private _user = signal<User | null>(null);
  readonly currentUser = this._user.asReadonly();
  readonly isLoggedIn = computed(() => this._user() !== null);

  constructor(private http: HttpClient) {
    // Rehydrate from localStorage on init
    const stored = localStorage.getItem('bs_user');
    if (stored) {
      try { this._user.set(JSON.parse(stored)); } catch { /**/ }
    }
  }

  register(email: string, password: string, name: string): Observable<User> {
    return this.http.post<User>('/api/auth/register', { email, password, name });
  }

  login(email: string, password: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>('/api/auth/login', { email, password }).pipe(
      tap(res => {
        localStorage.setItem('bs_token', res.token);
        localStorage.setItem('bs_user', JSON.stringify(res.user));
        this._user.set(res.user);
      })
    );
  }

  logout(): void {
    localStorage.removeItem('bs_token');
    localStorage.removeItem('bs_user');
    this._user.set(null);
  }

  getMe(): Observable<User> {
    return this.http.get<User>('/api/auth/me').pipe(
      tap(user => {
        localStorage.setItem('bs_user', JSON.stringify(user));
        this._user.set(user);
      })
    );
  }
}
