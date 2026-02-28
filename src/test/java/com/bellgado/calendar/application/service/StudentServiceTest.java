package com.bellgado.calendar.application.service;

import com.bellgado.calendar.api.dto.StudentCreateRequest;
import com.bellgado.calendar.api.dto.StudentResponse;
import com.bellgado.calendar.api.dto.StudentUpdateRequest;
import com.bellgado.calendar.api.sse.SseEventType;
import com.bellgado.calendar.application.event.StudentChangedEvent;
import com.bellgado.calendar.domain.entity.Student;
import com.bellgado.calendar.application.service.AuthEmailService;
import com.bellgado.calendar.infrastructure.repository.StudentRepository;
import com.bellgado.calendar.infrastructure.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentServiceTest {

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthEmailService authEmailService;

    private StudentService studentService;

    @BeforeEach
    void setUp() {
        studentService = new StudentService(studentRepository, eventPublisher,
                userRepository, passwordEncoder, authEmailService);
    }

    // =========================================================================
    // create
    // =========================================================================

    @Test
    void create_shouldSaveAndReturnStudentResponse() {
        StudentCreateRequest request = new StudentCreateRequest(
                "Alice Smith", "0888111222", "alice@example.com", "notes",
                null, null, null, null, null, null, null, null);

        Student saved = new Student("Alice Smith", "0888111222", "alice@example.com", "notes");
        saved.setId(UUID.randomUUID());
        when(studentRepository.save(any(Student.class))).thenReturn(saved);

        StudentResponse response = studentService.create(request);

        assertEquals("Alice Smith", response.fullName());
        assertEquals("0888111222", response.phone());
    }

    @Test
    void create_shouldPublishStudentCreatedEvent() {
        StudentCreateRequest request = new StudentCreateRequest(
                "Bob Jones", null, null, null,
                null, null, null, null, null, null, null, null);

        Student saved = new Student("Bob Jones", null, null, null);
        saved.setId(UUID.randomUUID());
        when(studentRepository.save(any(Student.class))).thenReturn(saved);

        studentService.create(request);

        ArgumentCaptor<StudentChangedEvent> captor = ArgumentCaptor.forClass(StudentChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertEquals(SseEventType.STUDENT_CREATED, captor.getValue().eventType());
        assertEquals("Bob Jones", captor.getValue().student().fullName());
    }

    // =========================================================================
    // update
    // =========================================================================

    @Test
    void update_shouldPublishStudentUpdatedEvent() {
        UUID id = UUID.randomUUID();
        Student existing = new Student("Old Name", null, null, null);
        existing.setId(id);

        when(studentRepository.findById(id)).thenReturn(Optional.of(existing));
        when(studentRepository.save(any(Student.class))).thenAnswer(i -> i.getArgument(0));

        StudentUpdateRequest request = new StudentUpdateRequest(
                "New Name", null, null, null, true,
                null, null, null, null, null, null, null, null);
        studentService.update(id, request);

        ArgumentCaptor<StudentChangedEvent> captor = ArgumentCaptor.forClass(StudentChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertEquals(SseEventType.STUDENT_UPDATED, captor.getValue().eventType());
    }

    // =========================================================================
    // deactivate
    // =========================================================================

    @Test
    void deactivate_shouldSetActiveToFalseAndPublishEvent() {
        UUID id = UUID.randomUUID();
        Student student = new Student("Carol White", null, null, null);
        student.setId(id);
        student.setActive(true);

        when(studentRepository.findById(id)).thenReturn(Optional.of(student));
        when(studentRepository.save(any(Student.class))).thenAnswer(i -> i.getArgument(0));

        studentService.deactivate(id);

        assertFalse(student.isActive());

        ArgumentCaptor<StudentChangedEvent> captor = ArgumentCaptor.forClass(StudentChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertEquals(SseEventType.STUDENT_DEACTIVATED, captor.getValue().eventType());
        assertFalse(captor.getValue().student().active());
    }

    @Test
    void deactivate_shouldThrowWhenStudentNotFound() {
        UUID id = UUID.randomUUID();
        when(studentRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(com.bellgado.calendar.application.exception.NotFoundException.class,
                () -> studentService.deactivate(id));
        verify(eventPublisher, never()).publishEvent(any());
    }
}
