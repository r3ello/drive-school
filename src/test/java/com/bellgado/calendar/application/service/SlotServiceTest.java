package com.bellgado.calendar.application.service;

import com.bellgado.calendar.api.dto.*;
import com.bellgado.calendar.application.exception.ConflictException;
import com.bellgado.calendar.application.exception.InvalidStateException;
import com.bellgado.calendar.application.exception.NotFoundException;
import com.bellgado.calendar.domain.entity.Slot;
import com.bellgado.calendar.domain.entity.SlotEvent;
import com.bellgado.calendar.domain.entity.Student;
import com.bellgado.calendar.domain.enums.CancelledBy;
import com.bellgado.calendar.domain.enums.EventType;
import com.bellgado.calendar.domain.enums.SlotStatus;
import com.bellgado.calendar.infrastructure.repository.SlotRepository;
import com.bellgado.calendar.notification.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SlotServiceTest {

    /** A slot start time that is always in the future and within the 07:00–19:00 Sofia window. */
    private static final OffsetDateTime FUTURE_WORKING_HOURS_SLOT =
            ZonedDateTime.now(ZoneId.of("Europe/Sofia"))
                    .plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0)
                    .toOffsetDateTime();

    @Mock
    private SlotRepository slotRepository;

    @Mock
    private StudentService studentService;

    @Mock
    private SlotEventService slotEventService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private WaitlistService waitlistService;

    private SlotService slotService;

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void setUp() {
        slotService = new SlotService(slotRepository, studentService, slotEventService,
                notificationService, eventPublisher, waitlistService);

        // Stub recordEventAndReturn* variants to return a non-null SlotEvent
        // so that SlotEventResponse.from(slotEvent) does not NPE in service methods.
        lenient().when(slotEventService.recordEventAndReturn(any(UUID.class), any(EventType.class)))
                .thenAnswer(i -> new SlotEvent(i.getArgument(0), i.getArgument(1)));
        lenient().when(slotEventService.recordEventAndReturn(
                        any(UUID.class), any(EventType.class), nullable(UUID.class), nullable(UUID.class)))
                .thenAnswer(i -> new SlotEvent(i.getArgument(0), i.getArgument(1)));
        lenient().when(slotEventService.recordEventAndReturn(
                        any(UUID.class), any(EventType.class), any(Map.class)))
                .thenAnswer(i -> new SlotEvent(i.getArgument(0), i.getArgument(1)));
        lenient().when(slotEventService.recordEventWithStudentsAndReturn(
                        any(UUID.class), any(EventType.class), nullable(UUID.class), nullable(UUID.class), any()))
                .thenAnswer(i -> new SlotEvent(i.getArgument(0), i.getArgument(1)));
    }

    @Test
    void book_shouldBookFreeSlot() {
        UUID slotId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        OffsetDateTime startAt = OffsetDateTime.now().plusDays(1);

        Slot slot = new Slot(startAt);
        slot.setId(slotId);
        slot.setStatus(SlotStatus.FREE);

        Student student = new Student("John Doe", null, null, null);
        student.setId(studentId);

        when(slotRepository.findByIdWithStudent(slotId)).thenReturn(Optional.of(slot));
        when(studentService.getEntityById(studentId)).thenReturn(student);
        when(slotRepository.save(any(Slot.class))).thenAnswer(i -> i.getArgument(0));

        SlotBookRequest request = new SlotBookRequest(studentId, "Test notes");
        SlotResponse response = slotService.book(slotId, request);

        assertEquals(SlotStatus.BOOKED, response.status());
        assertEquals(studentId, response.student().id());
        verify(slotEventService).recordEventAndReturn(eq(slotId), any(EventType.class), isNull(), eq(studentId));
    }

    @Test
    void book_shouldThrowWhenSlotNotFree() {
        UUID slotId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        OffsetDateTime startAt = OffsetDateTime.now().plusDays(1);

        Slot slot = new Slot(startAt);
        slot.setId(slotId);
        slot.setStatus(SlotStatus.BOOKED);

        when(slotRepository.findByIdWithStudent(slotId)).thenReturn(Optional.of(slot));

        SlotBookRequest request = new SlotBookRequest(studentId, null);

        assertThrows(InvalidStateException.class, () -> slotService.book(slotId, request));
        verify(slotRepository, never()).save(any());
    }

    @Test
    void book_shouldThrowWhenSlotNotFound() {
        UUID slotId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();

        when(slotRepository.findByIdWithStudent(slotId)).thenReturn(Optional.empty());

        SlotBookRequest request = new SlotBookRequest(studentId, null);

        assertThrows(NotFoundException.class, () -> slotService.book(slotId, request));
    }

    @Test
    void cancel_shouldCancelBookedSlot() {
        UUID slotId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        OffsetDateTime startAt = OffsetDateTime.now().plusDays(1);

        Student student = new Student("John Doe", null, null, null);
        student.setId(studentId);

        Slot slot = new Slot(startAt);
        slot.setId(slotId);
        slot.setStatus(SlotStatus.BOOKED);
        slot.setStudent(student);

        when(slotRepository.findByIdWithStudent(slotId)).thenReturn(Optional.of(slot));
        when(slotRepository.save(any(Slot.class))).thenAnswer(i -> i.getArgument(0));

        SlotCancelRequest request = new SlotCancelRequest(CancelledBy.STUDENT, "Sick");
        SlotResponse response = slotService.cancel(slotId, request);

        assertEquals(SlotStatus.CANCELLED, response.status());
        verify(slotEventService).recordEventWithStudentsAndReturn(eq(slotId), any(EventType.class), eq(studentId), isNull(), any());
    }

    @Test
    void cancel_shouldThrowWhenSlotNotBooked() {
        UUID slotId = UUID.randomUUID();
        OffsetDateTime startAt = OffsetDateTime.now().plusDays(1);

        Slot slot = new Slot(startAt);
        slot.setId(slotId);
        slot.setStatus(SlotStatus.FREE);

        when(slotRepository.findByIdWithStudent(slotId)).thenReturn(Optional.of(slot));

        SlotCancelRequest request = new SlotCancelRequest(CancelledBy.TEACHER, null);

        assertThrows(InvalidStateException.class, () -> slotService.cancel(slotId, request));
    }

    @Test
    void replace_shouldReplaceStudentInBookedSlot() {
        UUID slotId = UUID.randomUUID();
        UUID oldStudentId = UUID.randomUUID();
        UUID newStudentId = UUID.randomUUID();
        OffsetDateTime startAt = OffsetDateTime.now().plusDays(1);

        Student oldStudent = new Student("Old Student", null, null, null);
        oldStudent.setId(oldStudentId);

        Student newStudent = new Student("New Student", null, null, null);
        newStudent.setId(newStudentId);

        Slot slot = new Slot(startAt);
        slot.setId(slotId);
        slot.setStatus(SlotStatus.BOOKED);
        slot.setStudent(oldStudent);

        when(slotRepository.findByIdWithStudent(slotId)).thenReturn(Optional.of(slot));
        when(studentService.getEntityById(newStudentId)).thenReturn(newStudent);
        when(slotRepository.save(any(Slot.class))).thenAnswer(i -> i.getArgument(0));

        SlotReplaceRequest request = new SlotReplaceRequest(newStudentId, "Swap students");
        SlotResponse response = slotService.replace(slotId, request);

        assertEquals(SlotStatus.BOOKED, response.status());
        assertEquals(newStudentId, response.student().id());
        verify(slotEventService).recordEventWithStudentsAndReturn(eq(slotId), any(EventType.class), eq(oldStudentId), eq(newStudentId), any());
    }

    @Test
    void replace_shouldThrowWhenSlotIsFree() {
        UUID slotId = UUID.randomUUID();
        UUID newStudentId = UUID.randomUUID();
        OffsetDateTime startAt = OffsetDateTime.now().plusDays(1);

        Slot slot = new Slot(startAt);
        slot.setId(slotId);
        slot.setStatus(SlotStatus.FREE);

        when(slotRepository.findByIdWithStudent(slotId)).thenReturn(Optional.of(slot));

        SlotReplaceRequest request = new SlotReplaceRequest(newStudentId, null);

        assertThrows(InvalidStateException.class, () -> slotService.replace(slotId, request));
    }

    @Test
    void reschedule_shouldMoveBookingToTargetSlot() {
        UUID originSlotId = UUID.randomUUID();
        UUID targetSlotId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();

        Student student = new Student("John Doe", null, null, null);
        student.setId(studentId);

        Slot originSlot = new Slot(OffsetDateTime.now().plusDays(1));
        originSlot.setId(originSlotId);
        originSlot.setStatus(SlotStatus.BOOKED);
        originSlot.setStudent(student);
        originSlot.setNotes("Original notes");

        Slot targetSlot = new Slot(OffsetDateTime.now().plusDays(2));
        targetSlot.setId(targetSlotId);
        targetSlot.setStatus(SlotStatus.FREE);

        when(slotRepository.findByIdWithStudent(originSlotId)).thenReturn(Optional.of(originSlot));
        when(slotRepository.findByIdWithStudent(targetSlotId)).thenReturn(Optional.of(targetSlot));
        when(slotRepository.save(any(Slot.class))).thenAnswer(i -> i.getArgument(0));

        SlotRescheduleRequest request = new SlotRescheduleRequest(targetSlotId, "Reschedule reason");
        RescheduleResponse response = slotService.reschedule(originSlotId, request);

        assertEquals(SlotStatus.FREE, response.originSlot().status());
        assertNull(response.originSlot().student());
        assertEquals(SlotStatus.BOOKED, response.targetSlot().status());
        assertEquals(studentId, response.targetSlot().student().id());
    }

    @Test
    void reschedule_shouldThrowWhenOriginNotBooked() {
        UUID originSlotId = UUID.randomUUID();
        UUID targetSlotId = UUID.randomUUID();

        Slot originSlot = new Slot(OffsetDateTime.now().plusDays(1));
        originSlot.setId(originSlotId);
        originSlot.setStatus(SlotStatus.FREE);

        when(slotRepository.findByIdWithStudent(originSlotId)).thenReturn(Optional.of(originSlot));

        SlotRescheduleRequest request = new SlotRescheduleRequest(targetSlotId, null);

        assertThrows(InvalidStateException.class, () -> slotService.reschedule(originSlotId, request));
    }

    @Test
    void reschedule_shouldThrowWhenTargetNotFree() {
        UUID originSlotId = UUID.randomUUID();
        UUID targetSlotId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();

        Student student = new Student("John Doe", null, null, null);
        student.setId(studentId);

        Slot originSlot = new Slot(OffsetDateTime.now().plusDays(1));
        originSlot.setId(originSlotId);
        originSlot.setStatus(SlotStatus.BOOKED);
        originSlot.setStudent(student);

        Slot targetSlot = new Slot(OffsetDateTime.now().plusDays(2));
        targetSlot.setId(targetSlotId);
        targetSlot.setStatus(SlotStatus.BOOKED);

        when(slotRepository.findByIdWithStudent(originSlotId)).thenReturn(Optional.of(originSlot));
        when(slotRepository.findByIdWithStudent(targetSlotId)).thenReturn(Optional.of(targetSlot));

        SlotRescheduleRequest request = new SlotRescheduleRequest(targetSlotId, null);

        assertThrows(InvalidStateException.class, () -> slotService.reschedule(originSlotId, request));
    }

    @Test
    void free_shouldFreeACancelledSlot() {
        UUID slotId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();

        Student student = new Student("John Doe", null, null, null);
        student.setId(studentId);

        Slot slot = new Slot(OffsetDateTime.now().plusDays(1));
        slot.setId(slotId);
        slot.setStatus(SlotStatus.CANCELLED);
        slot.setStudent(student);

        when(slotRepository.findByIdWithStudent(slotId)).thenReturn(Optional.of(slot));
        when(slotRepository.save(any(Slot.class))).thenAnswer(i -> i.getArgument(0));

        SlotResponse response = slotService.free(slotId, new SlotFreeRequest(null));

        assertEquals(SlotStatus.FREE, response.status());
        assertNull(response.student());
        verify(slotEventService).recordEventAndReturn(eq(slotId), any(EventType.class), eq(studentId), isNull());
    }

    @Test
    void create_shouldThrowWhenSlotAlreadyExists() {
        // Use a fixed time within the 07:00–19:00 working-hours window so the
        // duplicate-check stub is guaranteed to be reached.
        OffsetDateTime startAt = FUTURE_WORKING_HOURS_SLOT;

        when(slotRepository.existsByStartAt(startAt)).thenReturn(true);

        SlotCreateRequest request = new SlotCreateRequest(startAt, 60);

        assertThrows(ConflictException.class, () -> slotService.create(request));
    }

    @Test
    void delete_shouldThrowWhenSlotIsBooked() {
        UUID slotId = UUID.randomUUID();

        Slot slot = new Slot(OffsetDateTime.now().plusDays(1));
        slot.setId(slotId);
        slot.setStatus(SlotStatus.BOOKED);

        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));

        assertThrows(ConflictException.class, () -> slotService.delete(slotId));
        verify(slotRepository, never()).delete(any(Slot.class));
    }

    // =========================================================================
    // CREATE — past validation
    // =========================================================================

    @Test
    void create_shouldRejectPastSlot() {
        OffsetDateTime past = OffsetDateTime.now(ZoneId.of("Europe/Sofia")).minusHours(1)
                .withMinute(0).withSecond(0).withNano(0);
        SlotCreateRequest request = new SlotCreateRequest(past, 60);

        assertThrows(ConflictException.class, () -> slotService.create(request));
        verify(slotRepository, never()).save(any());
    }

    // =========================================================================
    // CREATE — working-hours enforcement
    // =========================================================================

    @Test
    void create_shouldRejectSlotBefore7AM() {
        OffsetDateTime before7am = ZonedDateTime.now(ZoneId.of("Europe/Sofia"))
                .plusDays(1).withHour(6).withMinute(0).withSecond(0).withNano(0)
                .toOffsetDateTime();
        SlotCreateRequest request = new SlotCreateRequest(before7am, 60);

        assertThrows(ConflictException.class, () -> slotService.create(request));
        verify(slotRepository, never()).save(any());
    }

    @Test
    void create_shouldRejectSlotAt7PM() {
        // 19:00 is the exclusive upper bound — must be rejected
        OffsetDateTime at7pm = ZonedDateTime.now(ZoneId.of("Europe/Sofia"))
                .plusDays(1).withHour(19).withMinute(0).withSecond(0).withNano(0)
                .toOffsetDateTime();
        SlotCreateRequest request = new SlotCreateRequest(at7pm, 60);

        assertThrows(ConflictException.class, () -> slotService.create(request));
        verify(slotRepository, never()).save(any());
    }

    @Test
    void create_shouldAcceptSlotAt7AM() {
        // 07:00 is the inclusive lower bound — must succeed
        OffsetDateTime at7am = ZonedDateTime.now(ZoneId.of("Europe/Sofia"))
                .plusDays(1).withHour(7).withMinute(0).withSecond(0).withNano(0)
                .toOffsetDateTime();
        UUID slotId = UUID.randomUUID();

        when(slotRepository.existsByStartAt(at7am)).thenReturn(false);
        when(slotRepository.save(any(Slot.class))).thenAnswer(i -> {
            Slot s = i.getArgument(0);
            s.setId(slotId);
            return s;
        });

        SlotResponse response = slotService.create(new SlotCreateRequest(at7am, 60));
        assertEquals(SlotStatus.FREE, response.status());
    }

    @Test
    void create_shouldAcceptSlotAt6PM() {
        // 18:00 is the last valid hour before the 19:00 exclusive bound
        OffsetDateTime at6pm = ZonedDateTime.now(ZoneId.of("Europe/Sofia"))
                .plusDays(1).withHour(18).withMinute(0).withSecond(0).withNano(0)
                .toOffsetDateTime();
        UUID slotId = UUID.randomUUID();

        when(slotRepository.existsByStartAt(at6pm)).thenReturn(false);
        when(slotRepository.save(any(Slot.class))).thenAnswer(i -> {
            Slot s = i.getArgument(0);
            s.setId(slotId);
            return s;
        });

        SlotResponse response = slotService.create(new SlotCreateRequest(at6pm, 60));
        assertEquals(SlotStatus.FREE, response.status());
    }

    // =========================================================================
    // BOOK — past validation
    // =========================================================================

    @Test
    void book_shouldRejectPastSlot() {
        UUID slotId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        OffsetDateTime past = OffsetDateTime.now(ZoneId.of("Europe/Sofia")).minusHours(2)
                .withMinute(0).withSecond(0).withNano(0);

        Slot slot = new Slot(past);
        slot.setId(slotId);
        slot.setStatus(SlotStatus.FREE);

        when(slotRepository.findByIdWithStudent(slotId)).thenReturn(Optional.of(slot));

        assertThrows(ConflictException.class, () -> slotService.book(slotId, new SlotBookRequest(studentId, null)));
        verify(slotRepository, never()).save(any());
    }

    // =========================================================================
    // BOOK — waitlist auto-cleanup
    // =========================================================================

    @Test
    void book_shouldRemoveStudentFromWaitlistAfterBooking() {
        UUID slotId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();

        Slot slot = new Slot(FUTURE_WORKING_HOURS_SLOT);
        slot.setId(slotId);
        slot.setStatus(SlotStatus.FREE);

        Student student = new Student("Jane Doe", null, null, null);
        student.setId(studentId);

        when(slotRepository.findByIdWithStudent(slotId)).thenReturn(Optional.of(slot));
        when(studentService.getEntityById(studentId)).thenReturn(student);
        when(slotRepository.save(any(Slot.class))).thenAnswer(i -> i.getArgument(0));

        slotService.book(slotId, new SlotBookRequest(studentId, null));

        verify(waitlistService).removeActiveByStudentId(studentId);
    }

    @Test
    void book_shouldNotInteractWithWaitlistWhenBookingFails() {
        UUID slotId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();

        Slot slot = new Slot(FUTURE_WORKING_HOURS_SLOT);
        slot.setId(slotId);
        slot.setStatus(SlotStatus.BOOKED); // already booked → will throw

        when(slotRepository.findByIdWithStudent(slotId)).thenReturn(Optional.of(slot));

        assertThrows(InvalidStateException.class, () -> slotService.book(slotId, new SlotBookRequest(studentId, null)));
        verify(waitlistService, never()).removeActiveByStudentId(any());
    }

    // =========================================================================
    // BLOCK SLOT (single-slot direct blocking)
    // =========================================================================

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void blockSlot_shouldSetFreeSlotToBlocked() {
        UUID slotId = UUID.randomUUID();

        Slot slot = new Slot(FUTURE_WORKING_HOURS_SLOT);
        slot.setId(slotId);
        slot.setStatus(SlotStatus.FREE);

        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(slotRepository.save(any(Slot.class))).thenAnswer(i -> i.getArgument(0));

        SlotResponse response = slotService.blockSlot(slotId);

        assertEquals(SlotStatus.BLOCKED, response.status());
        verify(slotEventService).recordEventAndReturn(eq(slotId), any(EventType.class), any(Map.class));
    }

    @Test
    void blockSlot_shouldSetCancelledSlotToBlocked() {
        UUID slotId = UUID.randomUUID();

        Slot slot = new Slot(FUTURE_WORKING_HOURS_SLOT);
        slot.setId(slotId);
        slot.setStatus(SlotStatus.CANCELLED);

        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(slotRepository.save(any(Slot.class))).thenAnswer(i -> i.getArgument(0));

        SlotResponse response = slotService.blockSlot(slotId);

        assertEquals(SlotStatus.BLOCKED, response.status());
    }

    @Test
    void blockSlot_shouldThrowWhenSlotIsBooked() {
        UUID slotId = UUID.randomUUID();

        Slot slot = new Slot(FUTURE_WORKING_HOURS_SLOT);
        slot.setId(slotId);
        slot.setStatus(SlotStatus.BOOKED);

        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));

        assertThrows(InvalidStateException.class, () -> slotService.blockSlot(slotId));
        verify(slotRepository, never()).save(any());
    }

    @Test
    void blockSlot_shouldThrowWhenAlreadyBlocked() {
        UUID slotId = UUID.randomUUID();

        Slot slot = new Slot(FUTURE_WORKING_HOURS_SLOT);
        slot.setId(slotId);
        slot.setStatus(SlotStatus.BLOCKED);

        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));

        assertThrows(InvalidStateException.class, () -> slotService.blockSlot(slotId));
        verify(slotRepository, never()).save(any());
    }

    @Test
    void blockSlot_shouldThrowWhenNotFound() {
        UUID slotId = UUID.randomUUID();
        when(slotRepository.findById(slotId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> slotService.blockSlot(slotId));
    }

    // =========================================================================
    // UNBLOCK SLOT (single-slot unblock)
    // =========================================================================

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void unblockSlot_shouldSetBlockedSlotToFree() {
        UUID slotId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();

        Slot slot = new Slot(FUTURE_WORKING_HOURS_SLOT);
        slot.setId(slotId);
        slot.setStatus(SlotStatus.BLOCKED);
        slot.setBlockId(blockId);

        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(slotRepository.save(any(Slot.class))).thenAnswer(i -> i.getArgument(0));

        SlotResponse response = slotService.unblockSlot(slotId);

        assertEquals(SlotStatus.FREE, response.status());
        verify(slotEventService).recordEventAndReturn(eq(slotId), any(EventType.class), any(Map.class));
    }

    @Test
    void unblockSlot_shouldWorkWhenNoBlockId() {
        // A slot can be BLOCKED without a blockId (blocked via blockSlot directly)
        UUID slotId = UUID.randomUUID();

        Slot slot = new Slot(FUTURE_WORKING_HOURS_SLOT);
        slot.setId(slotId);
        slot.setStatus(SlotStatus.BLOCKED);
        // blockId is null — set via blockSlot, not createBlock

        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(slotRepository.save(any(Slot.class))).thenAnswer(i -> i.getArgument(0));

        SlotResponse response = slotService.unblockSlot(slotId);

        assertEquals(SlotStatus.FREE, response.status());
    }

    @Test
    void unblockSlot_shouldOnlyAffectOneSlot() {
        // Verifies save is called exactly once (not for an entire group)
        UUID slotId = UUID.randomUUID();

        Slot slot = new Slot(FUTURE_WORKING_HOURS_SLOT);
        slot.setId(slotId);
        slot.setStatus(SlotStatus.BLOCKED);
        slot.setBlockId(UUID.randomUUID());

        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(slotRepository.save(any(Slot.class))).thenAnswer(i -> i.getArgument(0));

        slotService.unblockSlot(slotId);

        verify(slotRepository, times(1)).save(any());
    }

    @Test
    void unblockSlot_shouldThrowWhenSlotIsFree() {
        UUID slotId = UUID.randomUUID();

        Slot slot = new Slot(FUTURE_WORKING_HOURS_SLOT);
        slot.setId(slotId);
        slot.setStatus(SlotStatus.FREE);

        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));

        assertThrows(InvalidStateException.class, () -> slotService.unblockSlot(slotId));
        verify(slotRepository, never()).save(any());
    }

    @Test
    void unblockSlot_shouldThrowWhenSlotIsBooked() {
        UUID slotId = UUID.randomUUID();

        Slot slot = new Slot(FUTURE_WORKING_HOURS_SLOT);
        slot.setId(slotId);
        slot.setStatus(SlotStatus.BOOKED);

        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));

        assertThrows(InvalidStateException.class, () -> slotService.unblockSlot(slotId));
        verify(slotRepository, never()).save(any());
    }

    @Test
    void unblockSlot_shouldThrowWhenNotFound() {
        UUID slotId = UUID.randomUUID();
        when(slotRepository.findById(slotId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> slotService.unblockSlot(slotId));
    }
}
