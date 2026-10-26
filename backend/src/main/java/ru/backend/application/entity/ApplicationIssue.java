package ru.backend.application.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.backend.application.entity.enums.IssueSource;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "application_issue")
@Getter
@Setter
@NoArgsConstructor
public class ApplicationIssue {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID applicationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IssueSource source;

    private String field;

    @Column(nullable = false)
    private String message;
    private String reason;

    @Column(nullable = false)
    private boolean resolved;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant resolvedAt;
}
