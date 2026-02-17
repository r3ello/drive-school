package com.bellgado.calendar.infrastructure.specification;

import com.bellgado.calendar.domain.entity.Block;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;

public final class BlockSpecifications {

    private BlockSpecifications() {}

    public static Specification<Block> overlapping(OffsetDateTime from, OffsetDateTime to) {
        return (root, criteriaQuery, cb) ->
            cb.and(
                cb.lessThan(root.get("from"), to),
                cb.greaterThan(root.get("to"), from)
            );
    }
}
