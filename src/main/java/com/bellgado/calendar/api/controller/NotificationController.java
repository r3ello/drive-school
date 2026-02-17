package com.bellgado.calendar.api.controller;

import com.bellgado.calendar.domain.enums.NotificationStatus;
import com.bellgado.calendar.notification.NotificationService;
import com.bellgado.calendar.notification.dto.NotificationCreateRequest;
import com.bellgado.calendar.notification.dto.NotificationResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for notification management.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * Creates a new notification.
     */
    @PostMapping
    public ResponseEntity<NotificationResponse> createNotification(
            @Valid @RequestBody NotificationCreateRequest request) {
        NotificationResponse response = notificationService.create(request);
        if (response == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Lists notifications with optional status filter.
     */
    @GetMapping
    public ResponseEntity<NotificationPageResponse> listNotifications(
            @RequestParam(required = false) List<NotificationStatus> status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        Pageable pageable = createPageable(page, size, sort);
        Page<NotificationResponse> notifications = notificationService.list(status, pageable);
        return ResponseEntity.ok(NotificationPageResponse.from(notifications));
    }

    /**
     * Gets a notification by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<NotificationResponse> getNotification(@PathVariable UUID id) {
        return ResponseEntity.ok(notificationService.getById(id));
    }

    /**
     * Manually processes/dispatches a notification.
     */
    @PostMapping("/{id}/process")
    public ResponseEntity<NotificationResponse> processNotification(@PathVariable UUID id) {
        return ResponseEntity.ok(notificationService.process(id));
    }

    /**
     * Gets notification statistics.
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getStatistics() {
        return ResponseEntity.ok(notificationService.getStatistics());
    }

    private Pageable createPageable(int page, int size, String sort) {
        String[] parts = sort.split(",");
        String property = parts[0];
        Sort.Direction direction = parts.length > 1 && parts[1].equalsIgnoreCase("desc")
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        return PageRequest.of(page, size, Sort.by(direction, property));
    }

    /**
     * Page response for notifications.
     */
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
}
