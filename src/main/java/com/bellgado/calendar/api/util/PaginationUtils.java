package com.bellgado.calendar.api.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class PaginationUtils {

    private PaginationUtils() {}

    /**
     * Builds a {@link Pageable} from flat controller parameters.
     * The {@code sort} parameter format is {@code "field,direction"}, e.g. {@code "createdAt,desc"}.
     * Direction defaults to ascending when omitted.
     */
    public static Pageable createPageable(int page, int size, String sort) {
        final String[] parts = sort.split(",");
        final String property = parts[0];
        final Sort.Direction direction = parts.length > 1 && parts[1].equalsIgnoreCase("desc")
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        return PageRequest.of(page, size, Sort.by(direction, property));
    }
}
