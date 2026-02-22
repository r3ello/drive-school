package com.bellgado.calendar.api.dto;

import com.bellgado.calendar.notification.dto.NotificationResponse;
import org.springframework.data.domain.Page;

import java.util.List;

public record NotificationPageResponse(
        List<NotificationResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static NotificationPageResponse from(Page<NotificationResponse> page) {
        return new NotificationPageResponse(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
