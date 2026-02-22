package com.bellgado.calendar.application.service;

import com.bellgado.calendar.api.dto.*;
import com.bellgado.calendar.api.sse.SseEventType;
import com.bellgado.calendar.application.event.SlotChangedEvent;
import com.bellgado.calendar.application.exception.ConflictException;
import com.bellgado.calendar.application.exception.InvalidStateException;
import com.bellgado.calendar.application.exception.NotFoundException;
import com.bellgado.calendar.domain.entity.Slot;
import com.bellgado.calendar.domain.entity.SlotEvent;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SlotService {

    /** Canonical timezone for all business-rule comparisons. */
    private static final ZoneId APP_ZONE = ZoneId.of("Europe/Sofia");
    /** Inclusive start of allowed scheduling window (07:00). */
    private static final int ALLOWED_HOUR_FROM = 7;
    /** Exclusive end of allowed scheduling window (19:00 = 7 PM). */
    private static final int ALLOWED_HOUR_TO = 19;

    private final SlotRepository slotRepository;
    private final StudentService studentService;
    private final SlotEventService slotEventService;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;
    @Lazy
    private final WaitlistService waitlistService;

    @Transactional
    public SlotResponse create(SlotCreateRequest request) {
        validateNotInPast(request.startAt(), "create a slot");
        validateWorkingHours(request.startAt(), "Slot");

        if (slotRepository.existsByStartAt(request.startAt())) {
            throw new ConflictException("A slot already exists at this time: " + request.startAt());
        }

        Slot slot = new Slot(request.startAt());
        slot = slotRepository.save(slot);

        SlotEvent slotEvent = slotEventService.recordEventAndReturn(slot.getId(), EventType.CREATED);
        SlotResponse response = SlotResponse.from(slot);
        eventPublisher.publishEvent(new SlotChangedEvent(SseEventType.SLOT_CREATED, response, SlotEventResponse.from(slotEvent)));

        return response;
    }

    @Transactional
    public SlotGenerateResponse generate(SlotGenerateRequest request) {
        // Validate that all weekly rules stay within the allowed 07:00–19:00 window
        for (WeeklyRule rule : request.weeklyRules()) {
            LocalTime start = LocalTime.parse(rule.startTime());
            LocalTime end   = LocalTime.parse(rule.endTime());
            if (start.getHour() < ALLOWED_HOUR_FROM) {
                throw new ConflictException(
                        "Weekly rule startTime " + rule.startTime() + " is before 07:00 AM.");
            }
            if (end.getHour() > ALLOWED_HOUR_TO || (end.getHour() == ALLOWED_HOUR_TO && end.getMinute() > 0)) {
                throw new ConflictException(
                        "Weekly rule endTime " + rule.endTime() + " is after 07:00 PM.");
            }
        }

        final ZoneId zoneId = ZoneId.of(request.timezone());

        // Pre-fetch all existing start times in the date range with a single query
        final OffsetDateTime rangeFrom = request.from().atStartOfDay(zoneId).toOffsetDateTime();
        final OffsetDateTime rangeTo = request.to().plusDays(1).atStartOfDay(zoneId).toOffsetDateTime();
        final Set<OffsetDateTime> existingStartTimes = slotRepository.findStartAtBetween(rangeFrom, rangeTo);

        int createdCount = 0;
        int skippedCount = 0;
        final List<Slot> slotsToSave = new ArrayList<>();

        LocalDate current = request.from();
        while (!current.isAfter(request.to())) {
            for (WeeklyRule rule : request.weeklyRules()) {
                if (current.getDayOfWeek() == rule.dayOfWeek().toJavaDayOfWeek()) {
                    final LocalTime startTime = LocalTime.parse(rule.startTime());
                    final LocalTime endTime = LocalTime.parse(rule.endTime());

                    LocalTime slotStart = startTime;
                    while (slotStart.plusMinutes(60).compareTo(endTime) <= 0) {
                        final OffsetDateTime slotStartAt = ZonedDateTime.of(current, slotStart, zoneId).toOffsetDateTime();

                        if (!existingStartTimes.contains(slotStartAt)) {
                            slotsToSave.add(new Slot(slotStartAt));
                            existingStartTimes.add(slotStartAt); // prevent duplicates within the same request
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

        final List<Slot> savedSlots = slotRepository.saveAll(slotsToSave);
        for (Slot saved : savedSlots) {
            SlotEvent slotEvent = slotEventService.recordEventAndReturn(saved.getId(), EventType.GENERATED);
            eventPublisher.publishEvent(new SlotChangedEvent(
                    SseEventType.SLOT_GENERATED, SlotResponse.from(saved), SlotEventResponse.from(slotEvent)));
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

        validateNotInPast(slot.getStartAt(), "book a slot");

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

        SlotEvent slotEvent = slotEventService.recordEventAndReturn(slot.getId(), EventType.BOOKED, null, student.getId());

        // Automatically remove the student from the waitlist once a slot is assigned
        waitlistService.removeActiveByStudentId(student.getId());

        SlotResponse response = SlotResponse.from(slot);
        eventPublisher.publishEvent(new SlotChangedEvent(SseEventType.SLOT_BOOKED, response, SlotEventResponse.from(slotEvent)));

        return response;
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
        SlotEvent slotEvent = slotEventService.recordEventWithStudentsAndReturn(slot.getId(), EventType.CANCELLED, studentId, null, meta);

        SlotResponse response = SlotResponse.from(slot);
        eventPublisher.publishEvent(new SlotChangedEvent(SseEventType.SLOT_CANCELLED, response, SlotEventResponse.from(slotEvent)));

        return response;
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

        SlotEvent slotEvent = slotEventService.recordEventAndReturn(slot.getId(), EventType.FREED, previousStudentId, null);

        SlotResponse response = SlotResponse.from(slot);
        eventPublisher.publishEvent(new SlotChangedEvent(SseEventType.SLOT_FREED, response, SlotEventResponse.from(slotEvent)));

        return response;
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
        SlotEvent slotEvent = slotEventService.recordEventWithStudentsAndReturn(slot.getId(), EventType.REPLACED, oldStudentId, newStudent.getId(), meta);

        SlotResponse response = SlotResponse.from(slot);
        eventPublisher.publishEvent(new SlotChangedEvent(SseEventType.SLOT_REPLACED, response, SlotEventResponse.from(slotEvent)));

        return response;
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

        validateNotInPast(targetSlot.getStartAt(), "reschedule to a slot");

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
        SlotEvent originEvent = slotEventService.recordEventWithStudentsAndReturn(
                originSlot.getId(), EventType.RESCHEDULED, student.getId(), null, originMeta);

        Map<String, Object> targetMeta = new HashMap<>();
        targetMeta.put("originSlotId", originSlotId.toString());
        if (request.reason() != null) {
            targetMeta.put("reason", request.reason());
        }
        SlotEvent targetEvent = slotEventService.recordEventWithStudentsAndReturn(
                targetSlot.getId(), EventType.RESCHEDULED, null, student.getId(), targetMeta);

        Slot freshOrigin = slotRepository.findByIdWithStudent(originSlotId).orElse(originSlot);
        Slot freshTarget = slotRepository.findByIdWithStudent(request.targetSlotId()).orElse(targetSlot);

        eventPublisher.publishEvent(new SlotChangedEvent(
                SseEventType.SLOT_RESCHEDULED, SlotResponse.from(freshOrigin), SlotEventResponse.from(originEvent)));
        eventPublisher.publishEvent(new SlotChangedEvent(
                SseEventType.SLOT_RESCHEDULED, SlotResponse.from(freshTarget), SlotEventResponse.from(targetEvent)));

        return new RescheduleResponse(SlotResponse.from(freshOrigin), SlotResponse.from(freshTarget));
    }

    @Transactional
    public void blockSlotsInRange(UUID blockId, OffsetDateTime from, OffsetDateTime to) {
        final String blockIdStr = blockId.toString();

        // Block existing non-BOOKED slots in bulk
        final List<Slot> existingSlots = slotRepository.findAll(SlotSpecifications.inRangeExcludingBooked(from, to));
        for (Slot slot : existingSlots) {
            slot.setStatus(SlotStatus.BLOCKED);
            slot.setBlockId(blockId);
        }
        slotRepository.saveAll(existingSlots);
        for (Slot slot : existingSlots) {
            SlotEvent slotEvent = slotEventService.recordEventAndReturn(slot.getId(), EventType.BLOCKED, Map.of("blockId", blockIdStr));
            eventPublisher.publishEvent(new SlotChangedEvent(
                    SseEventType.SLOT_BLOCKED, SlotResponse.from(slot), SlotEventResponse.from(slotEvent)));
        }

        // Pre-fetch existing start times to avoid N+1 existence checks
        final Set<OffsetDateTime> existingStartTimes = slotRepository.findStartAtBetween(from, to);

        // Create new BLOCKED slots for each hour in the range that has no slot yet
        final List<Slot> newSlots = new ArrayList<>();
        OffsetDateTime currentStart = from;
        while (currentStart.isBefore(to)) {
            if (!existingStartTimes.contains(currentStart)) {
                final Slot newSlot = new Slot(currentStart);
                newSlot.setStatus(SlotStatus.BLOCKED);
                newSlot.setBlockId(blockId);
                newSlots.add(newSlot);
            }
            currentStart = currentStart.plusHours(1);
        }
        final List<Slot> savedNewSlots = slotRepository.saveAll(newSlots);
        for (Slot saved : savedNewSlots) {
            SlotEvent slotEvent = slotEventService.recordEventAndReturn(saved.getId(), EventType.BLOCKED, Map.of("blockId", blockIdStr));
            eventPublisher.publishEvent(new SlotChangedEvent(
                    SseEventType.SLOT_BLOCKED, SlotResponse.from(saved), SlotEventResponse.from(slotEvent)));
        }
    }

    @Transactional
    public void unblockSlotsByBlockId(UUID blockId) {
        final String blockIdStr = blockId.toString();
        final List<Slot> slotsToUnblock = slotRepository.findByBlockId(blockId).stream()
                .filter(s -> s.getStatus() == SlotStatus.BLOCKED)
                .toList();
        for (Slot slot : slotsToUnblock) {
            slot.setStatus(SlotStatus.FREE);
            slot.setBlockId(null);
        }
        slotRepository.saveAll(slotsToUnblock);
        for (Slot slot : slotsToUnblock) {
            SlotEvent slotEvent = slotEventService.recordEventAndReturn(slot.getId(), EventType.UNBLOCKED, Map.of("blockId", blockIdStr));
            eventPublisher.publishEvent(new SlotChangedEvent(
                    SseEventType.SLOT_UNBLOCKED, SlotResponse.from(slot), SlotEventResponse.from(slotEvent)));
        }
    }

    /**
     * Blocks a single slot directly (without creating a Block entity).
     * The slot must be FREE or CANCELLED — BOOKED slots cannot be blocked.
     */
    @Transactional
    public SlotResponse blockSlot(UUID slotId) {
        Slot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new NotFoundException("Slot not found: " + slotId));

        if (slot.getStatus() == SlotStatus.BOOKED) {
            throw new InvalidStateException("Cannot block a BOOKED slot. Cancel it first.");
        }
        if (slot.getStatus() == SlotStatus.BLOCKED) {
            throw new InvalidStateException("Slot is already BLOCKED.");
        }

        slot.setStatus(SlotStatus.BLOCKED);
        slot = slotRepository.save(slot);

        SlotEvent slotEvent = slotEventService.recordEventAndReturn(slot.getId(), EventType.BLOCKED, Collections.emptyMap());
        SlotResponse response = SlotResponse.from(slot);
        eventPublisher.publishEvent(new SlotChangedEvent(SseEventType.SLOT_BLOCKED, response, SlotEventResponse.from(slotEvent)));

        return response;
    }

    /**
     * Unblocks a single BLOCKED slot, setting it back to FREE.
     * Does not affect other slots that may share the same blockId.
     */
    @Transactional
    public SlotResponse unblockSlot(UUID slotId) {
        Slot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new NotFoundException("Slot not found: " + slotId));

        if (slot.getStatus() != SlotStatus.BLOCKED) {
            throw new InvalidStateException("Slot must be BLOCKED to unblock. Current status: " + slot.getStatus());
        }

        UUID blockId = slot.getBlockId();
        slot.setStatus(SlotStatus.FREE);
        slot.setBlockId(null);
        slot = slotRepository.save(slot);

        Map<String, Object> meta = blockId != null ? Map.of("blockId", blockId.toString()) : Collections.emptyMap();
        SlotEvent slotEvent = slotEventService.recordEventAndReturn(slot.getId(), EventType.UNBLOCKED, meta);
        SlotResponse response = SlotResponse.from(slot);
        eventPublisher.publishEvent(new SlotChangedEvent(SseEventType.SLOT_UNBLOCKED, response, SlotEventResponse.from(slotEvent)));

        return response;
    }

    // -------------------------------------------------------------------------
    // Private validation helpers
    // -------------------------------------------------------------------------

    /**
     * Throws {@link ConflictException} if {@code time} is strictly in the past
     * relative to the current moment in the application timezone (Europe/Sofia).
     */
    private void validateNotInPast(OffsetDateTime time, String action) {
        OffsetDateTime now = OffsetDateTime.now(APP_ZONE);
        if (time.toInstant().isBefore(now.toInstant())) {
            throw new ConflictException(
                    "Cannot " + action + " in the past. Requested: " + time + ", Now: " + now);
        }
    }

    /**
     * Throws {@link ConflictException} if the hour component of {@code time}
     * (in Europe/Sofia) is outside the allowed window [07:00, 19:00).
     */
    private void validateWorkingHours(OffsetDateTime time, String label) {
        int hour = time.atZoneSameInstant(APP_ZONE).getHour();
        if (hour < ALLOWED_HOUR_FROM || hour >= ALLOWED_HOUR_TO) {
            throw new ConflictException(
                    label + " time must be between 07:00 and 19:00 (Europe/Sofia). Got hour: " + hour);
        }
    }
}
