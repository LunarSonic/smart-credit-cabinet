package ru.backend.application.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.backend.application.entity.ApplicationIssue;

import java.util.List;
import java.util.UUID;

public interface ApplicationIssueRepository extends JpaRepository<ApplicationIssue, UUID> {
    List<ApplicationIssue> findByApplicationIdOrderByCreatedAtAsc(UUID applicationId);
    List<ApplicationIssue> findByApplicationIdAndResolvedFalse(UUID applicationId);
}
