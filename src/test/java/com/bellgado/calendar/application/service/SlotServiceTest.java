package com.bellgado.calendar.application.service;

import com.bellgado.calendar.api.dto.*;
import com.bellgado.calendar.application.exception.ConflictException;
import com.bellgado.calendar.application.exception.InvalidStateException;
import com.bellgado.calendar.application.exception.NotFoundException;
import com.bellgado.calendar.domain.entity.Slot;
import com.bellgado.calendar.domain.entity.Student;
import com.bellgado.calendar.domain.enums.CancelledBy;
import com.bellgado.calendar.domain.enums.SlotStatus;
import com.bellgado.calendar.infrastructure.repository.SlotRepository;
import com.bellgado.calendar.notification.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SlotServiceTest {

    @Mock
    private SlotRepository slotRepository;

    @Mock
    private StudentService studentService;

    @Mock
    private SlotEventService slotEventService;
@Mock
private NotificationService notificationService;
    private SlotService slotService;

    @BeforeEach
    void setUp() {
        slotService = new SlotService(slotRepository, studentService, slotEventService, notificationService);
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
        verify(slotEventService).recordEvent(eq(slotId), any(), isNull(), eq(studentId));
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
        verify(slotEventService).recordEventWithStudents(eq(slotId), any(), eq(studentId), isNull(), any());
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
        verify(slotEventService).recordEventWithStudents(eq(slotId), any(), eq(oldStudentId), eq(newStudentId), any());
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
        verify(slotEventService).recordEvent(eq(slotId), any(), eq(studentId), isNull());
    }

    @Test
    void create_shouldThrowWhenSlotAlreadyExists() {
        OffsetDateTime startAt = OffsetDateTime.now().plusDays(1);

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
}
