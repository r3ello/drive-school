package com.bellgado.calendar.api.controller;

import com.bellgado.calendar.api.dto.WaitlistCreateRequest;
import com.bellgado.calendar.api.dto.WaitlistPageResponse;
import com.bellgado.calendar.api.dto.WaitlistResponse;
import com.bellgado.calendar.api.util.PaginationUtils;
import com.bellgado.calendar.application.service.WaitlistService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/waitlist")
@RequiredArgsConstructor
public class WaitlistController {

    private final WaitlistService waitlistService;

    @PostMapping
    public ResponseEntity<WaitlistResponse> addToWaitlist(@Valid @RequestBody WaitlistCreateRequest request) {
        WaitlistResponse response = waitlistService.add(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<WaitlistPageResponse> listWaitlist(
            @RequestParam(defaultValue = "true") Boolean active,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size,
            @RequestParam(defaultValue = "createdAt,asc") String sort
    ) {
        Pageable pageable = PaginationUtils.createPageable(page, size, sort);
        Page<WaitlistResponse> items = waitlistService.list(active, pageable);
        return ResponseEntity.ok(WaitlistPageResponse.from(items));
    }

    @DeleteMapping("/{waitlistId}")
    public ResponseEntity<Void> removeFromWaitlist(@PathVariable UUID waitlistId) {
        waitlistService.remove(waitlistId);
        return ResponseEntity.noContent().build();
    }

}
