package com.bellgado.calendar.application.service;

import com.bellgado.calendar.api.dto.NotificationPreferencesRequest;
import com.bellgado.calendar.api.dto.StudentCreateRequest;
import com.bellgado.calendar.api.dto.StudentResponse;
import com.bellgado.calendar.api.dto.StudentUpdateRequest;
import com.bellgado.calendar.api.sse.SseEventType;
import com.bellgado.calendar.application.event.StudentChangedEvent;
import com.bellgado.calendar.application.exception.ConflictException;
import com.bellgado.calendar.application.exception.InvalidStateException;
import com.bellgado.calendar.application.exception.NotFoundException;
import com.bellgado.calendar.domain.entity.Student;
import com.bellgado.calendar.domain.entity.User;
import com.bellgado.calendar.domain.enums.UserRole;
import com.bellgado.calendar.domain.enums.UserStatus;
import com.bellgado.calendar.infrastructure.repository.StudentRepository;
import com.bellgado.calendar.infrastructure.repository.UserRepository;
import com.bellgado.calendar.infrastructure.specification.StudentSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudentService {

    private final StudentRepository studentRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthEmailService authEmailService;

    private static final String ALPHANUM = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

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
        StudentResponse response = StudentResponse.from(student);
        eventPublisher.publishEvent(new StudentChangedEvent(SseEventType.STUDENT_CREATED, response));
        return response;
    }

    @Transactional(readOnly = true)
    public Page<StudentResponse> list(String query, Boolean active, Pageable pageable) {
        Page<Student> page = studentRepository.findAll(StudentSpecifications.search(query, active), pageable);

        // Batch-fetch users in a single query to avoid N+1
        List<UUID> ids = page.stream().map(Student::getId).toList();
        Map<UUID, User> userMap = userRepository.findByStudentIdIn(ids).stream()
                .collect(Collectors.toMap(User::getStudentId, u -> u));

        return page.map(s -> {
            User u = userMap.get(s.getId());
            return StudentResponse.from(s, u == null ? null : u.getStatus(), computeCanInvite(s, u));
        });
    }

    @Transactional(readOnly = true)
    public StudentResponse getById(UUID id) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Student not found: " + id));
        Optional<User> user = userRepository.findByStudentId(id);
        return StudentResponse.from(student,
                user.map(User::getStatus).orElse(null),
                computeCanInvite(student, user.orElse(null)));
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
        StudentResponse response = StudentResponse.from(student);
        eventPublisher.publishEvent(new StudentChangedEvent(SseEventType.STUDENT_UPDATED, response));
        return response;
    }

    @Transactional
    public void deactivate(UUID id) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Student not found: " + id));
        student.setActive(false);
        student = studentRepository.save(student);
        eventPublisher.publishEvent(new StudentChangedEvent(SseEventType.STUDENT_DEACTIVATED, StudentResponse.from(student)));
    }

    /**
     * Creates a user account for an existing student and sends them an invitation email
     * with a temporary password and email confirmation link.
     *
     * If the student already has a PENDING_CONFIRMATION account whose token has expired,
     * the existing account is refreshed (new password + new token) and the email is re-sent.
     */
    @Transactional
    public void inviteStudent(UUID studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new NotFoundException("Student not found: " + studentId));

        if (student.getEmail() == null || student.getEmail().isBlank()) {
            throw new InvalidStateException("Student does not have an email address.");
        }

        Optional<User> existing = userRepository.findByStudentId(studentId);
        if (existing.isPresent()) {
            User user = existing.get();
            // Allow re-invite only when PENDING_CONFIRMATION and token has expired
            if (user.getStatus() == UserStatus.PENDING_CONFIRMATION
                    && user.getConfirmationTokenExpiresAt() != null
                    && user.getConfirmationTokenExpiresAt().isBefore(OffsetDateTime.now())) {
                String tempPassword = generateTempPassword(12);
                String confirmationToken = UUID.randomUUID().toString().replace("-", "");
                user.setPasswordHash(passwordEncoder.encode(tempPassword));
                user.setConfirmationToken(confirmationToken);
                user.setConfirmationTokenExpiresAt(OffsetDateTime.now().plusHours(72));
                user.setPasswordChangeRequired(true);
                userRepository.save(user);
                authEmailService.sendInvitation(student.getEmail(), student.getFullName(),
                        tempPassword, confirmationToken);
                return;
            }
            throw new ConflictException("An account already exists for this student.");
        }

        if (userRepository.existsByEmail(student.getEmail())) {
            throw new ConflictException("A user account with this email already exists.");
        }

        String tempPassword = generateTempPassword(12);
        String confirmationToken = UUID.randomUUID().toString().replace("-", "");

        User user = new User();
        user.setEmail(student.getEmail());
        user.setPasswordHash(passwordEncoder.encode(tempPassword));
        user.setFullName(student.getFullName());
        user.setRole(UserRole.STUDENT);
        user.setStatus(UserStatus.PENDING_CONFIRMATION);
        user.setStudentId(studentId);
        user.setPasswordChangeRequired(true);
        user.setConfirmationToken(confirmationToken);
        user.setConfirmationTokenExpiresAt(OffsetDateTime.now().plusHours(72));
        userRepository.save(user);

        authEmailService.sendInvitation(student.getEmail(), student.getFullName(),
                tempPassword, confirmationToken);
    }

    /**
     * Returns true when an invitation can be (re-)sent for this student.
     * Conditions: student has email AND (no user account OR PENDING_CONFIRMATION with expired token).
     */
    private boolean computeCanInvite(Student student, User user) {
        if (student.getEmail() == null || student.getEmail().isBlank()) return false;
        if (user == null) return true;
        return user.getStatus() == UserStatus.PENDING_CONFIRMATION
                && user.getConfirmationTokenExpiresAt() != null
                && user.getConfirmationTokenExpiresAt().isBefore(OffsetDateTime.now());
    }

    private String generateTempPassword(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUM.charAt(RANDOM.nextInt(ALPHANUM.length())));
        }
        return sb.toString();
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
        StudentResponse response = StudentResponse.from(student);
        eventPublisher.publishEvent(new StudentChangedEvent(SseEventType.STUDENT_UPDATED, response));
        return response;
    }

}
