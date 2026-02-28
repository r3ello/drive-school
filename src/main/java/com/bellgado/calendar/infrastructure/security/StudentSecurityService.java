package com.bellgado.calendar.infrastructure.security;

import com.bellgado.calendar.domain.enums.UserRole;
import com.bellgado.calendar.infrastructure.repository.SlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Spring bean used in SpEL @PreAuthorize expressions to enforce STUDENT-level
 * data ownership checks. Keeps controller annotations readable.
 */
@Service("studentSecurityService")
@RequiredArgsConstructor
public class StudentSecurityService {

    private final SlotRepository slotRepository;

    /**
     * Returns true if the authenticated user is accessing their own student record.
     * Always returns true for TEACHER/ADMIN (they may access any student).
     */
    public boolean isOwnStudent(Authentication auth, UUID studentId) {
        JwtAuthenticationToken jwt = cast(auth);
        if (jwt == null) return false;
        if (jwt.getRole() != UserRole.STUDENT) return true;
        return studentId.equals(jwt.getStudentId());
    }

    /**
     * Returns true if the authenticated STUDENT owns the slot (is booked into it).
     * Always returns true for TEACHER/ADMIN.
     */
    public boolean ownsSlot(Authentication auth, UUID slotId) {
        JwtAuthenticationToken jwt = cast(auth);
        if (jwt == null) return false;
        if (jwt.getRole() != UserRole.STUDENT) return true;
        UUID studentId = jwt.getStudentId();
        if (studentId == null) return false;
        return slotRepository.findByIdWithStudent(slotId)
                .map(slot -> slot.getStudent() != null && studentId.equals(slot.getStudent().getId()))
                .orElse(false);
    }

    private JwtAuthenticationToken cast(Authentication auth) {
        if (auth instanceof JwtAuthenticationToken jat) return jat;
        return null;
    }
}
