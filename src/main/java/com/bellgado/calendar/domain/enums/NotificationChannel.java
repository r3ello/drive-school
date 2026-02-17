package com.bellgado.calendar.domain.enums;

/**
 * Supported notification delivery channels.
 * NONE indicates that the student has opted out of notifications.
 */
public enum NotificationChannel {
    NONE,
    EMAIL,
    SMS,
    WHATSAPP;

    /**
     * Checks if this channel can actually deliver notifications.
     * NONE is not a real delivery channel.
     */
    public boolean isDeliverable() {
        return this != NONE;
    }
}
