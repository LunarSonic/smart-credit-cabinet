import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Button } from '@alfalab/core-components/button';
import { Input } from '@alfalab/core-components/input';
import { Select } from '@alfalab/core-components/select';
import { Textarea } from '@alfalab/core-components/textarea';
import { Typography } from '@alfalab/core-components/typography';
import { bankGetApplication, bankDecide } from '../api';
import type { BankApplicationDetailDto, ManualAction } from '../api/types';
import { ErrorPlate, PageLoader } from '../components/Feedback';
import { CardTitle, InfoGrid } from '../components/InfoGrid';
import { Chip, StatusBadge } from '../components/StatusBadge';
import { fmt, fmtDate, fmtDateTime, purposeLabel, type StatusColor } from '../utils';

const checkTypeLabel: Record<string, string> = {
  EGRUL: 'ЕГРЮЛ/ЕГРИП', BKI: 'БКИ', ACCOUNT: 'Обороты счёта', COMPLETENESS: 'Полнота данных',
};
const checkStatus: Record<string, { text: string; color: StatusColor }> = {
  OK: { text: 'Пройдена', color: 'green' },
  FAILED: { text: 'Не пройдена', color: 'red' },
  UNAVAILABLE: { text: 'Недоступна', color: 'orange' },
  PENDING: { text: 'В процессе', color: 'blue' },
};
const actionLabel: Record<ManualAction, string> = {
  APPROVE: 'Одобрить', REJECT: 'Отказать', CHANGE_TERMS: 'Одобрить на иных условиях', REQUEST_INFO: 'Запросить информацию',
};
const actionColor: Record<ManualAction, StatusColor> = {
  APPROVE: 'green', CHANGE_TERMS: 'green', REJECT: 'red', REQUEST_INFO: 'orange',
};
const actionOptions = (Object.entries(actionLabel) as [ManualAction, string][]).map(([key, content]) => ({ key, content }));
const DECISION_STATUSES = ['MANUAL_REVIEW', 'NEEDS_INFO'];

export function BankApplicationPage() {
  const { id } = useParams<{ id: string }>();
  const [data, setData] = useState<BankApplicationDetailDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [action, setAction] = useState<ManualAction>('APPROVE');
  const [comment, setComment] = useState('');
  const [amount, setAmount] = useState('');
  const [term, setTerm] = useState('');
  const [rate, setRate] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [decisionError, setDecisionError] = useState('');

  const load = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    try {
      const r = await bankGetApplication(id);
      setData(r);
      setAmount(String(r.detail.summary.amount));
      setTerm(String(r.detail.summary.termMonths));
      if (r.offer) setRate(String(r.offer.rate));
    } catch { setError('Не удалось загрузить данные заявки.'); }
    finally { setLoading(false); }
  }, [id]);

  useEffect(() => { load(); }, [load]);

  const decide = async () => {
    if (!id || !data) return;
    setDecisionError(''); setSubmitting(true);
    try {
      const req: Parameters<typeof bankDecide>[1] = {
        action, comment: comment.trim() || undefined, version: data.detail.summary.version,
      };
      if (action === 'APPROVE' || action === 'CHANGE_TERMS') {
        if (amount) req.amount = Number(amount);
        if (term) req.termMonths = Number(term);
        if (rate) req.rate = Number(rate);
      }
      setData(await bankDecide(id, req));
      setComment('');
    } catch { setDecisionError('Не удалось принять решение. Проверьте данные и попробуйте снова.'); }
    finally { setSubmitting(false); }
  };

  if (loading) return <PageLoader />;
  if (error || !data) {
    return <div className="page"><Link to="/bank/queue">← Назад к очереди</Link><div style={{ marginTop: 16 }}><ErrorPlate>{error || 'Заявка не найдена'}</ErrorPlate></div></div>;
  }

  const { detail, company, checks, manualDecisions } = data;
  const { summary } = detail;
  const needsTerms = action === 'APPROVE' || action === 'CHANGE_TERMS';

  return (
    <div className="page">
      <Link to="/bank/queue">← Назад к очереди</Link>
      <div className="page-header" style={{ marginTop: 16 }}>
        <div>
          <Typography.Title tag="h1" view="medium" font="system" style={{ margin: '0 0 8px' }}>{summary.companyName}</Typography.Title>
          <div className="row"><StatusBadge status={summary.status} /><b>{fmt(summary.amount)}</b></div>
        </div>
      </div>

      <div className="card">
        <CardTitle>Информация о компании</CardTitle>
        <InfoGrid items={[
          ['ИНН', company.inn], ['Наименование', company.name], ['ОКВЭД', company.okved],
          ['Руководитель', company.directorName], ['Дата регистрации', fmtDate(company.registeredAt)],
        ]} />
      </div>

      <div className="card">
        <CardTitle>Параметры заявки</CardTitle>
        <InfoGrid items={[
          ['Сумма', fmt(summary.amount)], ['Срок', `${summary.termMonths} мес.`],
          ['Цель', purposeLabel[summary.purpose] ?? summary.purpose], ['Продукт', summary.productName],
          ...(detail.revenue6m != null ? [['Выручка за 6 мес.', fmt(detail.revenue6m)] as [string, string]] : []),
          ...(detail.monthlyExpenses != null ? [['Ежемесячные расходы', fmt(detail.monthlyExpenses)] as [string, string]] : []),
          ...(detail.fullName ? [['Контактное лицо', detail.fullName] as [string, string]] : []),
          ...(detail.phone ? [['Телефон', detail.phone] as [string, string]] : []),
        ]} />
      </div>

      {checks.length > 0 && (
        <div className="card table-wrap">
          <CardTitle>Внешние проверки</CardTitle>
          <table className="data">
            <thead><tr><th>Проверка</th><th>Статус</th><th>Дата</th></tr></thead>
            <tbody>
              {checks.map((c) => {
                const s = checkStatus[c.status] ?? { text: c.status, color: 'grey' as StatusColor };
                return <tr key={c.id}><td>{checkTypeLabel[c.checkType] ?? c.checkType}</td><td><Chip color={s.color}>{s.text}</Chip></td><td>{fmtDateTime(c.checkedAt)}</td></tr>;
              })}
            </tbody>
          </table>
        </div>
      )}

      {detail.issues.length > 0 && (
        <div className="card">
          <CardTitle>Замечания</CardTitle>
          {detail.issues.map((i) => (
            <div key={i.id} style={{ marginBottom: 12 }}>
              <div className="row"><b>{i.message}</b>{i.resolved && <Chip color="green">Решено</Chip>}</div>
              {i.reason && <Typography.Text view="primary-small" color="secondary" tag="div">{i.reason}</Typography.Text>}
            </div>
          ))}
        </div>
      )}

      {DECISION_STATUSES.includes(summary.status) && (
        <div className="card stack">
          <CardTitle>Принять решение</CardTitle>
          {decisionError && <ErrorPlate>{decisionError}</ErrorPlate>}
          <Select block size={56} label="Решение" labelView="outer" options={actionOptions} selected={action}
            onChange={({ selected }) => selected && setAction(selected.key as ManualAction)} />
          {needsTerms && (
            <div className="terms-grid">
              <Input block size={56} label="Сумма, ₽" labelView="outer" inputMode="numeric" value={amount}
                onChange={(_, { value }) => setAmount(value.replace(/\D/g, ''))} />
              <Input block size={56} label="Срок, мес." labelView="outer" inputMode="numeric" value={term}
                onChange={(_, { value }) => setTerm(value.replace(/\D/g, ''))} />
              <Input block size={56} label="Ставка, %" labelView="outer" inputMode="decimal" value={rate}
                onChange={(_, { value }) => setRate(value.replace(/[^\d.]/g, ''))} />
            </div>
          )}
          <Textarea block label="Комментарий" labelView="outer" placeholder="Комментарий к решению…" value={comment}
            onChange={(_, { value }) => setComment(value)} />
          <Button size={56} view="accent" loading={submitting} onClick={decide}>Принять решение</Button>
        </div>
      )}

      {manualDecisions.length > 0 && (
        <div className="card">
          <CardTitle>История решений</CardTitle>
          <ul className="history">
            {[...manualDecisions].reverse().map((d) => (
              <li key={d.id}>
                <span className="dot" />
                <div>
                  <div className="row"><Chip color={actionColor[d.action]}>{actionLabel[d.action]}</Chip>
                    <Typography.Text view="primary-small" color="secondary">{fmtDateTime(d.decidedAt)}</Typography.Text></div>
                  {d.comment && <Typography.Text view="primary-small" color="secondary" tag="p">{d.comment}</Typography.Text>}
                </div>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
