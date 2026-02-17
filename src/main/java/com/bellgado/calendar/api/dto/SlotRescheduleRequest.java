package com.bellgado.calendar.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SlotRescheduleRequest(
        @NotNull
        UUID targetSlotId,

        @Size(max = 500)
        String reason
) {}
