package com.bellgado.calendar.api.dto;

import com.bellgado.calendar.domain.enums.NotificationChannel;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

public record StudentCreateRequest(
        @NotBlank
        @Size(min = 1, max = 200)
        String fullName,

        @Size(max = 50)
        String phone,

        @Email
        @Size(max = 200)
        String email,

        @Size(max = 2000)
        String notes,

        // Notification preferences (all optional on create)
        NotificationChannel preferredNotificationChannel,

        Boolean notificationOptIn,

        @Pattern(regexp = "^\\+[1-9]\\d{1,14}$", message = "Phone must be in E.164 format (e.g., +12025551234)")
        @Size(max = 20)
        String phoneE164,

        @Pattern(regexp = "^\\+[1-9]\\d{1,14}$", message = "WhatsApp number must be in E.164 format")
        @Size(max = 20)
        String whatsappNumberE164,

        @Size(max = 50)
        String timezone,

        @Size(max = 10)
        String locale,

        LocalTime quietHoursStart,

        LocalTime quietHoursEnd
) {}
