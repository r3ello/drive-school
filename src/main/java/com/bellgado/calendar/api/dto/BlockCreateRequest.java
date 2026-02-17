package com.bellgado.calendar.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public record BlockCreateRequest(
        @NotNull
        OffsetDateTime from,

        @NotNull
        OffsetDateTime to,

        @Size(max = 500)
        String reason
) {}
