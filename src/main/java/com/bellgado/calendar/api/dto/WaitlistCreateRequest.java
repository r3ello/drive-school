package com.bellgado.calendar.api.dto;

import com.bellgado.calendar.domain.enums.DayOfWeek;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record WaitlistCreateRequest(
        @NotNull
        UUID studentId,

        List<DayOfWeek> preferredDays,

        List<TimeRange> preferredTimeRanges,

        @Size(max = 2000)
        String notes,

        Integer priority
) {
    public WaitlistCreateRequest {
        if (priority == null) {
            priority = 0;
        }
    }
}
