package com.bellgado.calendar.infrastructure.repository;

import com.bellgado.calendar.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    Optional<User> findByConfirmationToken(String confirmationToken);

    Optional<User> findByStudentId(UUID studentId);

    List<User> findByStudentIdIn(Collection<UUID> studentIds);

    boolean existsByEmail(String email);

    boolean existsByStudentId(UUID studentId);
}
