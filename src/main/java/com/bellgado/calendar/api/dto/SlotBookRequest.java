package com.bellgado.calendar.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SlotBookRequest(
        @NotNull
        UUID studentId,

        @Size(max = 2000)
        String notes
) {}
