package ru.backend.offer.entity;

import ru.backend.offer.entity.enums.OfferStatus;

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
@Table(name = "offer")
@Getter
@Setter
@NoArgsConstructor
public class Offer {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID applicationId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private int termMonths;

    @Column(nullable = false)
    private BigDecimal rate;

    @Column(nullable = false)
    private boolean termsChanged;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb") private Map<String, Object> changes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false) private OfferStatus status = OfferStatus.ACTIVE;

    @Column(nullable = false)
    private Instant validUntil;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
