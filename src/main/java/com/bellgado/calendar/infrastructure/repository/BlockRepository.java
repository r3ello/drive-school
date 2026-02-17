package com.bellgado.calendar.infrastructure.repository;

import com.bellgado.calendar.domain.entity.Block;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface BlockRepository extends JpaRepository<Block, UUID>, JpaSpecificationExecutor<Block> {
}
