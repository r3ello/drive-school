package com.bellgado.calendar.infrastructure.specification;

import com.bellgado.calendar.domain.entity.WaitlistItem;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

public final class WaitlistSpecifications {

    private WaitlistSpecifications() {}

    public static Specification<WaitlistItem> withActive(Boolean active) {
        return (root, criteriaQuery, cb) -> {
            if (active == null) {
                return cb.conjunction();
            }
            return cb.equal(root.get("active"), active);
        };
    }

    public static Specification<WaitlistItem> fetchStudent() {
        return (root, criteriaQuery, cb) -> {
            if (criteriaQuery.getResultType() != Long.class && criteriaQuery.getResultType() != long.class) {
                root.fetch("student", JoinType.LEFT);
            }
            return cb.conjunction();
        };
    }

    public static Specification<WaitlistItem> activeWithStudent(Boolean active) {
        return Specification.where(fetchStudent()).and(withActive(active));
    }
}
