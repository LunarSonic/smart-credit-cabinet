package ru.backend.application.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.backend.application.entity.Application;
import ru.backend.application.entity.enums.ApplicationStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {
    List<Application> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);
    Page<Application> findByStatus(ApplicationStatus status, Pageable pageable);
    List<Application> findByStatusInAndUpdatedAtBefore(Collection<ApplicationStatus> statuses, Instant before);
}