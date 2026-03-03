import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Book, PagedBooksResponse } from '../models';

export interface BookQueryParams {
  title?: string;
  author?: string;
  genre?: string;
  minPrice?: number;
  maxPrice?: number;
  page?: number;
  limit?: number;
}

@Injectable({ providedIn: 'root' })
export class BookService {
  constructor(private http: HttpClient) {}

  listBooks(params: BookQueryParams = {}): Observable<PagedBooksResponse> {
    let httpParams = new HttpParams();
    if (params.title) httpParams = httpParams.set('title', params.title);
    if (params.author) httpParams = httpParams.set('author', params.author);
    if (params.genre) httpParams = httpParams.set('genre', params.genre);
    if (params.minPrice != null) httpParams = httpParams.set('minPrice', params.minPrice);
    if (params.maxPrice != null) httpParams = httpParams.set('maxPrice', params.maxPrice);
    httpParams = httpParams.set('page', params.page ?? 1);
    httpParams = httpParams.set('limit', params.limit ?? 20);
    return this.http.get<PagedBooksResponse>('/api/books', { params: httpParams });
  }

  getBook(id: string): Observable<Book> {
    return this.http.get<Book>(`/api/books/${id}`);
  }

  createBook(book: Partial<Book>): Observable<Book> {
    return this.http.post<Book>('/api/books', book);
  }

  updateBook(id: string, book: Partial<Book>): Observable<Book> {
    return this.http.patch<Book>(`/api/books/${id}`, book);
  }

  deleteBook(id: string): Observable<void> {
    return this.http.delete<void>(`/api/books/${id}`);
  }
}
