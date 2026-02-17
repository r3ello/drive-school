package com.bellgado.calendar.api.dto;

import com.bellgado.calendar.domain.entity.Student;
import java.util.UUID;

public record StudentBrief(
        UUID id,
        String fullName
) {
    public static StudentBrief from(Student student) {
        if (student == null) {
            return null;
        }
        return new StudentBrief(student.getId(), student.getFullName());
    }
}
