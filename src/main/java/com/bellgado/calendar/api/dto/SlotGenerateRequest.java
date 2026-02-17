package com.bellgado.calendar.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

public record SlotGenerateRequest(
        @NotNull
        LocalDate from,

        @NotNull
        LocalDate to,

        @NotBlank
        String timezone,

        @NotNull
        @Size(min = 1)
        @Valid
        List<WeeklyRule> weeklyRules,

        @Min(60)
        @Max(60)
        Integer slotDurationMinutes
) {
    public SlotGenerateRequest {
        if (slotDurationMinutes == null) {
            slotDurationMinutes = 60;
        }
    }
}
