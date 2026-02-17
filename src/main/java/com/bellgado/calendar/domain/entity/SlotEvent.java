package com.bellgado.calendar.domain.entity;

import com.bellgado.calendar.domain.enums.EventType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "slot_events")
@Getter
@Setter
@NoArgsConstructor
public class SlotEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "slot_id", nullable = false)
    private UUID slotId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private EventType type;

    @Column(name = "at", nullable = false)
    private OffsetDateTime at;

    @Column(name = "old_student_id")
    private UUID oldStudentId;

    @Column(name = "new_student_id")
    private UUID newStudentId;

    @Column(name = "meta", columnDefinition = "TEXT")
    private String meta;

    @PrePersist
    protected void onCreate() {
        if (at == null) {
            at = OffsetDateTime.now();
        }
    }

    public SlotEvent(UUID slotId, EventType type) {
        this.slotId = slotId;
        this.type = type;
        this.at = OffsetDateTime.now();
    }
}
