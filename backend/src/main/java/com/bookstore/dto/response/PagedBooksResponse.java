package com.bookstore.dto.response;

import com.bookstore.model.Book;
import java.util.List;

public class PagedBooksResponse {
    private List<Book> data;
    private long total;
    private int page;
    private int limit;

    public PagedBooksResponse(List<Book> data, long total, int page, int limit) {
        this.data = data;
        this.total = total;
        this.page = page;
        this.limit = limit;
    }

    public List<Book> getData() { return data; }
    public long getTotal() { return total; }
    public int getPage() { return page; }
    public int getLimit() { return limit; }
}
