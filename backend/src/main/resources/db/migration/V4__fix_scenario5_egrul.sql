-- =============================================================================
-- V4  Исправление сценария 5 (client5 / Кузнецова):
--     ОКВЭД в mock_egrul меняется с 74.10 на 74.90, чтобы EgrulMockCheck
--     обнаружил расхождение (okved_matches=false) и вернул FAILED без
--     стоп-фактора. Заявка дойдёт до ML → fns_check=pending → needs_info
--     → бэкенд переводит в NEEDS_INFO (детерминированный сценарий).
--
--     Также добавляем оборотные данные по счёту, чтобы AccountCheck не
--     мешал прохождению конвейера.
-- =============================================================================

UPDATE mock_egrul
   SET okved = '74.90'
 WHERE inn = '770100000005';

INSERT INTO account_turnover (company_id, month, turnover_amount, avg_balance)
SELECT c.id, '2026-09-01', 155000, 58000
  FROM company c WHERE c.inn = '770100000005'
    ON CONFLICT (company_id, month) DO NOTHING;

INSERT INTO account_turnover (company_id, month, turnover_amount, avg_balance)
SELECT c.id, '2026-10-01', 162000, 61000
  FROM company c WHERE c.inn = '770100000005'
    ON CONFLICT (company_id, month) DO NOTHING;
