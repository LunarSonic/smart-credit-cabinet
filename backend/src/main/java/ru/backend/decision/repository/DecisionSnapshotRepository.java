package ru.backend.decision.repository;

import ru.backend.decision.entity.DecisionSnapshot;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DecisionSnapshotRepository extends JpaRepository<DecisionSnapshot, UUID> {
    Optional<DecisionSnapshot> findFirstByApplicationIdOrderByCreatedAtDesc(UUID applicationId);
}
