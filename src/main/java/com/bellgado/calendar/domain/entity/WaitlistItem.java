package com.bellgado.calendar.domain.entity;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "waitlist_items")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class WaitlistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "preferred_days", length = 200)
    private String preferredDays;

    @Column(name = "preferred_time_ranges", length = 500)
    private String preferredTimeRanges;

    @Column(name = "notes", length = 2000)
    private String notes;

    @Column(name = "priority", nullable = false)
    private int priority = 0;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    public WaitlistItem(Student student, String preferredDays, String preferredTimeRanges, String notes, int priority) {
        this.student = student;
        this.preferredDays = preferredDays;
        this.preferredTimeRanges = preferredTimeRanges;
        this.notes = notes;
        this.priority = priority;
        this.active = true;
    }
}
