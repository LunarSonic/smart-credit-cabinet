# Decision Engine — rule-based credit recommendations

Независимый от ML модуль предварительных рекомендаций по кредиту. Продуктовые
диапазоны взяты из `Ум.pdf`, стр. 3: `working_capital` 50–500 тыс. ₽ на 3–12 мес.,
`equipment` 100 тыс.–1 млн ₽ на 6–36 мес.

**Не взято из PDF и придумано только для демо:** годовые ставки 18%/20%, доля
свободного денежного потока, которую допускается направить на новый платёж
(60%), пороги/действия по категории риска и округление сумм до 1000 ₽.
Все параметры расположены в `decision/policy.json` и версионируются.

## Pipeline

1. Backend собирает анкету и данные из *моков* ФНС/БКИ и согласия.
2. ML Scoring возвращает `ScoringResult` (синтетический PD и risk_level).
3. Decision Engine проверяет согласия, доступность проверок, статус компании,
   полномочия заявителя, ограничения продуктов и риск.
4. При допустимом риске считает среднюю выручку, свободный остаток и максимально
   разрешённый новый **аннуитетный** платёж с учётом вымышленной ставки.
5. Возвращает `approve` / `alternative_offer` / `needs_info` / `manual_review` / `reject`
   с машиночитаемыми `reason_codes`, объяснением и предложением (если применимо).
6. **Backend** сохраняет предварительную оценку. **Сотрудник банка** рассматривает
   её и фиксирует финальное решение/изменение статуса в Workflow. Модуль не
   записывает БД и не меняет статусы самостоятельно.

Модель PD обучена на синтетике. По этой причине высокая категория риска по
умолчанию передаётся на **ручное рассмотрение**, а не ведёт к отказу автоматически.

## Встроить в существующий backend

```python
from ml.inference import CreditRiskScorer
from ml.schemas import ScoringInput
from decision import DecisionEngine, VerificationData

scorer = CreditRiskScorer("models")  # загрузка один раз при старте
engine = DecisionEngine()             # загрузка policy.json один раз

application = ScoringInput.model_validate(application_payload)
checks = VerificationData.model_validate(internal_fns_bki_and_consent_payload)
score = scorer.predict(application)
preliminary = engine.evaluate(application, score, checks)
print(preliminary.model_dump())
```

`VerificationData` — **только** результаты доверенных внутренних адаптеров.
Если проверка недоступна — `manual_review`; если ожидается — `needs_info`.
Отсутствующие согласия, неизвестный статус или полномочия также приводят к
`needs_info`.

Пример API: `POST /internal/decision/evaluate` с JSON-объектом
`{"application": <ScoringInput>, "verifications": <VerificationData>}`.
Это **внутренний демонстрационный endpoint** без реализации аутентификации:
добавляйте его только под авторизацией основного backend и не принимайте
`verifications` из публичного клиентского интерфейса. Нельзя принимать
`ScoringResult` от браузера: он всегда формируется на сервере.

Выход `DecisionResult` имеет `finalized=false` и
`requires_employee_confirmation=true` во всех сценариях.

## Запуск

```bash
uvicorn examples.serve:app --host 127.0.0.1 --port 8001
python -m pytest -q tests/test_decision.py
```

Оценка affordability использует среднюю шестимесячную выручку и обязательные
расходы из анкеты, что является **упрощением**: не заменяет анализ денежных
потоков или документы реального заёмщика. Приведённые предложения являются
только учебной симуляцией.

## Проверка предложения сотрудника

В документе (TS-06, стр. 13) сотрудник банка может предложить 150 000 ₽ вместо
запрошенных 180 000 ₽. Это поддержано функцией **валидации** (без записи в БД):

```python
from decision import StaffActionValidator

validator = StaffActionValidator(engine)
validated = validator.validate(application, score, checks, {
    "staff_id": "bank-employee-1",  # берите из доверенной auth-сессии, НЕ из тела HTTP
    "action": "alternative_offer",
    "offered_amount_rub": 150000,
    "offered_term_months": 12,
    "reason": "Альтернативное предложение по итогам рассмотрения",
})
# backend: проверяет текущую версию заявки, сохраняет действие + событие
# в одной транзакции и меняет статус только через Workflow Engine
```

У `ValidatedStaffAction` выставлены `validated_only=true` и
`persistence_required=true` — это **ещё не** сохранённое решение.
Сотрудник может также запросить данные (`request_info`) или отказать (`reject`)
с обязательным объяснением. Проверка положительных предложений отклоняет
суммы/сроки вне продукта, платежи сверх модельного бюджета и неполные
обязательные проверки. Ручное одобрение заявки высокой категории риска требует
объяснения. В боевом банке потребовались бы дополнительные политики и
контроль полномочий.

Полный локальный пример: `python -m decision.demo --model-dir models`.
