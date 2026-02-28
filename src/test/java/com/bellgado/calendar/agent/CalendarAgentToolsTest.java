package com.bellgado.calendar.agent;

import com.bellgado.calendar.api.dto.*;
import com.bellgado.calendar.application.exception.NotFoundException;
import com.bellgado.calendar.application.service.BlockService;
import com.bellgado.calendar.application.service.SlotService;
import com.bellgado.calendar.application.service.StudentService;
import com.bellgado.calendar.application.service.WaitlistService;
import com.bellgado.calendar.domain.enums.SlotStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CalendarAgentToolsTest {

    @Mock
    private SlotService slotService;
    @Mock
    private StudentService studentService;
    @Mock
    private BlockService blockService;
    @Mock
    private WaitlistService waitlistService;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @InjectMocks
    private CalendarAgentTools tools;

    private final UUID slotId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();

    @Test
    void createSlot_parsesIsoDateTimeAndDelegatesToService() throws Exception {
        String startAt = "2025-03-10T14:00:00+02:00";
        SlotResponse mockResponse = new SlotResponse(
                slotId, OffsetDateTime.parse(startAt),
                OffsetDateTime.parse(startAt).plusHours(1),
                SlotStatus.FREE, null, null, 0, null, null);

        when(slotService.create(any(SlotCreateRequest.class))).thenReturn(mockResponse);

        String result = tools.createSlot(startAt);

        assertThat(result).contains(slotId.toString());
        assertThat(result).contains("FREE");

        ArgumentCaptor<SlotCreateRequest> captor = ArgumentCaptor.forClass(SlotCreateRequest.class);
        verify(slotService).create(captor.capture());
        assertThat(captor.getValue().startAt()).isEqualTo(OffsetDateTime.parse(startAt));
    }

    @Test
    void bookSlot_parsesUuidsAndDelegatesToService() throws Exception {
        SlotResponse mockResponse = new SlotResponse(
                slotId, OffsetDateTime.now(), OffsetDateTime.now().plusHours(1),
                SlotStatus.BOOKED, null, null, 1, null, null);

        when(slotService.book(any(UUID.class), any(SlotBookRequest.class))).thenReturn(mockResponse);

        String result = tools.bookSlot(slotId.toString(), studentId.toString(), "test notes");

        assertThat(result).contains("BOOKED");

        ArgumentCaptor<SlotBookRequest> captor = ArgumentCaptor.forClass(SlotBookRequest.class);
        verify(slotService).book(eq(slotId), captor.capture());
        assertThat(captor.getValue().studentId()).isEqualTo(studentId);
        assertThat(captor.getValue().notes()).isEqualTo("test notes");
    }

    @Test
    void bookSlot_returnsErrorStringWhenSlotNotFound() {
        when(slotService.book(any(UUID.class), any(SlotBookRequest.class)))
                .thenThrow(new NotFoundException("Slot not found: " + slotId));

        String result = tools.bookSlot(slotId.toString(), studentId.toString(), "");

        assertThat(result).startsWith("Error:");
        assertThat(result).contains("Slot not found");
    }

    @Test
    void bookSlot_emptyNotesArePassedAsNull() throws Exception {
        SlotResponse mockResponse = new SlotResponse(
                slotId, OffsetDateTime.now(), OffsetDateTime.now().plusHours(1),
                SlotStatus.BOOKED, null, null, 1, null, null);
        when(slotService.book(any(UUID.class), any(SlotBookRequest.class))).thenReturn(mockResponse);

        tools.bookSlot(slotId.toString(), studentId.toString(), "");

        ArgumentCaptor<SlotBookRequest> captor = ArgumentCaptor.forClass(SlotBookRequest.class);
        verify(slotService).book(eq(slotId), captor.capture());
        assertThat(captor.getValue().notes()).isNull();
    }

    @Test
    void listSlots_parsesStatusesCorrectly() throws Exception {
        when(slotService.list(any(), any(), anyCollection())).thenReturn(List.of());

        tools.listSlots("2025-03-10T00:00:00+02:00", "2025-03-17T00:00:00+02:00", "FREE,BOOKED");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Collection<SlotStatus>> captor =
                ArgumentCaptor.forClass(java.util.Collection.class);
        verify(slotService).list(any(), any(), captor.capture());
        assertThat(captor.getValue()).containsExactly(SlotStatus.FREE, SlotStatus.BOOKED);
    }

    @Test
    void listSlots_emptyStatusesMeansAll() throws Exception {
        when(slotService.list(any(), any(), anyCollection())).thenReturn(List.of());

        tools.listSlots("2025-03-10T00:00:00+02:00", "2025-03-17T00:00:00+02:00", "");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Collection<SlotStatus>> captor =
                ArgumentCaptor.forClass(java.util.Collection.class);
        verify(slotService).list(any(), any(), captor.capture());
        assertThat(captor.getValue()).isEmpty();
    }

    @Test
    void searchStudents_parsesActiveFilter() throws Exception {
        when(studentService.list(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        tools.searchStudents("Maria", "true");

        verify(studentService).list(eq("Maria"), eq(true), any());
    }

    @Test
    void searchStudents_emptyActiveReturnsAll() throws Exception {
        when(studentService.list(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        tools.searchStudents("", "");

        verify(studentService).list(isNull(), isNull(), any());
    }

    @Test
    void createStudent_passesBasicFieldsOnly() throws Exception {
        StudentResponse mockResponse = new StudentResponse(
                studentId, "Maria", "0888", "maria@test.com", null,
                true, null, false, null, false, null, null, null, null, null, null, null,
                null, null);
        when(studentService.create(any(StudentCreateRequest.class))).thenReturn(mockResponse);

        String result = tools.createStudent("Maria", "0888", "maria@test.com", "");

        assertThat(result).contains("Maria");

        ArgumentCaptor<StudentCreateRequest> captor = ArgumentCaptor.forClass(StudentCreateRequest.class);
        verify(studentService).create(captor.capture());
        assertThat(captor.getValue().fullName()).isEqualTo("Maria");
        assertThat(captor.getValue().phone()).isEqualTo("0888");
        assertThat(captor.getValue().notes()).isNull();
    }

    @Test
    void deleteSlot_returnsSuccessMessage() {
        doNothing().when(slotService).delete(any(UUID.class));

        String result = tools.deleteSlot(slotId.toString());

        assertThat(result).contains("deleted successfully");
        verify(slotService).delete(slotId);
    }

    @Test
    void cancelSlot_parsesCancelledByEnum() throws Exception {
        SlotResponse mockResponse = new SlotResponse(
                slotId, OffsetDateTime.now(), OffsetDateTime.now().plusHours(1),
                SlotStatus.CANCELLED, null, null, 1, null, null);
        when(slotService.cancel(any(UUID.class), any(SlotCancelRequest.class))).thenReturn(mockResponse);

        tools.cancelSlot(slotId.toString(), "student", "sick");

        ArgumentCaptor<SlotCancelRequest> captor = ArgumentCaptor.forClass(SlotCancelRequest.class);
        verify(slotService).cancel(eq(slotId), captor.capture());
        assertThat(captor.getValue().cancelledBy()).isEqualTo(com.bellgado.calendar.domain.enums.CancelledBy.STUDENT);
        assertThat(captor.getValue().reason()).isEqualTo("sick");
    }

    @Test
    void invalidUuid_returnsError() {
        String result = tools.getSlotById("not-a-uuid");

        assertThat(result).startsWith("Error:");
    }

    @Test
    void invalidDateTime_returnsError() {
        String result = tools.createSlot("not-a-date");

        assertThat(result).startsWith("Error:");
    }
}
