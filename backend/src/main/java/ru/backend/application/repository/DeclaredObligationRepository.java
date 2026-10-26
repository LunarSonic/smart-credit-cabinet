package ru.backend.application.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.backend.application.entity.DeclaredObligation;

import java.util.List;
import java.util.UUID;

public interface DeclaredObligationRepository extends JpaRepository<DeclaredObligation, UUID> {
    List<DeclaredObligation> findByApplicationId(UUID applicationId);
    void deleteByApplicationId(UUID applicationId);
}
