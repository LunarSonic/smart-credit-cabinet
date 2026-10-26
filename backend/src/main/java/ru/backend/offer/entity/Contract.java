package ru.backend.offer.entity;

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
@Table(name = "contract")
@Getter
@Setter
@NoArgsConstructor
public class Contract {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID offerId;

    @Column(nullable = false)
    private Instant signedAt;

    @Column(nullable = false)
    private UUID signedBy;

    private Instant disbursedAt;

    @Column(nullable = false)
    private boolean simulated = true;
}
