package com.bellgado.calendar.api.dto;

import com.bellgado.calendar.domain.entity.WaitlistItem;
import com.bellgado.calendar.domain.enums.DayOfWeek;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public record WaitlistResponse(
        UUID id,
        StudentBrief student,
        List<DayOfWeek> preferredDays,
        List<TimeRange> preferredTimeRanges,
        String notes,
        int priority,
        boolean active,
        OffsetDateTime createdAt
) {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static WaitlistResponse from(WaitlistItem item) {
        List<DayOfWeek> days = null;
        if (item.getPreferredDays() != null && !item.getPreferredDays().isEmpty()) {
            days = Arrays.stream(item.getPreferredDays().split(","))
                    .map(String::trim)
                    .map(DayOfWeek::valueOf)
                    .toList();
        }

        List<TimeRange> ranges = null;
        if (item.getPreferredTimeRanges() != null && !item.getPreferredTimeRanges().isEmpty()) {
            try {
                ranges = objectMapper.readValue(item.getPreferredTimeRanges(), new TypeReference<>() {});
            } catch (JsonProcessingException e) {
                ranges = List.of();
            }
        }

        return new WaitlistResponse(
                item.getId(),
                StudentBrief.from(item.getStudent()),
                days,
                ranges,
                item.getNotes(),
                item.getPriority(),
                item.isActive(),
                item.getCreatedAt()
        );
    }
}
