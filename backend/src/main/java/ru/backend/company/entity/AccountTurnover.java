package ru.backend.company.entity;

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
@Table(name = "account_turnover")
@Getter
@Setter
@NoArgsConstructor
public class AccountTurnover {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID companyId;

    @Column(nullable = false)
    private LocalDate month;

    @Column(nullable = false)
    private BigDecimal turnoverAmount;

    @Column(nullable = false)
    private BigDecimal avgBalance;
}
