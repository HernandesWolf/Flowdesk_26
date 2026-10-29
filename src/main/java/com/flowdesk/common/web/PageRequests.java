package com.flowdesk.common.web;

import com.flowdesk.common.exception.BadRequestException;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/** Builds safe page requests: capped size and a whitelist of sortable properties. */
public final class PageRequests {

    public static final int MAX_PAGE_SIZE = 100;

    private PageRequests() {}

    public static PageRequest of(int page, int size, String sortBy, String direction, Set<String> sortable) {
        if (page < 0) {
            throw new BadRequestException("page must be >= 0");
        }
        if (size < 1) {
            throw new BadRequestException("size must be >= 1");
        }
        if (!sortable.contains(sortBy)) {
            throw new BadRequestException("Unsupported sort property. Allowed: " + sortable);
        }
        Sort.Direction dir;
        try {
            dir = Sort.Direction.fromString(direction);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("direction must be 'asc' or 'desc'");
        }
        return PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), Sort.by(dir, sortBy).and(Sort.by(Sort.Direction.DESC, "id")));
    }
}
