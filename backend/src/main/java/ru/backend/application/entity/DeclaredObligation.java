package ru.backend.application.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "declared_obligation")
@Getter
@Setter
@NoArgsConstructor
public class DeclaredObligation {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID applicationId;

    @Column(nullable = false)
    private String creditorName;

    @Column(nullable = false)
    private BigDecimal monthlyPayment;

    private BigDecimal remainingDebt;
}