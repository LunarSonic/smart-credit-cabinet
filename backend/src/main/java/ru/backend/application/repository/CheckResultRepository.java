package ru.backend.application.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.backend.application.entity.CheckResult;
import ru.backend.application.entity.enums.CheckType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CheckResultRepository extends JpaRepository<CheckResult, UUID> {
    List<CheckResult> findByApplicationIdOrderByCheckedAtAsc(UUID applicationId);
    Optional<CheckResult> findFirstByApplicationIdAndCheckTypeOrderByCheckedAtDesc(UUID applicationId, CheckType type);
}

