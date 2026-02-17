package com.bellgado.calendar.domain.entity;

import com.bellgado.calendar.domain.enums.NotificationChannel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "students")
@Getter
@Setter
@NoArgsConstructor
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    // Original phone field (display format, kept for backward compatibility)
    @Column(name = "phone", length = 50)
    private String phone;

    @Column(name = "email", length = 200)
    private String email;

    @Column(name = "notes", length = 2000)
    private String notes;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    // ============================================================================
    // NOTIFICATION PREFERENCES
    // ============================================================================

    /**
     * Preferred channel for receiving notifications.
     * NONE means the student does not want notifications.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_notification_channel", nullable = false, length = 20)
    private NotificationChannel preferredNotificationChannel = NotificationChannel.NONE;

    /**
     * Whether the student has explicitly opted in to receive notifications.
     * Must be true for notifications to be sent.
     */
    @Column(name = "notification_opt_in", nullable = false)
    private boolean notificationOptIn = false;

    /**
     * Timestamp when the student opted in (for compliance/audit purposes).
     */
    @Column(name = "notification_opt_in_at")
    private OffsetDateTime notificationOptInAt;

    // ============================================================================
    // NORMALIZED CONTACT POINTS
    // ============================================================================

    /**
     * Phone number in E.164 format (e.g., +12025551234).
     * Used for SMS notifications.
     */
    @Column(name = "phone_e164", length = 20)
    private String phoneE164;

    /**
     * WhatsApp number in E.164 format.
     * If null, phoneE164 is used as fallback for WhatsApp.
     */
    @Column(name = "whatsapp_number_e164", length = 20)
    private String whatsappNumberE164;

    // ============================================================================
    // USER PREFERENCES
    // ============================================================================

    /**
     * Student's timezone for scheduling notifications (IANA timezone ID).
     */
    @Column(name = "timezone", length = 50)
    private String timezone = "UTC";

    /**
     * Student's preferred locale for notification content.
     */
    @Column(name = "locale", length = 10)
    private String locale = "en";

    /**
     * Start of quiet hours (no notifications during this period).
     */
    @Column(name = "quiet_hours_start")
    private LocalTime quietHoursStart;

    /**
     * End of quiet hours.
     */
    @Column(name = "quiet_hours_end")
    private LocalTime quietHoursEnd;

    // ============================================================================
    // AUDIT TIMESTAMPS
    // ============================================================================

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // ============================================================================
    // CONSTRUCTORS
    // ============================================================================

    public Student(String fullName, String phone, String email, String notes) {
        this.fullName = fullName;
        this.phone = phone;
        this.email = email;
        this.notes = notes;
        this.active = true;
    }

    // ============================================================================
    // NOTIFICATION HELPER METHODS
    // ============================================================================

    /**
     * Checks if the student can receive notifications on any channel.
     */
    public boolean canReceiveNotifications() {
        return active && notificationOptIn && preferredNotificationChannel.isDeliverable();
    }

    /**
     * Checks if the student can receive notifications on a specific channel.
     */
    public boolean canReceiveNotificationsOn(NotificationChannel channel) {
        if (!canReceiveNotifications()) {
            return false;
        }
        return switch (channel) {
            case EMAIL -> email != null && !email.isBlank();
            case SMS -> phoneE164 != null && !phoneE164.isBlank();
            case WHATSAPP -> getEffectiveWhatsappNumber() != null;
            case NONE -> false;
        };
    }

    /**
     * Gets the effective WhatsApp number (dedicated or fallback to phone).
     */
    public String getEffectiveWhatsappNumber() {
        if (whatsappNumberE164 != null && !whatsappNumberE164.isBlank()) {
            return whatsappNumberE164;
        }
        return phoneE164;
    }

    /**
     * Sets opt-in status and records the timestamp if opting in.
     */
    public void setNotificationOptIn(boolean optIn) {
        this.notificationOptIn = optIn;
        if (optIn && this.notificationOptInAt == null) {
            this.notificationOptInAt = OffsetDateTime.now();
        }
    }
}
