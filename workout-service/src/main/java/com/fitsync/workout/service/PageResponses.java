package com.fitsync.workout.service;

import com.fitsync.web.response.PageResponse;
import org.springframework.data.domain.Page;

/** Turns a Spring Data page into the shared paged response the workout endpoints return. */
final class PageResponses {

    private PageResponses() {
    }

    static <T> PageResponse<T> of(Page<T> page) {
        return PageResponse.of(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }
}
