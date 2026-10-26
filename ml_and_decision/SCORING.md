# ML-скоринг и Decision Engine для «Умного кредитного кабинета»

Готовые Python-модули `ml/` и `decision/`, подключаемые к backend через `CreditRiskScorer`, `DecisionEngine` или пример FastAPI-router. **Только учебный прототип, обученный на синтетических данных.** Его прогнозы нельзя использовать для реального кредитования.

## 1. Что взято из продуктовой аналитики

Продуктовая спецификация — [docs/analytics.pdf](../docs/analytics.pdf).

- Стр. 3: два демонстрационных продукта: текущие расходы (`working_capital`, 50–500 тыс. ₽, 3–12 мес.) и оборудование (`equipment`, 100 тыс.–1 млн ₽, 6–36 мес.).
- Стр. 6: предприниматель вводит сумму/срок/продукт, выручку за 6 месяцев, обязательные ежемесячные расходы, наличие действующих кредитов и платёж по ним. Данные о регистрации и виде деятельности поступают из имитации ФНС.
- Стр. 7: модель использует выручку, расходы, кредиты, возраст бизнеса, предыдущие просрочки (имитация БКИ); выход — **низкий / средний / высокий риск** плюс факторы.
- Стр. 8: пример предпринимательницы: 150 тыс. ₽/мес., бизнес 1,5 года, запрос 180 тыс. ₽ на 12 мес. **Расходы и кредитная история там не приведены**, поэтому значения в `ml/demo.py` вымышлены.

Файл — продуктовая спецификация, **не датасет с историческими кредитами**. Чтобы обучить модель, `generate_dataset.py` симулирует исторические заявки и исходы. Прогноз «PD12» имеет смысл **только внутри синтетического симулятора**. Ни ставки Альфа-Банка, ни фактические политики кредитования не используются.

## 2. Архитектура

```text
Анкета + mock ФНС + mock БКИ
        |
        v
Pydantic ScoringInput -> ml.features.build_features()
        |
        v
ml.inference.CreditRiskScorer (CatBoost + SHAP)
        |
        v
ScoringResult: risk_level, predicted_pd_12m, risk_factors, model_version
        |
        v
decision.DecisionEngine (policy.json, проверки и аннуитетный платеж)
        |
        v
DecisionResult: recommendation, reasons, offer, policy_version
        |
        v
Сотрудник банка подтверждает результат -> backend сохраняет -> Workflow (статусы)
```

Модуль `decision/` проверяет требования демонстрационных продуктов и возвращает
**предварительную рекомендацию**. По спецификации окончательное решение фиксирует
сотрудник банка. `Decision Engine` и `ML` **не меняют статус заявки самостоятельно**.
Ставки 18%/20%, доля свободного денежного потока 60% и пороги риска — **выдуманные
демонстрационные допущения**. См. [`decision/README.md`](decision/README.md).

## 3. Установка

Тестировано с Python 3.12. Из корня проекта:

```bash
python -m venv .venv
source .venv/bin/activate        # Windows: .venv\Scripts\activate
pip install -r requirements.txt
```

## 4. Запуск обучения

```bash
# Генерация полностью искусственной истории выданных кредитов
python -m ml.generate_dataset --rows 50000 --seed 42 --output data/historical_loans.csv

# Обучение CatBoost + baseline LogisticRegression, временной holdout
python -m ml.train --data data/historical_loans.csv --out models

# Независимая проверка отложенной части
python -m ml.evaluate --data data/historical_loans.csv --model-dir models

# Пример предсказания по заявке из документа (с допущениями)
python -m ml.demo --model-dir models

# Сквозная локальная демонстрация: ML -> решение -> действие сотрудника
python -m decision.demo --model-dir models

# Запуск unit/integration tests
pytest -q
```

**Для быстрого запуска обученная модель уже включена** в репозиторий (`models/model.cbm`); переобучать необязательно. Метрики прогона — в `models/metrics.json`. CSV-датасет не хранится в репозитории — при необходимости его можно сгенерировать командой выше.

## 5. Подключение к backend

Скопируйте каталоги `ml/` и `models/` в backend (или используйте как отдельный внутренний сервис) и установите зависимости. Загрузите модель **один раз при старте процесса**:

```python
from ml.inference import CreditRiskScorer

scorer = CreditRiskScorer(model_dir="models")
result = scorer.predict({
    "application_id": "APP-123",
    "application_date": "2026-10-25",       # дата снимка заявки
    "registration_date": "2025-04-25",      # mock ФНС
    "industry": "retail",                 # упрощенная категория из mock ФНС
    "product_type": "working_capital",
    "revenue_6m_rub": 900000,              # СУММА выручки за 6 мес., не средняя
    "monthly_mandatory_expenses_rub": 90000,
    "has_active_loans": False,
    "existing_monthly_debt_payment_rub": 0,
    "past_overdue_count": 0,               # mock БКИ
    "requested_amount_rub": 180000,
    "term_months": 12,
})
print(result.model_dump())
```

Поля `application_id` и даты не являются признаками ML напрямую; дата используется только для вычисления возраста бизнеса. Внимание: `past_overdue_count` — **количество прошлых просрочек**, а не факт будущего дефолта.

### FastAPI

В `examples/fastapi_router.py` есть router. В существующем backend:

```python
from examples.fastapi_router import router
app.include_router(router)
```

Для локального теста предусмотрено самостоятельное приложение:

```bash
uvicorn examples.serve:app --host 127.0.0.1 --port 8001
# Swagger: http://127.0.0.1:8001/docs
```

POST `/internal/scoring/predict`, Content-Type `application/json`.

Также есть `POST /internal/decision/evaluate` для запуска **ML-скоринга и
Rule-based Decision Engine одним запросом**. Принимает:

```json
{
  "application": {"...": "те же поля, что у /internal/scoring/predict"},
  "verifications": {
    "company_status": "active",
    "representative_authorized": true,
    "personal_data_consent": true,
    "credit_history_consent": true,
    "fns_check": "ok",
    "bki_check": "ok"
  }
}
```

**Это только пример внутреннего API**. В реальном backend `verifications`
должны формироваться сервером на основании моков и сохранённых согласий, а не
приходить из браузера. `ScoringResult` нельзя принимать от клиента. Этот endpoint
не выдаёт деньги и не переводит заявку в статус «Одобрена».

Для проверки решения сотрудника используйте `StaffActionValidator` после
аутентификации сотрудника; подробности — в `decision/README.md`.

```bash
curl -X POST http://127.0.0.1:8001/internal/scoring/predict \
  -H 'Content-Type: application/json' \
  -d '{"application_id":"APP-123","application_date":"2026-10-25","registration_date":"2025-04-25","industry":"retail","product_type":"working_capital","revenue_6m_rub":900000,"monthly_mandatory_expenses_rub":90000,"has_active_loans":false,"existing_monthly_debt_payment_rub":0,"past_overdue_count":0,"requested_amount_rub":180000,"term_months":12}'
```

**ВНИМАНИЕ:** example router **не включает авторизацию**. Не публикуйте его без авторизации банковской стороны, проверки прав на `application_id`, журналирования запросов и ограничения частоты. В Docker Compose по умолчанию проброс порта ограничен `127.0.0.1:8001`.

Docker-запуск:

```bash
docker compose up --build
```

### Локальный rule-based baseline (только для отладки)

Для локальной sanity-проверки признаков предусмотрена детерминированная эвристика (`ml/rule_baseline.py`), возвращающая балл **0–100 — не вероятность дефолта**. В serving не участвует, клиенту не показывается.

```python
from ml.rule_baseline import rule_score
print(rule_score(request))
# {"score_0_100": 72.5, "risk_level": "medium", "factor_scores": {...}, "scoring_backend": "rule_based_local_only"}
```

Экспертные веса условны. Scoring API обслуживает **только CatBoost** (или LogReg/BASELINE при отсутствии файлов модели); fallback на эту эвристику при сбое ML **не происходит**.

## 6. Принятые допущения

- В PDF «выручка за последние 6 месяцев» трактуется как **единая суммарная величина**, а не массив из 6 помесячных значений. `avg_monthly_revenue = revenue_6m_rub / 6`.
- `industry`: для обучения используются `retail`, `services`, `construction`, `manufacturing`, `other`; неизвестные категории нормализуются в `other`. Маппинг кодов ОКВЭД должен выполняться в интеграционном слое.
- Запрошенная выплата `requested_amount / term_months` — **приближение по основному долгу без процентов** исключительно для конструирования признаков. Для проверки платёжеспособности `decision/affordability.py` использует аннуитетную формулу с **вымышленной демо-ставкой**, заданной в `decision/policy.json`; это не условия реального кредита.
- Экспериментальная цель: `default_12m = 1` при 90+ днях просрочки в течение 12 мес. с выдачи. Генератор прямо симулирует такой итог; помесячная история платежей для данного MVP не реализована.
- Для обучения генерируются только **исторические выданные кредиты**, по которым известен исход. Отклоненные заявки НЕ размечаются нулём. Демо-заявки хранятся отдельно.
- Синтетическая базовая доля дефолтов по умолчанию — 10% (настройка генератора), не фактический уровень банка.
- Тестовая временная выборка — вторая половина 2024 года. Даты соответствуют полностью наблюдаемой истории на октябрь 2026 года.
- Пороги категорий в `models/metadata.json`: `low` при PD `< 0.08`, `medium` при PD `< 0.18`, иначе `high` — **условные**.
- Значения SHAP отражают вклад в **логарифм шансов**, не изменение PD в процентах. SHAP может распределять вклад между коррелированными производными признаками.
- Модель не обрабатывает пропущенные критически необходимые поля: вход валидируется Pydantic, а отсутствующие данные должен запрашивать workflow.

## 7. Файлы

```text
ml/
  schemas.py             Pydantic contract
  features.py            Одни и те же признаки для обучения и inference
  generate_dataset.py    Генератор синтетических исторических кредитов
  train.py               CatBoost + LogisticRegression; сохранение артефактов
  metrics.py             ROC-AUC, PR-AUC, Brier, LogLoss, calibration bins
  evaluate.py            Внешний запуск оценки по тестовой выборке
  inference.py           Scorer + объяснения по SHAP
  rule_baseline.py       Локальная экспертная эвристика, не PD
  demo.py                Пример заявки
decision/
  schemas.py             VerificationData, DecisionResult, StaffActionInput
  policy.json            Версионируемые демо-правила и условия продуктов
  policy.py              Загрузка и валидация конфигурации
  affordability.py       Аннуитет и допустимый платёж
  engine.py              Rule-based предварительное решение
  staff_actions.py       Валидация выбора сотрудника банка
  demo.py                Сквозной ML + Decision + staff пример
  README.md              Описание API, правил и ограничений
examples/
  fastapi_router.py      Для импорта в существующий FastAPI
  decision_router.py     POST /internal/decision/evaluate
  serve.py               Локальная пробная служба
models/
  model.cbm              Обученная модель CatBoost
  logreg_baseline.joblib Baseline (только локальное сравнение)
  metadata.json          Версия и схема признаков
  metrics.json           Метрики синтетического теста
```

Поля и единицы подробно перечислены в [DATA_DICTIONARY.md](DATA_DICTIONARY.md).
