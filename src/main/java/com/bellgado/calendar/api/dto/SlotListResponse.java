package com.bellgado.calendar.api.dto;

import java.util.List;

public record SlotListResponse(
        List<SlotResponse> content
) {}
