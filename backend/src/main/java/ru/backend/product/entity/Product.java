package ru.backend.product.entity;

import ru.backend.product.entity.enums.Purpose;

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
@Table(name = "product")
@Getter
@Setter
@NoArgsConstructor
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Purpose suitablePurpose;

    @Column(nullable = false)
    private BigDecimal minAmount;

    @Column(nullable = false)
    private BigDecimal maxAmount;

    @Column(nullable = false)
    private int minTermMonths;

    @Column(nullable = false)
    private int maxTermMonths;

    @Column(nullable = false)
    private BigDecimal baseRate;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private boolean preapprovable;
}
