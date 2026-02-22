package com.bellgado.calendar.notification;

import com.bellgado.calendar.domain.entity.Notification;
import com.bellgado.calendar.domain.entity.Student;
import com.bellgado.calendar.domain.enums.NotificationChannel;
import com.bellgado.calendar.notification.provider.NotificationMessage;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Builds {@link NotificationMessage} instances from a {@link Notification} and its target {@link Student}.
 * Extracted to eliminate duplication between {@link NotificationService} and {@link NotificationScheduler}.
 */
@Component
public class NotificationMessageFactory {

    public NotificationMessage build(Notification notification, Student student) {
        final String recipient = recipientForChannel(student, notification.getChannel());

        final Map<String, String> variables = new HashMap<>(notification.getVariables());
        variables.putIfAbsent("studentName", student.getFullName());
        variables.putIfAbsent("studentId", student.getId().toString());

        return NotificationMessage.builder()
                .notificationId(notification.getId())
                .studentId(notification.getStudentId())
                .channel(notification.getChannel())
                .type(notification.getType())
                .recipient(recipient)
                .recipientName(student.getFullName())
                .templateKey(notification.getTemplateKey())
                .variables(variables)
                .subject(notification.getRenderedSubject())
                .body(notification.getRenderedBody())
                .locale(student.getLocale())
                .build();
    }

    private String recipientForChannel(Student student, NotificationChannel channel) {
        return switch (channel) {
            case EMAIL -> student.getEmail();
            case SMS -> student.getPhoneE164();
            case WHATSAPP -> student.getEffectiveWhatsappNumber();
            case NONE -> null;
        };
    }
}
