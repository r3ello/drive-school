package com.bellgado.calendar.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SlotReplaceRequest(
        @NotNull
        UUID newStudentId,

        @Size(max = 500)
        String reason
) {}
