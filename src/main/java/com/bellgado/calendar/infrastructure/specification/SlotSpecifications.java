package com.bellgado.calendar.infrastructure.specification;

import com.bellgado.calendar.domain.entity.Slot;
import com.bellgado.calendar.domain.enums.SlotStatus;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.UUID;

public final class SlotSpecifications {

    private SlotSpecifications() {}

    public static Specification<Slot> startAtBetween(OffsetDateTime from, OffsetDateTime to) {
        return (root, criteriaQuery, cb) ->
            cb.and(
                cb.greaterThanOrEqualTo(root.get("startAt"), from),
                cb.lessThan(root.get("startAt"), to)
            );
    }

    public static Specification<Slot> withStatuses(Collection<SlotStatus> statuses) {
        return (root, criteriaQuery, cb) -> {
            if (statuses == null || statuses.isEmpty()) {
                return cb.conjunction();
            }
            return root.get("status").in(statuses);
        };
    }

    public static Specification<Slot> withStudentId(UUID studentId) {
        return (root, criteriaQuery, cb) ->
            cb.equal(root.get("student").get("id"), studentId);
    }

    public static Specification<Slot> excludeStatus(SlotStatus status) {
        return (root, criteriaQuery, cb) ->
            cb.notEqual(root.get("status"), status);
    }

    public static Specification<Slot> fetchStudent() {
        return (root, criteriaQuery, cb) -> {
            if (criteriaQuery.getResultType() != Long.class && criteriaQuery.getResultType() != long.class) {
                root.fetch("student", JoinType.LEFT);
            }
            return cb.conjunction();
        };
    }

    public static Specification<Slot> inDateRangeWithStatuses(OffsetDateTime from, OffsetDateTime to, Collection<SlotStatus> statuses) {
        return Specification.where(fetchStudent())
                .and(startAtBetween(from, to))
                .and(withStatuses(statuses));
    }

    public static Specification<Slot> forStudentInDateRange(UUID studentId, OffsetDateTime from, OffsetDateTime to, Collection<SlotStatus> statuses) {
        return Specification.where(fetchStudent())
                .and(withStudentId(studentId))
                .and(startAtBetween(from, to))
                .and(withStatuses(statuses));
    }

    public static Specification<Slot> inRangeExcludingBooked(OffsetDateTime from, OffsetDateTime to) {
        return Specification.where(startAtBetween(from, to))
                .and(excludeStatus(SlotStatus.BOOKED));
    }
}
