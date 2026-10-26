package ru.backend.bank.entity;

import ru.backend.bank.entity.enums.ManualAction;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "manual_decision")
@Getter
@Setter
@NoArgsConstructor
public class ManualDecision {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID applicationId;

    @Column(nullable = false)
    private UUID employeeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false) private ManualAction action;
    private String comment;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
