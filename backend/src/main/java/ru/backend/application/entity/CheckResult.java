package ru.backend.application.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import ru.backend.application.entity.enums.CheckStatus;
import ru.backend.application.entity.enums.CheckType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "check_result")
@Getter
@Setter
@NoArgsConstructor
@Immutable
public class CheckResult {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID applicationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CheckType checkType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CheckStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Column(nullable = false)
    private Instant checkedAt = Instant.now();
}