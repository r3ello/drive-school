package com.bellgado.calendar.api.dto;

public record RescheduleResponse(
        SlotResponse originSlot,
        SlotResponse targetSlot
) {}
