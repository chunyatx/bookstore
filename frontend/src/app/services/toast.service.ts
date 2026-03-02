import { Injectable, signal } from '@angular/core';

export type ToastType = 'success' | 'error' | 'info';

export interface Toast {
  text: string;
  type: ToastType;
}

@Injectable({ providedIn: 'root' })
export class ToastService {
  readonly toast = signal<Toast | null>(null);

  show(text: string, type: ToastType = 'info'): void {
    this.toast.set({ text, type });
    setTimeout(() => this.toast.set(null), 2800);
  }
}
