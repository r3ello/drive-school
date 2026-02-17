package com.bellgado.calendar.application.service;

import com.bellgado.calendar.api.dto.WaitlistCreateRequest;
import com.bellgado.calendar.api.dto.WaitlistResponse;
import com.bellgado.calendar.application.exception.NotFoundException;
import com.bellgado.calendar.domain.entity.Student;
import com.bellgado.calendar.domain.entity.WaitlistItem;
import com.bellgado.calendar.infrastructure.repository.WaitlistRepository;
import com.bellgado.calendar.infrastructure.specification.WaitlistSpecifications;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WaitlistService {

    private final WaitlistRepository waitlistRepository;
    private final StudentService studentService;
    private final ObjectMapper objectMapper;

    @Transactional
    public WaitlistResponse add(WaitlistCreateRequest request) {
        Student student = studentService.getEntityById(request.studentId());

        String preferredDays = null;
        if (request.preferredDays() != null && !request.preferredDays().isEmpty()) {
            preferredDays = request.preferredDays().stream()
                    .map(Enum::name)
                    .collect(Collectors.joining(","));
        }

        String preferredTimeRanges = null;
        if (request.preferredTimeRanges() != null && !request.preferredTimeRanges().isEmpty()) {
            try {
                preferredTimeRanges = objectMapper.writeValueAsString(request.preferredTimeRanges());
            } catch (JsonProcessingException e) {
                preferredTimeRanges = null;
            }
        }

        WaitlistItem item = new WaitlistItem(
                student,
                preferredDays,
                preferredTimeRanges,
                request.notes(),
                request.priority()
        );

        item = waitlistRepository.save(item);
        return WaitlistResponse.from(item);
    }

    @Transactional(readOnly = true)
    public Page<WaitlistResponse> list(Boolean active, Pageable pageable) {
        return waitlistRepository.findAll(
                WaitlistSpecifications.activeWithStudent(active),
                pageable
        ).map(WaitlistResponse::from);
    }

    @Transactional
    public void remove(UUID id) {
        WaitlistItem item = waitlistRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Waitlist item not found: " + id));
        item.setActive(false);
        waitlistRepository.save(item);
    }
}
