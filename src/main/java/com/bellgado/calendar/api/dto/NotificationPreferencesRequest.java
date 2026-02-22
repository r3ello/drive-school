package com.bellgado.calendar.api.dto;

import com.bellgado.calendar.domain.enums.NotificationChannel;

import java.time.LocalTime;

/**
 * Request for updating a student's notification preferences only.
 */
public record NotificationPreferencesRequest(
        NotificationChannel preferredChannel,
        Boolean optIn,
        String phoneE164,
        String whatsappNumberE164,
        String timezone,
        String locale,
        LocalTime quietHoursStart,
        LocalTime quietHoursEnd
) {}
