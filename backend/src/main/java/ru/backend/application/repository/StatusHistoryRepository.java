package ru.backend.application.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.backend.application.entity.ApplicationStatusHistory;

import java.util.List;
import java.util.UUID;

public interface StatusHistoryRepository extends JpaRepository<ApplicationStatusHistory, Long> {
    List<ApplicationStatusHistory> findByApplicationIdOrderByIdAsc(UUID applicationId);
}
