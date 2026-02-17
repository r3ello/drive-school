package com.bellgado.calendar.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "blocks")
@Getter
@Setter
@NoArgsConstructor
public class Block {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "from_at", nullable = false)
    private OffsetDateTime from;

    @Column(name = "to_at", nullable = false)
    private OffsetDateTime to;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    public Block(OffsetDateTime from, OffsetDateTime to, String reason) {
        this.from = from;
        this.to = to;
        this.reason = reason;
    }
}
