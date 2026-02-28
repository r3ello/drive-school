package com.bellgado.calendar.api.controller;

import com.bellgado.calendar.api.dto.*;
import com.bellgado.calendar.api.util.PaginationUtils;
import com.bellgado.calendar.application.service.SlotService;
import com.bellgado.calendar.application.service.StudentService;
import com.bellgado.calendar.domain.enums.SlotStatus;
import com.bellgado.calendar.notification.NotificationService;
import com.bellgado.calendar.notification.dto.NotificationResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/students")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService studentService;
    private final SlotService slotService;
    private final NotificationService notificationService;

    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<StudentResponse> createStudent(@Valid @RequestBody StudentCreateRequest request) {
        StudentResponse response = studentService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<StudentPageResponse> listStudents(
            @RequestParam(required = false) @Size(max = 200) String query,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size,
            @RequestParam(defaultValue = "fullName,asc") String sort
    ) {
        Pageable pageable = PaginationUtils.createPageable(page, size, sort);
        Page<StudentResponse> students = studentService.list(query, active, pageable);
        return ResponseEntity.ok(StudentPageResponse.from(students));
    }

    @GetMapping("/{studentId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN') or " +
                  "(hasRole('STUDENT') and @studentSecurityService.isOwnStudent(authentication, #studentId))")
    public ResponseEntity<StudentResponse> getStudent(@PathVariable UUID studentId) {
        return ResponseEntity.ok(studentService.getById(studentId));
    }

    @PutMapping("/{studentId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<StudentResponse> updateStudent(
            @PathVariable UUID studentId,
            @Valid @RequestBody StudentUpdateRequest request
    ) {
        return ResponseEntity.ok(studentService.update(studentId, request));
    }

    @PatchMapping("/{studentId}/deactivate")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<Void> deactivateStudent(@PathVariable UUID studentId) {
        studentService.deactivate(studentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{studentId}/slots")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN') or " +
                  "(hasRole('STUDENT') and @studentSecurityService.isOwnStudent(authentication, #studentId))")
    public ResponseEntity<SlotListResponse> getStudentSlotHistory(
            @PathVariable UUID studentId,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(required = false) List<SlotStatus> status
    ) {
        ZoneId appZone = ZoneId.of("Europe/Sofia");
        OffsetDateTime fromDateTime = from.atStartOfDay(appZone).toOffsetDateTime();
        OffsetDateTime toDateTime = to.plusDays(1).atStartOfDay(appZone).toOffsetDateTime();
        List<SlotResponse> slots = slotService.listByStudent(studentId, fromDateTime, toDateTime, status);
        return ResponseEntity.ok(new SlotListResponse(slots));
    }

    @PatchMapping("/{studentId}/notification-preferences")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<StudentResponse> updateNotificationPreferences(
            @PathVariable UUID studentId,
            @Valid @RequestBody NotificationPreferencesRequest request
    ) {
        return ResponseEntity.ok(studentService.updateNotificationPreferences(studentId, request));
    }

    @GetMapping("/{studentId}/notifications")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<NotificationPageResponse> getStudentNotifications(
            @PathVariable UUID studentId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<NotificationResponse> notifications = notificationService.listByStudent(studentId, pageable);
        return ResponseEntity.ok(NotificationPageResponse.from(notifications));
    }

    @PostMapping("/{studentId}/invite")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<Map<String, String>> inviteStudent(@PathVariable UUID studentId) {
        studentService.inviteStudent(studentId);
        return ResponseEntity.ok(Map.of("message", "Invitation sent."));
    }

}
