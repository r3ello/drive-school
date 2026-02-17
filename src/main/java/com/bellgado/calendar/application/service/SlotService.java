package com.bellgado.calendar.application.service;

import com.bellgado.calendar.api.dto.*;
import com.bellgado.calendar.application.exception.ConflictException;
import com.bellgado.calendar.application.exception.InvalidStateException;
import com.bellgado.calendar.application.exception.NotFoundException;
import com.bellgado.calendar.domain.entity.Slot;
import com.bellgado.calendar.domain.entity.Student;
import com.bellgado.calendar.domain.enums.EventType;
import com.bellgado.calendar.domain.enums.NotificationType;
import com.bellgado.calendar.domain.enums.SlotStatus;
import com.bellgado.calendar.infrastructure.repository.SlotRepository;
import com.bellgado.calendar.infrastructure.specification.SlotSpecifications;
import com.bellgado.calendar.notification.NotificationService;
import com.bellgado.calendar.notification.dto.NotificationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SlotService {

    private final SlotRepository slotRepository;
    private final StudentService studentService;
    private final SlotEventService slotEventService;
    private final NotificationService notificationService;

    @Transactional
    public SlotResponse create(SlotCreateRequest request) {
        if (slotRepository.existsByStartAt(request.startAt())) {
            throw new ConflictException("A slot already exists at this time: " + request.startAt());
        }

        Slot slot = new Slot(request.startAt());
        slot = slotRepository.save(slot);

        slotEventService.recordEvent(slot.getId(), EventType.CREATED);

        return SlotResponse.from(slot);
    }

    @Transactional
    public SlotGenerateResponse generate(SlotGenerateRequest request) {
        ZoneId zoneId = ZoneId.of(request.timezone());
        int createdCount = 0;
        int skippedCount = 0;

        LocalDate current = request.from();
        while (!current.isAfter(request.to())) {
            for (WeeklyRule rule : request.weeklyRules()) {
                if (current.getDayOfWeek() == rule.dayOfWeek().toJavaDayOfWeek()) {
                    LocalTime startTime = LocalTime.parse(rule.startTime());
                    LocalTime endTime = LocalTime.parse(rule.endTime());

                    LocalTime slotStart = startTime;
                    while (slotStart.plusMinutes(60).compareTo(endTime) <= 0) {
                        ZonedDateTime zonedSlotStart = ZonedDateTime.of(current, slotStart, zoneId);
                        OffsetDateTime slotStartAt = zonedSlotStart.toOffsetDateTime();

                        if (!slotRepository.existsByStartAt(slotStartAt)) {
                            Slot slot = new Slot(slotStartAt);
                            Slot persitedSlot = slotRepository.save(slot);
                            slotEventService.recordEvent(persitedSlot.getId(), EventType.GENERATED);
                            createdCount++;
                        } else {
                            skippedCount++;
                        }

                        slotStart = slotStart.plusMinutes(60);
                    }
                }
            }
            current = current.plusDays(1);
        }

        return new SlotGenerateResponse(createdCount, skippedCount);
    }

    @Transactional(readOnly = true)
    public List<SlotResponse> list(OffsetDateTime from, OffsetDateTime to, Collection<SlotStatus> statuses) {
        log.info(" List Slot from {}  to {}", from, to);
        return slotRepository.findAll(
                SlotSpecifications.inDateRangeWithStatuses(from, to, statuses),
                Sort.by(Sort.Direction.ASC, "startAt")
        ).stream().map(SlotResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<SlotResponse> listByStudent(UUID studentId, OffsetDateTime from, OffsetDateTime to, Collection<SlotStatus> statuses) {
        studentService.getEntityById(studentId);
        return slotRepository.findAll(
                SlotSpecifications.forStudentInDateRange(studentId, from, to, statuses),
                Sort.by(Sort.Direction.ASC, "startAt")
        ).stream().map(SlotResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public SlotResponse getById(UUID id) {
        Slot slot = slotRepository.findByIdWithStudent(id)
                .orElseThrow(() -> new NotFoundException("Slot not found: " + id));
        return SlotResponse.from(slot);
    }

    @Transactional
    public void delete(UUID id) {
        Slot slot = slotRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Slot not found: " + id));

        if (slot.getStatus() == SlotStatus.BOOKED) {
            throw new ConflictException("Cannot delete a BOOKED slot");
        }

        slotRepository.delete(slot);
    }

    @Transactional
    public SlotResponse book(UUID slotId, SlotBookRequest request) {
        Slot slot = slotRepository.findByIdWithStudent(slotId)
                .orElseThrow(() -> new NotFoundException("Slot not found: " + slotId));

        if (slot.getStatus() != SlotStatus.FREE) {
            throw new InvalidStateException("Slot must be FREE to book. Current status: " + slot.getStatus());
        }

        Student student = studentService.getEntityById(request.studentId());

        slot.setStatus(SlotStatus.BOOKED);
        slot.setStudent(student);
        if (request.notes() != null) {
            slot.setNotes(request.notes());
        }

        slot = slotRepository.save(slot);

        slotEventService.recordEvent(slot.getId(), EventType.BOOKED, null, student.getId());
        return SlotResponse.from(slot);
    }

    @Transactional
    public SlotResponse cancel(UUID slotId, SlotCancelRequest request) {
        Slot slot = slotRepository.findByIdWithStudent(slotId)
                .orElseThrow(() -> new NotFoundException("Slot not found: " + slotId));

        if (slot.getStatus() != SlotStatus.BOOKED) {
            throw new InvalidStateException("Slot must be BOOKED to cancel. Current status: " + slot.getStatus());
        }

        UUID studentId = slot.getStudent() != null ? slot.getStudent().getId() : null;

        slot.setStatus(SlotStatus.CANCELLED);
        slot = slotRepository.save(slot);

        Map<String, Object> meta = new HashMap<>();
        meta.put("cancelledBy", request.cancelledBy().name());
        if (request.reason() != null) {
            meta.put("reason", request.reason());
        }
        slotEventService.recordEventWithStudents(slot.getId(), EventType.CANCELLED, studentId, null, meta);

        return SlotResponse.from(slot);
    }

    @Transactional
    public SlotResponse free(UUID slotId, SlotFreeRequest request) {
        Slot slot = slotRepository.findByIdWithStudent(slotId)
                .orElseThrow(() -> new NotFoundException("Slot not found: " + slotId));

        if (slot.getStatus() != SlotStatus.CANCELLED && slot.getStatus() != SlotStatus.BOOKED) {
            throw new InvalidStateException("Slot must be CANCELLED or BOOKED to free. Current status: " + slot.getStatus());
        }

        UUID previousStudentId = slot.getStudent() != null ? slot.getStudent().getId() : null;

        slot.setStatus(SlotStatus.FREE);
        slot.setStudent(null);
        if (request != null && request.notes() != null) {
            slot.setNotes(request.notes());
        }

        slot = slotRepository.save(slot);

        slotEventService.recordEvent(slot.getId(), EventType.FREED, previousStudentId, null);

        return SlotResponse.from(slot);
    }

    @Transactional
    public SlotResponse replace(UUID slotId, SlotReplaceRequest request) {
        Slot slot = slotRepository.findByIdWithStudent(slotId)
                .orElseThrow(() -> new NotFoundException("Slot not found: " + slotId));

        if (slot.getStatus() != SlotStatus.BOOKED && slot.getStatus() != SlotStatus.CANCELLED) {
            throw new InvalidStateException("Slot must be BOOKED or CANCELLED to replace. Current status: " + slot.getStatus());
        }

        UUID oldStudentId = slot.getStudent() != null ? slot.getStudent().getId() : null;
        Student newStudent = studentService.getEntityById(request.newStudentId());

        slot.setStudent(newStudent);
        if (slot.getStatus() == SlotStatus.CANCELLED) {
            slot.setStatus(SlotStatus.BOOKED);
        }

        slot = slotRepository.save(slot);

        Map<String, Object> meta = null;
        if (request.reason() != null) {
            meta = Map.of("reason", request.reason());
        }
        slotEventService.recordEventWithStudents(slot.getId(), EventType.REPLACED, oldStudentId, newStudent.getId(), meta);

        return SlotResponse.from(slot);
    }

    @Transactional
    public RescheduleResponse reschedule(UUID originSlotId, SlotRescheduleRequest request) {
        Slot originSlot = slotRepository.findByIdWithStudent(originSlotId)
                .orElseThrow(() -> new NotFoundException("Origin slot not found: " + originSlotId));

        if (originSlot.getStatus() != SlotStatus.BOOKED) {
            throw new InvalidStateException("Origin slot must be BOOKED to reschedule. Current status: " + originSlot.getStatus());
        }

        Slot targetSlot = slotRepository.findByIdWithStudent(request.targetSlotId())
                .orElseThrow(() -> new NotFoundException("Target slot not found: " + request.targetSlotId()));

        if (targetSlot.getStatus() != SlotStatus.FREE) {
            throw new InvalidStateException("Target slot must be FREE. Current status: " + targetSlot.getStatus());
        }

        Student student = originSlot.getStudent();
        String notes = originSlot.getNotes();

        originSlot.setStatus(SlotStatus.FREE);
        originSlot.setStudent(null);
        originSlot.setNotes(null);
        originSlot = slotRepository.save(originSlot);

        targetSlot.setStatus(SlotStatus.BOOKED);
        targetSlot.setStudent(student);
        targetSlot.setNotes(notes);
        targetSlot = slotRepository.save(targetSlot);

        Map<String, Object> originMeta = new HashMap<>();
        originMeta.put("targetSlotId", request.targetSlotId().toString());
        if (request.reason() != null) {
            originMeta.put("reason", request.reason());
        }
        slotEventService.recordEventWithStudents(originSlot.getId(), EventType.RESCHEDULED, student.getId(), null, originMeta);

        Map<String, Object> targetMeta = new HashMap<>();
        targetMeta.put("originSlotId", originSlotId.toString());
        if (request.reason() != null) {
            targetMeta.put("reason", request.reason());
        }
        slotEventService.recordEventWithStudents(targetSlot.getId(), EventType.RESCHEDULED, null, student.getId(), targetMeta);

        Slot freshOrigin = slotRepository.findByIdWithStudent(originSlotId).orElse(originSlot);
        Slot freshTarget = slotRepository.findByIdWithStudent(request.targetSlotId()).orElse(targetSlot);

        return new RescheduleResponse(SlotResponse.from(freshOrigin), SlotResponse.from(freshTarget));
    }

    @Transactional
    public void blockSlotsInRange(UUID blockId, OffsetDateTime from, OffsetDateTime to) {
        List<Slot> slots = slotRepository.findAll(SlotSpecifications.inRangeExcludingBooked(from, to));

        for (Slot slot : slots) {
            if (slot.getStatus() != SlotStatus.BOOKED) {
                slot.setStatus(SlotStatus.BLOCKED);
                slot.setBlockId(blockId);
                slotRepository.save(slot);
                slotEventService.recordEvent(slot.getId(), EventType.BLOCKED, Map.of("blockId", blockId.toString()));
            }
        }

        OffsetDateTime currentStart = from;
        while (currentStart.isBefore(to)) {
            OffsetDateTime slotStart = currentStart;
            if (!slotRepository.existsByStartAt(slotStart)) {
                Slot newSlot = new Slot(slotStart);
                newSlot.setStatus(SlotStatus.BLOCKED);
                newSlot.setBlockId(blockId);
                slotRepository.save(newSlot);
                slotEventService.recordEvent(newSlot.getId(), EventType.BLOCKED, Map.of("blockId", blockId.toString()));
            }
            currentStart = currentStart.plusHours(1);
        }
    }

    @Transactional
    public void unblockSlotsByBlockId(UUID blockId) {
        List<Slot> slots = slotRepository.findByBlockId(blockId);
        for (Slot slot : slots) {
            if (slot.getStatus() == SlotStatus.BLOCKED) {
                slot.setStatus(SlotStatus.FREE);
                slot.setBlockId(null);
                slotRepository.save(slot);
                slotEventService.recordEvent(slot.getId(), EventType.UNBLOCKED, Map.of("blockId", blockId.toString()));
            }
        }
    }
}
