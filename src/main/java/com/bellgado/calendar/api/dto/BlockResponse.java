package com.bellgado.calendar.api.dto;

import com.bellgado.calendar.domain.entity.Block;
import java.time.OffsetDateTime;
import java.util.UUID;

public record BlockResponse(
        UUID id,
        OffsetDateTime from,
        OffsetDateTime to,
        String reason,
        OffsetDateTime createdAt
) {
    public static BlockResponse from(Block block) {
        return new BlockResponse(
                block.getId(),
                block.getFrom(),
                block.getTo(),
                block.getReason(),
                block.getCreatedAt()
        );
    }
}
