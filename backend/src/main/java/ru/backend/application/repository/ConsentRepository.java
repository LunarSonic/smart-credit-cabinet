package ru.backend.application.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.backend.application.entity.Consent;
import ru.backend.application.entity.enums.ConsentType;

import java.util.List;
import java.util.UUID;

public interface ConsentRepository extends JpaRepository<Consent, UUID> {
    List<Consent> findByApplicationId(UUID applicationId);
    boolean existsByApplicationIdAndType(UUID applicationId, ConsentType type);
}
