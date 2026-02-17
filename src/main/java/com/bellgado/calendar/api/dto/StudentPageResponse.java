package com.bellgado.calendar.api.dto;

import org.springframework.data.domain.Page;
import java.util.List;

public record StudentPageResponse(
        List<StudentResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static StudentPageResponse from(Page<StudentResponse> page) {
        return new StudentPageResponse(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
