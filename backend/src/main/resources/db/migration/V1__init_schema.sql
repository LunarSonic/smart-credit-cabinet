-- =============================================================================
-- V1  Схема core: все таблицы кредитного конвейера
-- =============================================================================

-- ─── Справочник продуктов ─────────────────────────────────────────────────────
CREATE TABLE product (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code             VARCHAR(50)     NOT NULL UNIQUE,
    name             VARCHAR(255)    NOT NULL,
    description      TEXT,
    suitable_purpose VARCHAR(50)     NOT NULL,   -- Purpose enum
    min_amount       NUMERIC(18,2)   NOT NULL,
    max_amount       NUMERIC(18,2)   NOT NULL,
    min_term_months  INT             NOT NULL,
    max_term_months  INT             NOT NULL,
    base_rate        NUMERIC(6,2)    NOT NULL,
    active           BOOLEAN         NOT NULL DEFAULT TRUE,
    preapprovable    BOOLEAN         NOT NULL DEFAULT FALSE
);

-- ─── Компании (заёмщики) ──────────────────────────────────────────────────────
CREATE TABLE company (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inn            VARCHAR(12)  NOT NULL UNIQUE,
    name           VARCHAR(512) NOT NULL,
    okved          VARCHAR(20)  NOT NULL,
    director_name  VARCHAR(512) NOT NULL,
    registered_at  DATE         NOT NULL
);

-- ─── Пользователи приложения ──────────────────────────────────────────────────
CREATE TABLE app_user (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    login         VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL,   -- Role enum
    full_name     VARCHAR(512) NOT NULL,
    company_id    UUID REFERENCES company(id),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ─── Обороты по счёту (данные банка) ─────────────────────────────────────────
CREATE TABLE account_turnover (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id      UUID         NOT NULL REFERENCES company(id),
    month           DATE         NOT NULL,   -- первый день месяца
    turnover_amount NUMERIC(18,2) NOT NULL,
    avg_balance     NUMERIC(18,2) NOT NULL,
    UNIQUE (company_id, month)
);

-- ─── Предодобренные предложения ───────────────────────────────────────────────
CREATE TABLE preapproved_offer (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID          NOT NULL REFERENCES company(id),
    product_id UUID          NOT NULL REFERENCES product(id),
    max_amount NUMERIC(18,2) NOT NULL,
    rate_from  NUMERIC(6,2)  NOT NULL,
    valid_until TIMESTAMPTZ  NOT NULL,
    reason     JSONB,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- ─── Заявки ───────────────────────────────────────────────────────────────────
CREATE TABLE application (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id           UUID          NOT NULL REFERENCES company(id),
    product_id           UUID          NOT NULL REFERENCES product(id),
    created_by           UUID          NOT NULL REFERENCES app_user(id),
    amount               NUMERIC(18,2),
    term_months          INT,
    purpose              VARCHAR(50),          -- Purpose enum
    full_name            VARCHAR(512),
    phone                VARCHAR(30),
    revenue_6m           NUMERIC(18,2),
    monthly_expenses     NUMERIC(18,2),
    status               VARCHAR(50)   NOT NULL DEFAULT 'DRAFT',
    current_decision_ref VARCHAR(255),
    preapproved_offer_id UUID REFERENCES preapproved_offer(id),
    version              BIGINT        NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    submitted_at         TIMESTAMPTZ,
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_application_company    ON application(company_id);
CREATE INDEX idx_application_status     ON application(status);
CREATE INDEX idx_application_updated    ON application(updated_at);

-- ─── История статусов (append-only) ──────────────────────────────────────────
CREATE TABLE application_status_history (
    id             BIGSERIAL    PRIMARY KEY,
    application_id UUID         NOT NULL REFERENCES application(id),
    from_status    VARCHAR(50),
    to_status      VARCHAR(50)  NOT NULL,
    actor_type     VARCHAR(20)  NOT NULL,   -- ActorType enum
    actor_id       UUID,
    reason         VARCHAR(1024),
    comment        TEXT,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_ash_application ON application_status_history(application_id);

-- ─── Результаты внешних проверок ─────────────────────────────────────────────
-- Новая запись при каждой проверке (включая повторные) — не перезаписывается.
CREATE TABLE check_result (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id UUID        NOT NULL REFERENCES application(id),
    check_type     VARCHAR(30) NOT NULL,   -- CheckType enum
    status         VARCHAR(20) NOT NULL,   -- CheckStatus enum
    payload        JSONB,
    checked_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_cr_application ON check_result(application_id);

-- ─── Согласия (type + version) ───────────────────────────────────────────────
CREATE TABLE consent (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id UUID        NOT NULL REFERENCES application(id),
    type           VARCHAR(30) NOT NULL,   -- ConsentType enum
    text_version   VARCHAR(50) NOT NULL,
    given_by       UUID        NOT NULL REFERENCES app_user(id),
    given_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ─── Замечания к заявке ───────────────────────────────────────────────────────
CREATE TABLE application_issue (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id UUID        NOT NULL REFERENCES application(id),
    source         VARCHAR(20) NOT NULL,   -- IssueSource enum
    field          VARCHAR(100),
    message        VARCHAR(1024) NOT NULL,
    reason         VARCHAR(1024),
    resolved       BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at    TIMESTAMPTZ
);

-- ─── Задекларированные обязательства ─────────────────────────────────────────
CREATE TABLE declared_obligation (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id  UUID          NOT NULL REFERENCES application(id),
    creditor_name   VARCHAR(255),
    monthly_payment NUMERIC(18,2) NOT NULL,
    remaining_debt  NUMERIC(18,2)
);

-- ─── Снимки решений ML-сервиса ────────────────────────────────────────────────
CREATE TABLE decision_snapshot (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id UUID        NOT NULL REFERENCES application(id),
    decision_id    VARCHAR(255),
    request_id     VARCHAR(255),
    payload        JSONB,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ds_application ON decision_snapshot(application_id);

-- ─── Оферты ───────────────────────────────────────────────────────────────────
CREATE TABLE offer (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id UUID          NOT NULL REFERENCES application(id),
    amount         NUMERIC(18,2) NOT NULL,
    term_months    INT           NOT NULL,
    rate           NUMERIC(6,2)  NOT NULL,
    terms_changed  BOOLEAN       NOT NULL DEFAULT FALSE,
    changes        JSONB,
    status         VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',   -- OfferStatus
    valid_until    TIMESTAMPTZ   NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_offer_application ON offer(application_id);

-- ─── Договоры ─────────────────────────────────────────────────────────────────
CREATE TABLE contract (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    offer_id     UUID        NOT NULL REFERENCES offer(id),
    signed_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    signed_by    UUID        NOT NULL REFERENCES app_user(id),
    disbursed_at TIMESTAMPTZ,
    simulated    BOOLEAN     NOT NULL DEFAULT TRUE
);

-- ─── Ручные решения сотрудников ───────────────────────────────────────────────
CREATE TABLE manual_decision (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id UUID        NOT NULL REFERENCES application(id),
    employee_id    UUID        NOT NULL REFERENCES app_user(id),
    action         VARCHAR(30) NOT NULL,   -- ManualAction enum
    comment        TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ─── Моки внешних сервисов ────────────────────────────────────────────────────
CREATE TABLE mock_egrul (
    inn           VARCHAR(12)  PRIMARY KEY,
    status        VARCHAR(30)  NOT NULL,   -- ACTIVE | LIQUIDATING | INACTIVE
    okved         VARCHAR(20)  NOT NULL,
    director_name VARCHAR(512) NOT NULL,
    registered_at DATE         NOT NULL
);

CREATE TABLE mock_bki (
    inn             VARCHAR(12)   PRIMARY KEY,
    available       BOOLEAN       NOT NULL DEFAULT TRUE,
    has_overdue     BOOLEAN       NOT NULL DEFAULT FALSE,
    active_loans    INT           NOT NULL DEFAULT 0,
    total_debt      NUMERIC(18,2) NOT NULL DEFAULT 0,
    monthly_payment NUMERIC(18,2) NOT NULL DEFAULT 0
);
