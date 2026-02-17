package com.bellgado.calendar.infrastructure.specification;

import com.bellgado.calendar.domain.entity.SlotEvent;
import com.bellgado.calendar.domain.enums.EventType;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.util.Collection;

public final class SlotEventSpecifications {

    private SlotEventSpecifications() {}

    public static Specification<SlotEvent> atBetween(OffsetDateTime from, OffsetDateTime to) {
        return (root, criteriaQuery, cb) ->
            cb.and(
                cb.greaterThanOrEqualTo(root.get("at"), from),
                cb.lessThan(root.get("at"), to)
            );
    }

    public static Specification<SlotEvent> withTypes(Collection<EventType> types) {
        return (root, criteriaQuery, cb) -> {
            if (types == null || types.isEmpty()) {
                return cb.conjunction();
            }
            return root.get("type").in(types);
        };
    }

    public static Specification<SlotEvent> inDateRangeWithTypes(OffsetDateTime from, OffsetDateTime to, Collection<EventType> types) {
        return Specification.where(atBetween(from, to)).and(withTypes(types));
    }
}
