package com.bellgado.calendar.api.controller;

import com.bellgado.calendar.api.dto.SlotEventListResponse;
import com.bellgado.calendar.api.dto.SlotEventPageResponse;
import com.bellgado.calendar.api.dto.SlotEventResponse;
import com.bellgado.calendar.api.util.PaginationUtils;
import com.bellgado.calendar.application.service.SlotEventService;
import com.bellgado.calendar.application.service.SlotService;
import com.bellgado.calendar.domain.enums.EventType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
public class EventController {

    private final SlotEventService slotEventService;
    private final SlotService slotService;

    @GetMapping("/slots/{slotId}/events")
    public ResponseEntity<SlotEventListResponse> listSlotEvents(@PathVariable UUID slotId) {
        slotService.getById(slotId);
        List<SlotEventResponse> events = slotEventService.getBySlotId(slotId);
        return ResponseEntity.ok(new SlotEventListResponse(events));
    }

    @GetMapping("/events")
    public ResponseEntity<SlotEventPageResponse> listEvents(
            @RequestParam OffsetDateTime from,
            @RequestParam OffsetDateTime to,
            @RequestParam(required = false) List<EventType> type,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size,
            @RequestParam(defaultValue = "at,asc") String sort
    ) {
        Pageable pageable = PaginationUtils.createPageable(page, size, sort);
        Page<SlotEventResponse> events = slotEventService.list(from, to, type, pageable);
        return ResponseEntity.ok(SlotEventPageResponse.from(events));
    }

}
