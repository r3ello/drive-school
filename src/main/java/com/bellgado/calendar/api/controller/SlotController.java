package com.bellgado.calendar.api.controller;

import com.bellgado.calendar.api.dto.*;
import com.bellgado.calendar.application.service.SlotService;
import com.bellgado.calendar.domain.enums.SlotStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/slots")
@RequiredArgsConstructor
public class SlotController {

    private final SlotService slotService;

    @PostMapping
    public ResponseEntity<SlotResponse> createSlot(@Valid @RequestBody SlotCreateRequest request) {
        SlotResponse response = slotService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<SlotListResponse> listSlots(
            @RequestParam OffsetDateTime from,
            @RequestParam OffsetDateTime to,
            @RequestParam(required = false) List<SlotStatus> status
    ) {
        List<SlotResponse> slots = slotService.list(from, to, status);
        return ResponseEntity.ok(new SlotListResponse(slots));
    }

    @GetMapping("/{slotId}")
    public ResponseEntity<SlotResponse> getSlot(@PathVariable UUID slotId) {
        return ResponseEntity.ok(slotService.getById(slotId));
    }

    @DeleteMapping("/{slotId}")
    public ResponseEntity<Void> deleteSlot(@PathVariable UUID slotId) {
        slotService.delete(slotId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/generate")
    public ResponseEntity<SlotGenerateResponse> generateSlots(@Valid @RequestBody SlotGenerateRequest request) {
        SlotGenerateResponse response = slotService.generate(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{slotId}/book")
    public ResponseEntity<SlotResponse> bookSlot(
            @PathVariable UUID slotId,
            @Valid @RequestBody SlotBookRequest request
    ) {
        return ResponseEntity.ok(slotService.book(slotId, request));
    }

    @PostMapping("/{slotId}/cancel")
    public ResponseEntity<SlotResponse> cancelSlot(
            @PathVariable UUID slotId,
            @Valid @RequestBody SlotCancelRequest request
    ) {
        return ResponseEntity.ok(slotService.cancel(slotId, request));
    }

    @PostMapping("/{slotId}/free")
    public ResponseEntity<SlotResponse> freeSlot(
            @PathVariable UUID slotId,
            @RequestBody(required = false) SlotFreeRequest request
    ) {
        return ResponseEntity.ok(slotService.free(slotId, request != null ? request : new SlotFreeRequest(null)));
    }

    @PostMapping("/{slotId}/replace")
    public ResponseEntity<SlotResponse> replaceSlotStudent(
            @PathVariable UUID slotId,
            @Valid @RequestBody SlotReplaceRequest request
    ) {
        return ResponseEntity.ok(slotService.replace(slotId, request));
    }

    @PostMapping("/{slotId}/reschedule")
    public ResponseEntity<RescheduleResponse> rescheduleSlot(
            @PathVariable UUID slotId,
            @Valid @RequestBody SlotRescheduleRequest request
    ) {
        return ResponseEntity.ok(slotService.reschedule(slotId, request));
    }
}
