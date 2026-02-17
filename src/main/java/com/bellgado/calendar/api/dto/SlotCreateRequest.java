package com.bellgado.calendar.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

public record SlotCreateRequest(
        @NotNull
        OffsetDateTime startAt,

        @Min(60)
        @Max(60)
        Integer durationMinutes
) {
    public SlotCreateRequest {
        if (durationMinutes == null) {
            durationMinutes = 60;
        }
    }
}
