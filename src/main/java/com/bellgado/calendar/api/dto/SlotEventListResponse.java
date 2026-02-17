package com.bellgado.calendar.api.dto;

import java.util.List;

public record SlotEventListResponse(
        List<SlotEventResponse> content
) {}
