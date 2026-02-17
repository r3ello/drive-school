package com.bellgado.calendar.api.dto;

import java.util.List;

public record BlockListResponse(
        List<BlockResponse> content
) {}
