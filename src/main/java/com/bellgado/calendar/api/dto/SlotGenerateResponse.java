package com.bellgado.calendar.api.dto;

public record SlotGenerateResponse(
        int createdCount,
        int skippedCount
) {}
