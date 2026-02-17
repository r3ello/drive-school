package com.bellgado.calendar.api.dto;

import com.bellgado.calendar.domain.entity.Student;
import com.bellgado.calendar.domain.enums.NotificationChannel;

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
    public static StudentResponse from(Student student) {
        return new StudentResponse(
                student.getId(),
                student.getFullName(),
                student.getPhone(),
                student.getEmail(),
                student.getNotes(),
                student.isActive(),
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
