package ru.backend.application.check;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "mock_bki")
@Getter
@Setter
@NoArgsConstructor
public class MockBkiRecord {
    @Id
    private String inn;

    @Column(nullable = false)
    private boolean available;

    /** Историческая просрочка (когда-либо была) */
    @Column(nullable = false)
    private boolean hasOverdue;

    /** Активная просрочка прямо сейчас — стоп-фактор для автоодобрения */
    @Column(nullable = false)
    private boolean currentOverdue;

    /** Количество просроченных платежей за историю */
    @Column(nullable = false)
    private int overdueCount;

    /** Максимальное число дней просрочки когда-либо */
    @Column(nullable = false)
    private int maxOverdueDays;

    @Column(nullable = false)
    private int activeLoans;

    @Column(nullable = false)
    private BigDecimal totalDebt;

    @Column(nullable = false)
    private BigDecimal monthlyPayment;
}
