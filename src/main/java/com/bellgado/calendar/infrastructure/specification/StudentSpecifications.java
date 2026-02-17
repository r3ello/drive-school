package com.bellgado.calendar.infrastructure.specification;

import com.bellgado.calendar.domain.entity.Student;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public final class StudentSpecifications {

    private StudentSpecifications() {}

    public static Specification<Student> withQuery(String query) {
        return (root, criteriaQuery, cb) -> {
            if (!StringUtils.hasText(query)) {
                return cb.conjunction();
            }
            String pattern = "%" + query.toLowerCase() + "%";
            return cb.or(
                cb.like(cb.lower(root.get("fullName")), pattern),
                cb.like(cb.lower(cb.coalesce(root.get("email"), "")), pattern),
                cb.like(cb.lower(cb.coalesce(root.get("phone"), "")), pattern)
            );
        };
    }

    public static Specification<Student> withActive(Boolean active) {
        return (root, criteriaQuery, cb) -> {
            if (active == null) {
                return cb.conjunction();
            }
            return cb.equal(root.get("active"), active);
        };
    }

    public static Specification<Student> search(String query, Boolean active) {
        return Specification.where(withQuery(query)).and(withActive(active));
    }
}
