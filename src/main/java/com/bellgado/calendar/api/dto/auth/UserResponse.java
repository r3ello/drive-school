package com.bellgado.calendar.api.dto.auth;

import com.bellgado.calendar.domain.entity.User;
import com.bellgado.calendar.domain.enums.UserRole;
import com.bellgado.calendar.domain.enums.UserStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String fullName,
        UserRole role,
        UserStatus status,
        UUID studentId,
        boolean passwordChangeRequired,
        OffsetDateTime createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getStatus(),
                user.getStudentId(),
                user.isPasswordChangeRequired(),
                user.getCreatedAt()
        );
    }
}
