package ru.backend.product.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "preapproved_offer")
@Getter
@Setter
@NoArgsConstructor
public class PreapprovedOffer {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID companyId;

    @Column(nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private BigDecimal maxAmount;

    @Column(nullable = false)
    private BigDecimal rateFrom;

    @Column(nullable = false)
    private Instant validUntil;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> reason;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
