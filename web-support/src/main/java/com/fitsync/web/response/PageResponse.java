package com.fitsync.web.response;

import java.util.List;

public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {

    public static <T> PageResponse<T> of(List<T> content,
                                         int page,
                                         int size,
                                         long totalElements,
                                         int totalPages,
                                         boolean first,
                                         boolean last) {
        return new PageResponse<>(content, page, size, totalElements, totalPages, first, last);
    }
}
