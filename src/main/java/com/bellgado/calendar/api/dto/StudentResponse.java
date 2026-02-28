package com.bellgado.calendar.api.dto;

import com.bellgado.calendar.domain.entity.Student;
import com.bellgado.calendar.domain.enums.NotificationChannel;
import com.bellgado.calendar.domain.enums.UserStatus;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

public record StudentResponse(
        UUID id,
        String fullName,
        String phone,
        String email,
        String notes,
        boolean active,

        // User account status: null = no account yet, non-null = account exists
        UserStatus userStatus,
        // True when invite can be (re-)sent: no account, or PENDING_CONFIRMATION with expired token
        boolean canInvite,

        // Notification preferences
        NotificationChannel preferredNotificationChannel,
        boolean notificationOptIn,
        OffsetDateTime notificationOptInAt,
        String phoneE164,
        String whatsappNumberE164,
        String timezone,
        String locale,
        LocalTime quietHoursStart,
        LocalTime quietHoursEnd,

        // Audit
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    /** Used in SSE events and places where user status is not needed. */
    public static StudentResponse from(Student student) {
        return from(student, null, false);
    }

    public static StudentResponse from(Student student, UserStatus userStatus, boolean canInvite) {
        return new StudentResponse(
                student.getId(),
                student.getFullName(),
                student.getPhone(),
                student.getEmail(),
                student.getNotes(),
                student.isActive(),
                userStatus,
                canInvite,
                student.getPreferredNotificationChannel(),
                student.isNotificationOptIn(),
                student.getNotificationOptInAt(),
                student.getPhoneE164(),
                student.getWhatsappNumberE164(),
                student.getTimezone(),
                student.getLocale(),
                student.getQuietHoursStart(),
                student.getQuietHoursEnd(),
                student.getCreatedAt(),
                student.getUpdatedAt()
        );
    }
}
