-- =============================================================================
-- V3  Расширение mock_bki: детальные данные просрочек для ML-скоринга
-- =============================================================================

ALTER TABLE mock_bki
    ADD COLUMN IF NOT EXISTS current_overdue  BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS overdue_count    INT     NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS max_overdue_days INT     NOT NULL DEFAULT 0;

-- Сценарий 4: ИП Орлов — просрочки в истории (не текущие), балл MEDIUM → MANUAL_REVIEW
UPDATE mock_bki SET overdue_count = 3, max_overdue_days = 60 WHERE inn = '770100000004';

-- Сценарий 2: ИП Волков — небольшая историческая просрочка
UPDATE mock_bki SET overdue_count = 1, max_overdue_days = 15 WHERE inn = '770100000002';
