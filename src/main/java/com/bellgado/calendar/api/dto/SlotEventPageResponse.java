package com.bellgado.calendar.api.dto;

import org.springframework.data.domain.Page;
import java.util.List;

public record SlotEventPageResponse(
        List<SlotEventResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static SlotEventPageResponse from(Page<SlotEventResponse> page) {
        return new SlotEventPageResponse(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
