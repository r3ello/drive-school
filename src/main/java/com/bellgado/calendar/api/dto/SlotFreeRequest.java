package com.bellgado.calendar.api.dto;

import jakarta.validation.constraints.Size;

public record SlotFreeRequest(
        @Size(max = 2000)
        String notes
) {}
