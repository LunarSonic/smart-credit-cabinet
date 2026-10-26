package ru.backend.application.entity.enums;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Внутренние статусы заявки + допустимые переходы (state machine).
 *
 * CANCELLED и OFFER_EXPIRED — расширение спецификации (не описаны в контракте команды,
 * но необходимы для отзыва клиентом и истечения срока оферты).
 */
public enum ApplicationStatus {

    // ─── основные (по спецификации) ────────────────────────────────────────────
    DRAFT              ("Черновик",                    "Черновик",                 false),
    SUBMITTED          ("Отправлена",                  "Отправлена",               false),
    COMPLETENESS_CHECK ("Проверка полноты данных",     "Проверка данных",          false),
    NEEDS_INFO         ("Требуются уточнения",         "Нужны уточнения",          true),
    DATA_CHECK         ("Проверка данных",             "Проверка данных",          false),
    SCORING            ("Оценка риска",                "Проверка данных",          false),
    MANUAL_REVIEW      ("На рассмотрении у сотрудника","На рассмотрении",          false),
    APPROVED           ("Одобрено",                   "Предложены условия",       true),
    REJECTED           ("Отказано",                   "Отказано",                 false),
    OFFER_DECLINED     ("Условия отклонены клиентом", "Условия отклонены",        false),
    SIGNED             ("Договор подписан",            "Выдано",                   false),
    DISBURSED          ("Выдано",                     "Выдано",                   false),

    // ─── расширение спецификации ────────────────────────────────────────────────
    /** Заявка отозвана клиентом из DRAFT / SUBMITTED / NEEDS_INFO / MANUAL_REVIEW / APPROVED. */
    CANCELLED          ("Заявка отозвана",            "Заявка отозвана",          false),
    /** Клиент не принял оферту до истечения срока. */
    OFFER_EXPIRED      ("Срок предложения истёк",     "Срок предложения истёк",   false);

    // ─────────────────────────────────────────────────────────────────────────────

    private static final Map<ApplicationStatus, Set<ApplicationStatus>> ALLOWED =
            new EnumMap<>(ApplicationStatus.class);

    static {
        ALLOWED.put(DRAFT,              Set.of(SUBMITTED, CANCELLED));
        ALLOWED.put(SUBMITTED,          Set.of(COMPLETENESS_CHECK, CANCELLED));
        ALLOWED.put(COMPLETENESS_CHECK, Set.of(NEEDS_INFO, DATA_CHECK));
        ALLOWED.put(NEEDS_INFO,         Set.of(DATA_CHECK, CANCELLED));
        ALLOWED.put(DATA_CHECK,         Set.of(SCORING, MANUAL_REVIEW, REJECTED));
        ALLOWED.put(SCORING,            Set.of(APPROVED, REJECTED, MANUAL_REVIEW, NEEDS_INFO));
        ALLOWED.put(MANUAL_REVIEW,      Set.of(APPROVED, REJECTED, NEEDS_INFO, CANCELLED));
        ALLOWED.put(APPROVED,           Set.of(SIGNED, OFFER_DECLINED, OFFER_EXPIRED, CANCELLED));
        ALLOWED.put(OFFER_DECLINED,     Set.of());
        ALLOWED.put(OFFER_EXPIRED,      Set.of());
        ALLOWED.put(REJECTED,           Set.of());
        ALLOWED.put(CANCELLED,          Set.of());
        ALLOWED.put(SIGNED,             Set.of(DISBURSED));
        ALLOWED.put(DISBURSED,          Set.of());
    }

    /** Лейбл для сотрудников / истории (отражает конкретный шаг). */
    private final String internalLabel;
    /** Лейбл для клиента (несколько шагов могут группироваться в одно состояние). */
    private final String clientStatus;
    /** true — клиент должен совершить действие. */
    private final boolean actionRequired;

    ApplicationStatus(String internalLabel, String clientStatus, boolean actionRequired) {
        this.internalLabel   = internalLabel;
        this.clientStatus    = clientStatus;
        this.actionRequired  = actionRequired;
    }

    /** Человекочитаемое название статуса для сотрудников / истории. */
    public String clientLabel()     { return internalLabel; }

    /** Название, которое видит клиент в кабинете (может группировать несколько внутренних статусов). */
    public String clientStatus()    { return clientStatus; }

    /** Нужно ли клиенту совершить действие (принять/отклонить оферту, дополнить данные и т.п.). */
    public boolean actionRequired() { return actionRequired; }

    public boolean canGoTo(ApplicationStatus target) {
        return ALLOWED.get(this).contains(target);
    }

    public Set<ApplicationStatus> allowedTargets() { return ALLOWED.get(this); }

    public boolean isTerminal() { return ALLOWED.get(this).isEmpty(); }
}
