package ru.backend.bank.repository;

import ru.backend.bank.entity.ManualDecision;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ManualDecisionRepository extends JpaRepository<ManualDecision, UUID> {
    List<ManualDecision> findByApplicationIdOrderByCreatedAtAsc(UUID applicationId);
}
