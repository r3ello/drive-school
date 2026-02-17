package com.bellgado.calendar.application.service;

import com.bellgado.calendar.api.dto.StudentCreateRequest;
import com.bellgado.calendar.api.dto.StudentResponse;
import com.bellgado.calendar.api.dto.StudentUpdateRequest;
import com.bellgado.calendar.application.exception.NotFoundException;
import com.bellgado.calendar.domain.entity.Student;
import com.bellgado.calendar.domain.enums.NotificationChannel;
import com.bellgado.calendar.infrastructure.repository.StudentRepository;
import com.bellgado.calendar.infrastructure.specification.StudentSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudentService {

    private final StudentRepository studentRepository;

    @Transactional
    public StudentResponse create(StudentCreateRequest request) {
        Student student = new Student(
                request.fullName(),
                request.phone(),
                request.email(),
                request.notes()
        );

        // Set notification preferences if provided
        if (request.preferredNotificationChannel() != null) {
            student.setPreferredNotificationChannel(request.preferredNotificationChannel());
        }
        if (request.notificationOptIn() != null) {
            student.setNotificationOptIn(request.notificationOptIn());
        }
        if (request.phoneE164() != null) {
            student.setPhoneE164(request.phoneE164());
        }
        if (request.whatsappNumberE164() != null) {
            student.setWhatsappNumberE164(request.whatsappNumberE164());
        }
        if (request.timezone() != null) {
            student.setTimezone(request.timezone());
        }
        if (request.locale() != null) {
            student.setLocale(request.locale());
        }
        if (request.quietHoursStart() != null) {
            student.setQuietHoursStart(request.quietHoursStart());
        }
        if (request.quietHoursEnd() != null) {
            student.setQuietHoursEnd(request.quietHoursEnd());
        }

        student = studentRepository.save(student);
        return StudentResponse.from(student);
    }

    @Transactional(readOnly = true)
    public Page<StudentResponse> list(String query, Boolean active, Pageable pageable) {
        return studentRepository.findAll(StudentSpecifications.search(query, active), pageable)
                .map(StudentResponse::from);
    }

    @Transactional(readOnly = true)
    public StudentResponse getById(UUID id) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Student not found: " + id));
        return StudentResponse.from(student);
    }

    @Transactional(readOnly = true)
    public Student getEntityById(UUID id) {
        return studentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Student not found: " + id));
    }

    @Transactional
    public StudentResponse update(UUID id, StudentUpdateRequest request) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Student not found: " + id));

        // Basic fields
        student.setFullName(request.fullName());
        student.setPhone(request.phone());
        student.setEmail(request.email());
        student.setNotes(request.notes());
        student.setActive(request.active());

        // Notification preferences
        if (request.preferredNotificationChannel() != null) {
            student.setPreferredNotificationChannel(request.preferredNotificationChannel());
        }
        if (request.notificationOptIn() != null) {
            student.setNotificationOptIn(request.notificationOptIn());
        }
        if (request.phoneE164() != null) {
            student.setPhoneE164(request.phoneE164());
        } else if (request.phone() == null || request.phone().isBlank()) {
            // Clear E164 if phone is cleared
            student.setPhoneE164(null);
        }
        if (request.whatsappNumberE164() != null) {
            student.setWhatsappNumberE164(request.whatsappNumberE164());
        }
        if (request.timezone() != null) {
            student.setTimezone(request.timezone());
        }
        if (request.locale() != null) {
            student.setLocale(request.locale());
        }
        // Quiet hours can be explicitly set to null
        student.setQuietHoursStart(request.quietHoursStart());
        student.setQuietHoursEnd(request.quietHoursEnd());

        student = studentRepository.save(student);
        return StudentResponse.from(student);
    }

    @Transactional
    public void deactivate(UUID id) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Student not found: " + id));
        student.setActive(false);
        studentRepository.save(student);
    }

    /**
     * Updates only the notification preferences for a student.
     */
    @Transactional
    public StudentResponse updateNotificationPreferences(UUID id, NotificationPreferencesRequest request) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Student not found: " + id));

        if (request.preferredChannel() != null) {
            student.setPreferredNotificationChannel(request.preferredChannel());
        }
        if (request.optIn() != null) {
            student.setNotificationOptIn(request.optIn());
        }
        if (request.phoneE164() != null) {
            student.setPhoneE164(request.phoneE164());
        }
        if (request.whatsappNumberE164() != null) {
            student.setWhatsappNumberE164(request.whatsappNumberE164());
        }
        if (request.timezone() != null) {
            student.setTimezone(request.timezone());
        }
        if (request.locale() != null) {
            student.setLocale(request.locale());
        }
        student.setQuietHoursStart(request.quietHoursStart());
        student.setQuietHoursEnd(request.quietHoursEnd());

        student = studentRepository.save(student);
        return StudentResponse.from(student);
    }

    /**
     * Request for updating notification preferences only.
     */
    public record NotificationPreferencesRequest(
            NotificationChannel preferredChannel,
            Boolean optIn,
            String phoneE164,
            String whatsappNumberE164,
            String timezone,
            String locale,
            java.time.LocalTime quietHoursStart,
            java.time.LocalTime quietHoursEnd
    ) {}
}
