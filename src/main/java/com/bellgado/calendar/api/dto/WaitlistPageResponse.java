package com.bellgado.calendar.api.dto;

import org.springframework.data.domain.Page;
import java.util.List;

public record WaitlistPageResponse(
        List<WaitlistResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static WaitlistPageResponse from(Page<WaitlistResponse> page) {
        return new WaitlistPageResponse(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
