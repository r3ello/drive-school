package com.bellgado.calendar.api.dto;

import com.bellgado.calendar.domain.enums.DayOfWeek;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record WeeklyRule(
        @NotNull
        DayOfWeek dayOfWeek,

        @NotNull
        @Pattern(regexp = "^([01]?[0-9]|2[0-3]):[0-5][0-9]$", message = "Invalid time format, expected HH:mm")
        String startTime,

        @NotNull
        @Pattern(regexp = "^([01]?[0-9]|2[0-3]):[0-5][0-9]$", message = "Invalid time format, expected HH:mm")
        String endTime
) {}
