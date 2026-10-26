import type { ApplicationStatus, Purpose } from './api/types';

export const fmt = (n: number) =>
  new Intl.NumberFormat('ru-RU', { style: 'currency', currency: 'RUB', maximumFractionDigits: 0 }).format(n);
export const fmtDate = (s: string) => new Date(s).toLocaleDateString('ru-RU');
export const fmtDateTime = (s: string) => new Date(s).toLocaleString('ru-RU');

export const purposeLabel: Record<Purpose, string> = {
  WORKING_CAPITAL: 'Пополнение оборотных средств',
  EQUIPMENT: 'Покупка оборудования',
  CASH_GAP: 'Кассовый разрыв',
  REAL_ESTATE: 'Коммерческая недвижимость',
  REFINANCING: 'Рефинансирование',
  OTHER: 'Другое',
};
export const purposeOptions = (Object.entries(purposeLabel) as [Purpose, string][])
  .map(([key, content]) => ({ key, content }));

export type StatusColor = 'green' | 'orange' | 'red' | 'blue' | 'grey' | 'teal' | 'purple';
export const STATUS_VIEW: Record<ApplicationStatus, { text: string; color: StatusColor }> = {
  DRAFT: { text: 'Черновик', color: 'grey' },
  SUBMITTED: { text: 'Отправлена', color: 'blue' },
  COMPLETENESS_CHECK: { text: 'Проверка данных', color: 'blue' },
  NEEDS_INFO: { text: 'Нужны уточнения', color: 'orange' },
  DATA_CHECK: { text: 'Проверка данных', color: 'blue' },
  SCORING: { text: 'Оценка риска', color: 'blue' },
  MANUAL_REVIEW: { text: 'На рассмотрении', color: 'purple' },
  APPROVED: { text: 'Предложены условия', color: 'green' },
  REJECTED: { text: 'Отказано', color: 'red' },
  OFFER_DECLINED: { text: 'Условия отклонены', color: 'grey' },
  SIGNED: { text: 'Выдано', color: 'teal' },
  DISBURSED: { text: 'Выдано', color: 'teal' },
  CANCELLED: { text: 'Заявка отозвана', color: 'grey' },
  OFFER_EXPIRED: { text: 'Срок истёк', color: 'grey' },
};

export const POLLING_STATUSES: ApplicationStatus[] = ['SUBMITTED', 'COMPLETENESS_CHECK', 'DATA_CHECK', 'SCORING'];
export const CANCELLABLE_STATUSES: ApplicationStatus[] = ['DRAFT', 'SUBMITTED', 'NEEDS_INFO', 'MANUAL_REVIEW', 'APPROVED'];

export const onlyDigits = (v: string) => v.replace(/\D/g, '');
