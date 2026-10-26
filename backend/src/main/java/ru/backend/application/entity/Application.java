package ru.backend.application.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.backend.application.entity.enums.ApplicationStatus;
import ru.backend.product.entity.enums.Purpose;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "application")
@Getter
@Setter
@NoArgsConstructor
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID companyId;

    @Column(nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private UUID createdBy;

    private BigDecimal amount;
    private Integer termMonths;

    @Enumerated(EnumType.STRING)
    private Purpose purpose;

    /** ФИО представителя (ИП или директора). */
    private String fullName;

    /** Контактный телефон. */
    private String phone;

    /** Выручка за последние 6 месяцев (одна задекларированная сумма). */
    @Column(name = "revenue_6m")
    private BigDecimal revenue6m;

    /** Обязательные расходы в месяц (аренда, налоги, ФОТ и т.п.). */
    private BigDecimal monthlyExpenses;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status = ApplicationStatus.DRAFT;

    /** ID последнего решения из ML-сервиса (decision_id из ответа /decide). */
    private String currentDecisionRef;

    /** Ссылка на предодобренное предложение, с которого началась заявка (если было). */
    private UUID preapprovedOfferId;

    @Version
    private Long version;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant submittedAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
