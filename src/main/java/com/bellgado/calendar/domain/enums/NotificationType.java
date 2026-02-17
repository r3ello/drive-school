package com.bellgado.calendar.domain.enums;

/**
 * Types of notifications that can be sent to students.
 * Each type corresponds to a specific event or action in the system.
 */
public enum NotificationType {
    /**
     * Notification when a new class is scheduled/booked for a student.
     */
    CLASS_SCHEDULED,

    /**
     * Notification when a scheduled class is cancelled.
     */
    CLASS_CANCELLED,

    /**
     * Notification when a class is rescheduled to a different time.
     */
    CLASS_RESCHEDULED,

    /**
     * Reminder notification sent before an upcoming class.
     */
    CLASS_REMINDER,

    /**
     * Notification when a slot becomes available for a student on the waitlist.
     */
    WAITLIST_AVAILABLE,

    /**
     * Custom/manual notification sent by the administrator.
     */
    CUSTOM;

    /**
     * Returns the default template key for this notification type.
     */
    public String getDefaultTemplateKey() {
        return this.name();
    }
}
