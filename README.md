# Умный кредитный кабинет предпринимателя

Веб-сервис, который помогает владельцам микробизнеса без банковского опыта подобрать финансирование, заполнить заявку с понятными подсказками и пройти кредитный процесс. Для сотрудников банка система предоставляет единое рабочее пространство для просмотра, проверки и обработки поступающих заявок.

## Содержание

- [Аналитика и продуктовые требования](#аналитика-и-продуктовые-требования)
- [Стек технологий](#стек-технологий)
- [Быстрый запуск](#быстрый-запуск)
- [Архитектура](#архитектура)
- [Обработка заявки](#обработка-заявки)
- [ML-сервис](#ml-сервис)
- [Decision Engine](#decision-engine)
- [Демо-аккаунты](#демо-аккаунты)
- [Сценарии проверки](#сценарии-проверки)
- [API](#api)
- [Коллекция HTTP-запросов](#коллекция-http-запросов)
- [Переменные окружения](#переменные-окружения)
- [База данных](#база-данных)
- [Тесты](#тесты)
- [Структура проекта](#структура-проекта)

---

## Аналитика и продуктовые требования

Полное обоснование продукта — в [docs/analytics.pdf](docs/analytics.pdf).

Документ содержит:
- **Проблема и концепция** — начинающим предпринимателям сложно разобраться в банковских продуктах и пройти кредитный процесс; сотрудникам нужно единое рабочее место для обработки заявок.
- **Целевая аудитория** — ИП/микробизнес (25–30 лет, выручка 100–200 тыс./мес.) и сотрудник банка.
- **Границы MVP** — что входит: подбор продукта, заявка с подсказками, автопроверки, скоринг, рабочее место сотрудника. Что не входит: реальные интеграции, документы, мобильное приложение, кредиты под залог.
- **User Persona** — Виктория, 27 лет, интернет-магазин комнатных растений, выручка 150 тыс. ₽/мес., хочет 180 000 ₽ на 12 месяцев на закупку товара.
- **User Journey Map** — 5 этапов от возникновения потребности до получения результата с болями и эмоциями на каждом шаге.
- **User Stories** — 6 историй предпринимателя (US-C01–C06) и 5 историй сотрудника банка (US-B01–B05).
- **Тестовые сценарии** — 9 сценариев (TS-01–TS-09): подача, ошибки, черновик, уточнения, одобрение, отказ, история, выдача.

### Условные продукты для демонстрации

| Продукт                           | Сумма                 | Срок         |
|-----------------------------------|-----------------------|--------------|
| Кредит на текущие расходы бизнеса | 50 000 – 500 000 ₽    | 3–12 месяцев |
| Кредит на оборудование            | 100 000 – 1 000 000 ₽ | 6–36 месяцев |

## Стек технологий

| Слой            | Технология                                    |
|-----------------|-----------------------------------------------|
| Frontend        | React 18, TypeScript, Vite, Alfalab UI        |
| Backend         | Java 22, Spring Boot 4.1, Gradle, Flyway      |
| ML-сервис       | Python 3.12, FastAPI, CatBoost, scikit-learn  |
| База данных     | PostgreSQL 16                                 |
| Аутентификация  | JWT HS256 (jjwt 0.12)                         |
| Контейнеризация | Docker Compose                                |


## Быстрый запуск

```bash
docker compose up --build
```

| Сервис      | URL                                   |
|-------------|---------------------------------------|
| Frontend    | http://localhost:3000                 |
| Backend API | http://localhost:8080                 |
| Swagger UI  | http://localhost:8080/swagger-ui.html |
| ML-сервис   | http://localhost:8001/docs            |


## Архитектура

```
  Клиент            Сотрудник банка
     │                      │
     └──────────┬───────────┘
                │
  ╔═════════════▼════════════════════════════════════════════════╗
  ║                 Frontend  (React 18 · TypeScript · Vite)     ║
  ║  /login  /applications  /products  /contracts                ║
  ║  /bank/queue  /bank/applications                             ║
  ╚═════════════╤════════════════════════════════════════════════╝
                │  HTTP · REST · JWT
  ╔═════════════▼════════════════════════════════════════════════╗
  ║                 Backend  (Java 22 · Spring Boot 4.1)         ║
  ║                                                              ║
  ║  ┌────────────────────┐   ┌────────────────────────────┐     ║
  ║  │  Security · JWT    │   │  BankService · OfferService│     ║
  ║  └────────────────────┘   └────────────────────────────┘     ║
  ║                                                              ║
  ║  ┌──────────────────────────────────────────────────────┐    ║
  ║  │  Кредитный конвейер                                  │    ║
  ║  │                                                      │    ║
  ║  │  ApplicationService.submit()                         │    ║
  ║  │           │ @TransactionalEventListener              │    ║
  ║  │           ▼                                          │    ║
  ║  │  PipelineTrigger  (@Async · pipelineExecutor)        │    ║
  ║  │           │                                          │    ║
  ║  │   1. completeness()  — поля, согласия, диапазоны     │    ║
  ║  │   2. dataCheck()     — ЕГРЮЛ · БКИ · Обороты         │    ║
  ║  │   3. score()         ──────────────────────────────────────►
  ║  └──────────────────────────────────────────────────────┘    ║
  ║                                                              ║
  ║  ┌──────────────────────────────────────────────────────┐    ║
  ║  │  BackgroundJobs  (@Scheduled)                        │    ║
  ║  │  · recover  — подхватывает зависшие заявки (2 мин)   │    ║
  ║  │  · expire   — истекающие оферты                      │    ║
  ║  │  · disburse — имитация выдачи кредита                │    ║
  ║  └──────────────────────────────────────────────────────┘    ║
  ╚═════════════╤════════════════════════════════════════════════╝
                │                                    │  HTTP/1.1
                │              ╔═════════════════════▼═══════════════════╗
                │              ║  ML-сервис  (Python 3.12 · FastAPI)     ║
                │              ║                                         ║
                │              ║  POST /decide                           ║
                │              ║                                         ║
                │              ║  Скоринг — fallback-цепочка:            ║
                │              ║    CatBoost + SHAP  (model.cbm)         ║
                │              ║      → Logistic Regression              ║
                │              ║        → BASELINE (эксперт-правила)     ║
                │              ║                                         ║
                │              ║  Decision Engine (policy.json)          ║
                │              ║    PD → класс риска → решение           ║
                │              ║    проверка платёжеспособности          ║
                │              ╚═════════════════════╤═══════════════════╝
                │                                    │
  ╔═════════════▼════════════════════════════════════▼═══════════╗
  ║                    PostgreSQL  16                            ║
  ║                                                              ║
  ║  schema public  (Flyway)          schema ml  (Alembic)       ║
  ║  ─────────────────────────────    ─────────────────────────  ║
  ║  application · offer · contract   scoring_results            ║
  ║  check_result · decision_snapshot                            ║
  ║  company · product · app_user                                ║
  ╚══════════════════════════════════════════════════════════════╝
```


## Обработка заявки

Каждый переход — отдельная транзакция. При сбое на любом шаге фоновая задача подхватит заявку через 2 минуты.

```
  Клиент заполняет           Клиент подаёт
  ─────────────────          ─────────────
      DRAFT ──────────────────► SUBMITTED
                                    │
                         ┌──────────▼──────────┐
                         │  COMPLETENESS_CHECK │  ① проверка полноты данных
                         └──────┬──────────┬───┘
                                │          │
                    ╔═══════════▼╗    ╔════▼════════════╗
                    ║ NEEDS_INFO ║    ║   DATA_CHECK    ║  ② ЕГРЮЛ · БКИ · Обороты
                    ║  клиент    ║    ╚════╤══════╤══╤══╝
                    ║  исправляет║         │      │  │
                    ╚════════════╝         │      │  └─────────────────────┐
                         ▲                 │      │                        │
                         │ повтор          │      │ сервис недоступен      │ стоп-фактор
                         └─────────────────┘      │                        │
                                                  ▼                        ▼
                                           ╔════════════╗          ╔══════════════╗
                                           ║   MANUAL   ║          ║   REJECTED   ║
                                           ║   REVIEW   ║          ╚══════════════╝
                                           ╚═════╤══════╝
                              все ОК             │        сотрудник банка решает вручную
                                │                │─────────────────────────┐
                                ▼                │                         │
                          ┌─────────┐            │         ┌───────────────▼──────────────┐
                          │ SCORING │            │         │ APPROVED / REJECTED /        │
                          └────┬────┘            │         │ NEEDS_INFO / CANCELLED       │
                               │ ML /decide      │         └──────────────────────────────┘
              ┌────────────────┼──────────────┐  │
              │                │              │  │
              ▼                ▼              ▼  │
       ╔════════════╗  ╔══════════════╗  ╔═══════╝════╗
       ║  APPROVED  ║  ║   REJECTED   ║  ║   MANUAL   ║
       ╚═════╤══════╝  ╚══════════════╝  ║   REVIEW   ║
             │                           ╚════════════╝
     клиент принимает оферту
             │
      ┌──────▼──────┐
      │   SIGNED    │──── OFFER_DECLINED
      └──────┬──────┘──── OFFER_EXPIRED
             │
      ┌──────▼──────┐
      │  DISBURSED  │  кредит выдан (учебный режим)
      └─────────────┘
```


## ML-сервис

> **Только учебный прототип**, обученный на синтетических данных. Не предназначен для реального кредитования.

Продуктовые требования к скорингу взяты из [docs/analytics.pdf](docs/analytics.pdf): модель использует выручку, расходы, кредиты, возраст бизнеса и предыдущие просрочки; выход — **низкий / средний / высокий риск** с указанием факторов.

### Как это работает

После прохождения всех внешних проверок (ЕГРЮЛ, БКИ, обороты по счёту) бэкенд отправляет запрос на `POST /decide`. ML-сервис выполняет три шага:

#### Шаг 1 — Скоринг

Сервис выбирает лучшую из доступных моделей по приоритету. **Модель загружается один раз при первом запросе и не переобучается при перезапуске** — экземпляр `CreditRiskScorer` создаётся единожды и переиспользуется для всех последующих запросов.

| Приоритет | Модель              | Файл                            |
|-----------|---------------------|---------------------------------|
| 1         | CatBoost + SHAP     | `models/model.cbm`              |
| 2         | Logistic Regression | `models/logreg_baseline.joblib` |
| 3         | BASELINE (эксперт)  | всегда доступна, без файлов     |

#### Шаг 2 — Decision Engine

Применяет политику из `policy.json` (можно менять без пересборки):

| PD (вероятность дефолта) | Класс риска | Действие движка                                                     |
|--------------------------|-------------|---------------------------------------------------------------------|
| < 8%                     | LOW         | Проверка платёжеспособности → `APPROVE` или `APPROVE_CHANGED_TERMS` |
| 8% – 18%                 | MEDIUM      | Проверка платёжеспособности → `APPROVE` или `APPROVE_CHANGED_TERMS` |
| ≥ 18%                    | HIGH        | `MANUAL_REVIEW` (задаётся полем `high_risk_action` в `policy.json`) |

Поведение при MEDIUM и HIGH настраивается в `policy.json` без пересборки (`medium_risk_action`, `high_risk_action`).

Стоп-фактор на этапе DATA_CHECK (до ML-скоринга):
- Статус компании в ЕГРЮЛ: `LIQUIDATING` — заявка уходит в `REJECTED` без вызова ML.

Проверка платёжеспособности (при LOW и MEDIUM):
- Аннуитетный платёж ≤ 60% от свободного денежного потока → `APPROVE`
- Иначе — снижает сумму и возвращает `APPROVE_CHANGED_TERMS`
- Если даже минимальная сумма недоступна → `REJECT`

#### Шаг 3 — Идемпотентность

Каждый запрос сохраняется в `ml.scoring_results` по `request_id`. Повторный запрос с тем же `request_id` возвращает сохранённый результат без повторного скоринга.


### Модели

#### CatBoost (основная модель)

- **Файл**: `models/model.cbm`
- **Алгоритм**: `CatBoostClassifier`, 360 итераций (best iteration по early stopping), глубина 5, learning rate 0.055
- **Целевая переменная**: `default_12m = 1` при просрочке 90+ дней в течение 12 месяцев с выдачи
- **Объяснимость**: SHAP-значения на шкале лог-шансов, топ-4 влияющих признака с описанием на русском языке
- **Пороги риска**: low < 8% PD, medium < 18% PD, high ≥ 18% PD

#### Logistic Regression (baseline для сравнения)

- **Файл**: `models/logreg_baseline.joblib`
- **Pipeline**: `StandardScaler` для числовых + `OneHotEncoder` для категориальных признаков → `LogisticRegression(max_iter=1500)`
- Используется как сравнительная точка отсчёта при обучении и как fallback при недоступности CatBoost

#### Rule-based baseline (только для локальной отладки)

Детерминированная эвристика (`ml/rule_baseline.py`), **не участвует в serving**. Возвращает балл 0–100 (не PD). Используется для sanity-проверки логики признаков:

| Компонент              | Вес   | Логика                                            |
|------------------------|-------|---------------------------------------------------|
| Прошлые просрочки      | 30%   | `100 - 25 * past_overdue_count`                   |
| Платёжеспособность     | 25%   | покрытие платежа свободным денежным потоком       |
| Доля расходов          | 20%   | чем ниже доля расходов в выручке, тем лучше       |
| Действующие кредиты    | 15%   | чем ниже доля существующих платежей, тем лучше    |
| Возраст компании       | 10%   | насыщение к 36 месяцам                            |

```python
from ml.rule_baseline import rule_score
print(rule_score(request))
# {"score_0_100": 72.5, "risk_level": "medium", "factor_scores": {...}}
```


### Признаки модели (15 штук)

Единый источник признаков для обучения и inference — `ml/features.py`. Гарантирует отсутствие расхождений между обучением и продакшном.

| Признак                                 | Тип      | Описание                                                           |
|-----------------------------------------|----------|--------------------------------------------------------------------|
| `industry`                              | категор. | Отрасль (retail / services / construction / manufacturing / other) |
| `product_type`                          | категор. | Тип кредита (working_capital / equipment)                          |
| `company_age_months`                    | числовой | Возраст компании в месяцах на дату заявки                          |
| `avg_monthly_revenue_rub`               | числовой | Среднемесячная выручка (revenue_6m / 6)                            |
| `mandatory_expense_ratio`               | числовой | Доля обязательных расходов в выручке                               |
| `existing_payment_ratio`                | числовой | Доля платежей по действующим кредитам в выручке                    |
| `free_cashflow_rub`                     | числовой | Свободный денежный поток: выручка − расходы − кредиты              |
| `has_active_loans`                      | бинарный | Наличие действующих кредитов (0/1)                                 |
| `past_overdue_count`                    | числовой | Количество прошлых просрочек (из мока БКИ)                         |
| `requested_amount_rub`                  | числовой | Запрашиваемая сумма                                                |
| `term_months`                           | числовой | Срок кредита                                                       |
| `requested_payment_principal_proxy_rub` | числовой | Оценка погашения основного долга: сумма / срок                     |
| `payment_burden_ratio`                  | числовой | Долговая нагрузка: (существующие + новый платёж) / выручка         |
| `loan_to_revenue_6m_ratio`              | числовой | Сумма кредита / выручка за 6 месяцев                               |
| `affordability_coverage_proxy`          | числовой | Свободный денежный поток / оценка платежа                          |


### Обучение и оценка

#### Данные

Синтетический генератор (`ml/generate_dataset.py`) создаёт исторические заявки с известным исходом:
- 50 000 строк, базовая доля дефолтов ~10%
- Реалистичные корреляции: выручка (логнормальное распределение), расходы (бета), возраст компании (гамма)
- Каждая компания присутствует только в одном сплите — исключает утечку данных

#### Разбивка по времени

| Сплит      | Период                | Строк   |
|------------|-----------------------|---------|
| Train      | до 01.01.2024         | ~43 000 |
| Validation | 01.01.2024–01.07.2024 | ~3 400  |
| Test       | с 01.07.2024          | ~3 300  |

#### Метрики (синтетические данные)

| Метрика    | CatBoost (val) | LogReg (val) | CatBoost (test) | LogReg (test) |
|------------|----------------|--------------|-----------------|---------------|
| ROC-AUC    | 0.641          | 0.636        | 0.634           | 0.632         |
| PR-AUC     | 0.146          | 0.153        | 0.170           | 0.180         |
| Brier      | 0.083          | 0.083        | 0.096           | 0.096         |

Калибровка CatBoost на тесте (доля дефолтов vs предсказанная PD):

| PD диапазон | Предсказанная PD | Фактическая доля |
|-------------|------------------|------------------|
| 0.0 – 0.1   | 0.074            | 0.075            |
| 0.1 – 0.2   | 0.134            | 0.145            |
| 0.2 – 0.3   | 0.226            | 0.260            |

#### Запуск обучения

```bash
cd ml_and_decision

# 1. Установить зависимости
pip install -r requirements.txt

# 2. Сгенерировать синтетический датасет
python -m ml.generate_dataset --rows 50000 --seed 42 --output data/historical_loans.csv

# 3. Обучить CatBoost + Logistic Regression
python -m ml.train --data data/historical_loans.csv --out models

# 4. Оценить модель на тестовой выборке
python -m ml.evaluate --data data/historical_loans.csv --model-dir models

# 5. Пример предсказания
python -m ml.demo --model-dir models

# 6. Сквозная демонстрация: ML → Decision → действие сотрудника
python -m decision.demo --model-dir models
```

> **Для быстрого старта обученная модель уже включена** в репозиторий (`models/model.cbm`). Переобучать не нужно. Метрики текущего прогона — в `models/metrics.json`.

### Ответ `/decide`

```json
{
  "decision_id": "uuid",
  "scoring_result_id": "uuid",
  "score": 740,
  "probability_of_default": 0.0121,
  "risk_class": "LOW",
  "decision": "APPROVE",
  "final_terms": {
    "amount": 400000,
    "term_months": 12,
    "rate": 18.50,
    "monthly_payment": 36714.05
  },
  "top_factors": [
    {
      "feature": "loan_to_revenue_6m_ratio",
      "description": "Сумма кредита / выручка за 6 мес.: 0.44",
      "direction": "decreases_risk",
      "shap_log_odds": -0.31
    }
  ],
  "model_version": "synthetic-credit-risk-v1"
}
```

Возможные значения `decision`: `APPROVE`, `APPROVE_CHANGED_TERMS`, `REJECT`, `MANUAL_REVIEW`, `REQUEST_INFO`.

### Fallback при недоступности ML-сервиса

Если ML-сервис недоступен или вернул ошибку — бэкенд выполняет 3 попытки (настраивается через `ML_RETRIES`), затем переводит заявку в `MANUAL_REVIEW`. Сотрудник принимает решение вручную.


## Decision Engine

Rule-based движок (`decision/engine.py`) применяет политику последовательно:

1. **Согласия** — наличие согласия на обработку персональных данных и кредитной истории
2. **Доступность проверок** — ФНС/БКИ не в статусе `unavailable`
3. **Статус компании** — ЕГРЮЛ: не LIQUIDATING / LIQUIDATED / BANKRUPT
4. **Лимиты продукта** — сумма и срок в допустимых диапазонах
5. **Класс риска** — HIGH → `REJECT`, LOW/MEDIUM → продолжаем
6. **Платёжеспособность** — аннуитетный платёж ≤ 60% от свободного денежного потока

Если на шаге 6 платёж не укладывается в бюджет, движок рассчитывает максимально допустимую сумму и предлагает её (`APPROVE_CHANGED_TERMS`).

Все пороги, ставки и лимиты настраиваются в `decision/policy.json` без пересборки контейнера.

## Демо-аккаунты

Пароль для всех: **`Demo1234!`**

| Логин      | Роль     | Компания                                    | Ожидаемый результат                             |
|------------|----------|---------------------------------------------|-------------------------------------------------|
| `client1`  | CLIENT   | ИП Петрова Виктория, ОКВЭД 47.11, 6+ лет    | **APPROVED** — хороший профиль, LOW риск        |
| `client2`  | CLIENT   | ИП Волков Сергей, высокий оборот по счёту   | **APPROVED** — изменённые условия (сумма ↓)     |
| `client3`  | CLIENT   | ООО «Ликвид», ЕГРЮЛ: LIQUIDATING            | **REJECTED** — стоп-фактор, ML не вызывается    |
| `client4`  | CLIENT   | ИП Орлов, 8 просрочек в БКИ (overdue ×8)   | **MANUAL_REVIEW** — HIGH риск (PD > 18 %)       |
| `client5`  | CLIENT   | ИП Кузнецова, ОКВЭД в ЕГРЮЛ не совпадает   | **NEEDS_INFO** — расхождение ОКВЭД 74.10/74.90  |
| `client6`  | CLIENT   | ИП Соколов, БКИ недоступен                  | **MANUAL_REVIEW** — внешняя проверка упала      |
| `employee` | EMPLOYEE | —                                           | Доступ к `/api/bank/**`                         |

> **Важно:** результат зависит от значения `monthlyExpenses`, которое вводит пользователь.
> Используйте цифры ниже — они подобраны под порог affordability каждого сценария.


## Сценарии проверки

### Сценарий 1 — автоодобрение (client1 / Петрова)

```
1.  POST /api/auth/login
    {"login":"client1","password":"Demo1234!"}
    → сохранить token

2.  GET  /api/products
    → получить id продукта WORKING_CAPITAL

3.  POST /api/applications
    {"productId":"<id>","amount":180000,"termMonths":12,"purpose":"WORKING_CAPITAL"}
    → сохранить id заявки

4.  PUT  /api/applications/{id}
    {"fullName":"Петрова Виктория Андреевна","phone":"+79001000001",
     "revenue6m":1485000,"monthlyExpenses":100000,"version":0}
    # revenue6m = сумма оборотов по счёту за 6 мес (~247 500 ₽/мес)
    # monthlyExpenses < monthly_revenue → есть свободный денежный поток

5.  POST /api/applications/{id}/submit
    {"bkiConsent":true,"personalDataConsent":true,"version":1}

6.  GET  /api/applications/{id}   (polling ~2-5 сек)
    → статус APPROVED, risk=LOW, в поле offer — условия

7.  POST /api/offers/{offerId}/accept
    → статус SIGNED → через 5 сек DISBURSED

8.  GET  /api/contracts
    → кредит в кабинете (simulated: true)
```

### Сценарий 2 — изменённые условия (client2 / Волков)

```
1.  POST /api/auth/login {"login":"client2","password":"Demo1234!"}

2.  POST /api/applications
    {"productId":"<WORKING_CAPITAL_id>","amount":450000,"termMonths":12,
     "purpose":"WORKING_CAPITAL"}

3.  PUT  /api/applications/{id}
    {"fullName":"Волков Сергей Михайлович","phone":"+79001000002",
     "revenue6m":3108000,"monthlyExpenses":450000,"version":0}
    # monthlyExpenses подобраны так, что annuity(450000) > max_payment
    # → Decision Engine снижает сумму до ~445 000 ₽

4.  POST /api/applications/{id}/submit
    {"bkiConsent":true,"personalDataConsent":true,"version":1}

5.  GET  /api/applications/{id}
    → статус APPROVED, offer.termsChanged=true, offer.amount < 450000
```

### Сценарий 3 — ручное рассмотрение (client4 + employee)

```
1.  POST /api/auth/login {"login":"client4","password":"Demo1234!"}

2.  POST /api/applications
    {"productId":"<WORKING_CAPITAL_id>","amount":200000,"termMonths":12,
     "purpose":"WORKING_CAPITAL"}

3.  PUT  /api/applications/{id}
    {"fullName":"Орлов Михаил Сергеевич","phone":"+79001000004",
     "revenue6m":1090500,"monthlyExpenses":100000,"version":0}
    # overdue_count=8 в БКИ → PD > 0.18 → HIGH риск → MANUAL_REVIEW

4.  POST /api/applications/{id}/submit
    {"bkiConsent":true,"personalDataConsent":true,"version":1}
    → статус MANUAL_REVIEW

5.  POST /api/auth/login {"login":"employee","password":"Demo1234!"}

6.  GET  /api/bank/applications?status=MANUAL_REVIEW  → найти заявку

7.  GET  /api/bank/applications/{id}
    → PD, класс риска, топ-факторы — только для сотрудника

8.  POST /api/bank/applications/{id}/decision
    Варианты:
    {"action":"APPROVE","version":N}
    {"action":"APPROVE","amount":200000,"rate":22.0,"version":N}
    {"action":"REJECT","comment":"Высокая долговая нагрузка","version":N}
    {"action":"CHANGE_TERMS","amount":150000,"termMonths":12,"rate":21.5,"version":N}
    {"action":"REQUEST_INFO","comment":"Нужна выписка по счёту","version":N}
```

### Сценарий 4 — отказ по стоп-фактору (client3)

```
1.  Войти как client3, создать и подать заявку (любые корректные данные)
    → статус сразу REJECTED (ЕГРЮЛ: LIQUIDATING)
    ML-сервис не вызывается.
```

### Сценарий 5 — NEEDS_INFO (расхождение ОКВЭД, client5)

```
1.  POST /api/auth/login {"login":"client5","password":"Demo1234!"}

2.  POST /api/applications
    {"productId":"<WORKING_CAPITAL_id>","amount":150000,"termMonths":6,
     "purpose":"WORKING_CAPITAL"}

3.  PUT  /api/applications/{id}
    {"fullName":"Кузнецова Анна Игоревна","phone":"+79001000005",
     "revenue6m":957000,"monthlyExpenses":70000,"version":0}

4.  POST /api/applications/{id}/submit
    {"bkiConsent":true,"personalDataConsent":true,"version":1}
    → pipeline: DATA_CHECK → EgrulMockCheck: ОКВЭД 74.90 в реестре ≠ 74.10 в заявке
    → SCORING → fns_check=pending → NEEDS_INFO
```

### Сценарий 6 — MANUAL_REVIEW (БКИ недоступен, client6)

```
1.  POST /api/auth/login {"login":"client6","password":"Demo1234!"}

2.  POST /api/applications + PUT + submit (любые корректные данные)
    {"revenue6m":1950000,"monthlyExpenses":130000,...}
    → BkiMockCheck: available=false → UNAVAILABLE
    → Decision Engine: внешняя проверка недоступна → MANUAL_REVIEW
```

## API

### Клиентские эндпоинты

| Метод | Путь                            | Описание                        |
|-------|---------------------------------|---------------------------------|
| POST  | `/api/auth/login`               | Получить JWT-токен              |
| GET   | `/api/products`                 | Список кредитных продуктов      |
| POST  | `/api/recommendations`          | Подобрать продукт по параметрам |
| GET   | `/api/offers/preapproved`       | Предодобренные предложения      |
| GET   | `/api/applications`             | Мои заявки (страница)           |
| POST  | `/api/applications`             | Создать заявку                  |
| GET   | `/api/applications/{id}`        | Детали заявки                   |
| PUT   | `/api/applications/{id}`        | Обновить черновик               |
| POST  | `/api/applications/{id}/submit` | Подать заявку                   |
| POST  | `/api/applications/{id}/cancel` | Отозвать заявку                 |
| POST  | `/api/offers/{id}/accept`       | Принять оферту                  |
| POST  | `/api/offers/{id}/decline`      | Отклонить оферту                |
| GET   | `/api/contracts`                | Мои договоры                    |

### Банковские эндпоинты (роль EMPLOYEE)

| Метод | Путь                                   | Описание                           |
|-------|----------------------------------------|------------------------------------|
| GET   | `/api/bank/applications`               | Очередь заявок (фильтр по статусу) |
| GET   | `/api/bank/applications/{id}`          | Карточка с проверками и ML-данными |
| POST  | `/api/bank/applications/{id}/decision` | Принять решение                    |

Полная документация: `http://localhost:8080/swagger-ui.html`

## Коллекция HTTP-запросов

Файл [`docs/requests.http`](docs/requests.http) содержит готовые запросы ко всем эндпоинтам.
Открывается без дополнительных инструментов:

- **IntelliJ IDEA** — встроенный HTTP Client (кнопка ▶ рядом с каждым `###`)
- **VS Code** — расширение [REST Client](https://marketplace.visualstudio.com/items?itemName=humao.rest-client)

### Быстрый старт с коллекцией

1. Выполните запрос логина (`POST /api/auth/login`) — IDEA автоматически сохранит ответ в переменную `loginClient1`.
2. Заполните переменные в конце файла:

```
@clientToken   = <token из POST /api/auth/login client1>
@employeeToken = <token из POST /api/auth/login employee>
@productId     = <id из GET /api/products>
@applicationId = <id из POST /api/applications>
@offerId       = <offer.id из GET /api/applications/{id}>
```

3. Запускайте запросы по порядку разделов: аутентификация → продукты → создание заявки → submit → polling статуса → оферта.

В файле также есть прямые запросы к ML-сервису (порт `8001`) для проверки скоринга и Decision Engine без участия бэкенда.

## Переменные окружения

### Backend

| Переменная                        | По умолчанию                                      | Описание                               |
|-----------------------------------|---------------------------------------------------|----------------------------------------|
| `DB_URL`                          | `jdbc:postgresql://localhost:5432/credit_cabinet` | JDBC URL                               |
| `DB_USERNAME`                     | `postgres`                                        | Пользователь БД                        |
| `DB_PASSWORD`                     | `postgres`                                        | Пароль БД                              |
| `JWT_SECRET`                      | `my-super-secret-key-for-hackathon-32+`           | Секрет JWT (≥ 32 байта)                |
| `JWT_TTL_MINUTES`                 | `480`                                             | Время жизни токена                     |
| `ML_BASE_URL`                     | `http://ml:8000` (Docker) / `http://localhost:8001` (локально) | URL ML-сервиса          |
| `ML_CONNECT_TIMEOUT_MS`           | `2000`                                            | Таймаут подключения к ML               |
| `ML_READ_TIMEOUT_MS`              | `10000`                                           | Таймаут чтения ответа ML               |
| `ML_RETRIES`                      | `2`                                               | Повторные попытки к ML                 |
| `PIPELINE_RECOVERY_AFTER_SECONDS` | `120`                                             | Секунд до считания заявки «зависшей»   |
| `PIPELINE_RECOVERY_INTERVAL_MS`   | `30000`                                           | Интервал фоновой задачи восстановления |
| `PIPELINE_DISBURSEMENT_DELAY_MS`  | `5000`                                            | Задержка имитации выдачи               |
| `OFFER_TTL_DAYS`                  | `3`                                               | Срок действия оферты (дни)             |
| `SEED_ENABLED`                    | `true`                                            | Создавать демо-данные при старте       |
| `SEED_PASSWORD`                   | `Demo1234!`                                       | Пароль демо-аккаунтов                  |
| `CORS_ORIGINS`                    | `http://localhost:3000,http://localhost:5173`     | Разрешённые CORS-источники             |

### ML-сервис

| Переменная             | По умолчанию                                                   | Описание                      |
|------------------------|----------------------------------------------------------------|-------------------------------|
| `DATABASE_URL`         | `postgresql://postgres:postgres@localhost:5433/credit_cabinet` | URL БД                        |
| `SCORING_MODEL_DIR`    | `/app/models`                                                  | Путь к файлам моделей         |
| `DECISION_POLICY_PATH` | `/app/decision/policy.json`                                    | Путь к файлу политики решений |
| `MODEL_VERSION`        | `1.0`                                                          | Версия модели (в ответах)     |

## База данных

### Схема `public` (Flyway)

```
application               — заявки
application_issue         — замечания по заявке
application_status_history — история переходов
check_result              — результаты внешних проверок (EGRUL, BKI, ACCOUNT)
consent                   — согласия клиента
declared_obligation       — задекларированные обязательства
decision_snapshot         — снапшот ответа ML (JSONB)
offer                     — оферты банка
contract                  — договоры

company                   — данные компании
account_turnover          — обороты по счёту

product                   — кредитные продукты
preapproved_offer         — предодобренные предложения

app_user                  — пользователи системы
mock_egrul                — мок ЕГРЮЛ
mock_bki                  — мок БКИ
```

### Схема `ml` (Alembic)

```
scoring_results  — результаты скоринга с топ-факторами и решением
```


## Тесты

### Backend

```bash
cd backend
./gradlew test
```

Требуется Docker — Testcontainers поднимает PostgreSQL автоматически.

| Класс                          | Тип         | Что проверяет                                               |
|--------------------------------|-------------|-------------------------------------------------------------|
| `ApplicationStateMachineTest`  | Unit        | Допустимые и недопустимые переходы статусов                 |
| `CompletenessValidatorTest`    | Unit        | Обязательные поля, диапазоны продукта, согласия             |
| `PipelineIntegrationTest`      | Integration | Полный путь SUBMITTED → APPROVED / REJECTED / MANUAL_REVIEW |
| `MlFallbackAndIdempotencyTest` | Integration | ML недоступен → MANUAL_REVIEW; идемпотентность              |

### ML-сервис

```bash
cd ml_and_decision
pip install -r requirements.txt
pytest -q
```

## Структура проекта

```
.
├── docs/
│   └── analytics.pdf               продуктовая аналитика (14 стр.)
│
├── backend/                        Java 22 / Spring Boot 4.1
│   └── src/main/java/ru/backend/
│       ├── application/            заявки — контроллер, сервис, пайплайн, сущности
│       ├── auth/                   аутентификация, JWT, DataSeeder
│       ├── bank/                   API для сотрудника банка
│       ├── common/                 общие утилиты, обработка ошибок, AsyncConfig
│       ├── company/                компании, обороты по счёту
│       ├── decision/               ML-клиент, снапшоты решений
│       ├── offer/                  оферты, договоры, выдача
│       ├── product/                продукты, предодобрение, рекомендации
│       └── session/                JWT-фильтр, Spring Security
│
├── frontend/                       React 18 / TypeScript
│   └── src/
│       ├── api/                    HTTP-клиент и типы
│       ├── components/             StatusBadge и др.
│       ├── pages/                  8 страниц (клиент + банк)
│       └── store/                  Redux (authSlice)
│
├── ml_and_decision/                Python 3.12 / FastAPI
│   ├── ml/
│   │   ├── schemas.py              Pydantic-контракт (ScoringInput, ScoringResult)
│   │   ├── features.py             15 признаков — единый источник для train и inference
│   │   ├── generate_dataset.py     генератор синтетических исторических кредитов
│   │   ├── train.py                CatBoost + LogisticRegression; сохранение артефактов
│   │   ├── evaluate.py             оценка модели на тестовой выборке
│   │   ├── metrics.py              ROC-AUC, PR-AUC, Brier, LogLoss, калибровка
│   │   ├── inference.py            CreditRiskScorer — загрузка один раз, SHAP-объяснения
│   │   ├── rule_baseline.py        локальная эвристика 0–100 (только отладка)
│   │   └── demo.py                 пример предсказания по заявке
│   ├── decision/
│   │   ├── engine.py               Rule-based движок решений
│   │   ├── policy.json             версионируемые пороги и параметры продуктов
│   │   ├── affordability.py        аннуитетный расчёт и проверка платёжеспособности
│   │   ├── staff_actions.py        валидация решения сотрудника банка
│   │   └── demo.py                 сквозной ML + Decision + staff пример
│   ├── examples/
│   │   ├── serve.py                самостоятельное FastAPI-приложение (uvicorn)
│   │   ├── fastapi_router.py       router для встраивания в существующий FastAPI
│   │   └── decision_router.py      POST /internal/decision/evaluate
│   ├── models/
│   │   ├── model.cbm               обученная модель CatBoost
│   │   ├── logreg_baseline.joblib  baseline Logistic Regression
│   │   ├── metadata.json           версия, схема признаков, пороги риска
│   │   └── metrics.json            метрики синтетического теста
│   └── alembic/                    миграции схемы ml
│
└── docker-compose.yml              postgres + ml + backend + frontend
```
