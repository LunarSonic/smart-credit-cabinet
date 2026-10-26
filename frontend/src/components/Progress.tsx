import type { ApplicationStatus } from '../api/types';

const STEPS = ['Подана', 'Проверка данных', 'Оценка риска', 'Решение', 'Подписание', 'Выдача'];
const INDEX: Record<ApplicationStatus, number> = {
  DRAFT: -1, SUBMITTED: 0, COMPLETENESS_CHECK: 1, NEEDS_INFO: 1, DATA_CHECK: 1, SCORING: 2, MANUAL_REVIEW: 2,
  APPROVED: 3, REJECTED: 3, OFFER_DECLINED: 3, OFFER_EXPIRED: 3, CANCELLED: 3, SIGNED: 5, DISBURSED: 6,
};
const BAD: ApplicationStatus[] = ['REJECTED', 'OFFER_DECLINED', 'OFFER_EXPIRED', 'CANCELLED'];

/** Клиентский трекер этапов заявки */
export function Progress({ status }: { status: ApplicationStatus }) {
  const idx = INDEX[status];
  return (
    <div className="progress" aria-label="Этапы заявки">
      {STEPS.map((s, k) => {
        const cls = k < idx ? 'done' : k === idx ? (BAD.includes(status) ? 'bad' : 'current') : '';
        return <div key={s} className={`progress-step ${cls}`}>{s}</div>;
      })}
    </div>
  );
}
