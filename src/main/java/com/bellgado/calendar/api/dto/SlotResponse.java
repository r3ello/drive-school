package com.bellgado.calendar.api.dto;

import com.bellgado.calendar.domain.entity.Slot;
import com.bellgado.calendar.domain.enums.SlotStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public record SlotResponse(
        UUID id,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        SlotStatus status,
        StudentBrief student,
        String notes,
        int version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static SlotResponse from(Slot slot) {
        return new SlotResponse(
                slot.getId(),
                slot.getStartAt(),
                slot.getEndAt(),
                slot.getStatus(),
                StudentBrief.from(slot.getStudent()),
                slot.getNotes(),
                slot.getVersion(),
                slot.getCreatedAt(),
                slot.getUpdatedAt()
        );
    }
}
