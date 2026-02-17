package com.bellgado.calendar.api.dto;

import com.bellgado.calendar.domain.enums.CancelledBy;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SlotCancelRequest(
        @NotNull
        CancelledBy cancelledBy,

        @Size(max = 500)
        String reason
) {}
