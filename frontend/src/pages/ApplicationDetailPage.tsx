import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Button } from '@alfalab/core-components/button';
import { Typography } from '@alfalab/core-components/typography';
import { getApplication, cancelApplication, acceptOffer, declineOffer } from '../api';
import type { ApplicationDetailDto } from '../api/types';
import { ConfirmModal } from '../components/ConfirmModal';
import { ErrorPlate, PageLoader } from '../components/Feedback';
import { CardTitle, InfoGrid } from '../components/InfoGrid';
import { Progress } from '../components/Progress';
import { Chip, StatusBadge } from '../components/StatusBadge';
import { CANCELLABLE_STATUSES, POLLING_STATUSES, fmt, fmtDate, fmtDateTime, purposeLabel } from '../utils';

const actorLabel: Record<string, string> = { SYSTEM: 'Система', CLIENT: 'Клиент', EMPLOYEE: 'Сотрудник' };

export function ApplicationDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [detail, setDetail] = useState<ApplicationDetailDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [confirm, setConfirm] = useState<'cancel' | 'decline' | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const load = useCallback(async (silent = false) => {
    if (!id) return;
    if (!silent) setLoading(true);
    try { setDetail(await getApplication(id)); setError(''); }
    catch { setError('Не удалось загрузить данные заявки.'); }
    finally { if (!silent) setLoading(false); }
  }, [id]);

  useEffect(() => { load(false); }, [load]);

  // Пока конвейер работает, обновляем статус каждые 4 секунды
  useEffect(() => {
    if (detail && POLLING_STATUSES.includes(detail.summary.status)) timer.current = setTimeout(() => load(true), 4000);
    return () => { if (timer.current) clearTimeout(timer.current); };
  }, [detail, load]);

  const run = async (fn: () => Promise<void>, msg: string) => {
    setBusy(true);
    try { await fn(); } catch { setError(msg); } finally { setBusy(false); setConfirm(null); }
  };

  if (loading) return <PageLoader />;
  if (!detail) {
    return <div className="page"><Link to="/applications">← Назад</Link><div style={{ marginTop: 16 }}><ErrorPlate>{error || 'Заявка не найдена'}</ErrorPlate></div></div>;
  }

  const { summary, offer, issues, history } = detail;
  const open = issues.filter((i) => !i.resolved);

  return (
    <div className="page">
      <Link to="/applications">← Назад</Link>
      <div className="page-header" style={{ marginTop: 16 }}>
        <div>
          <Typography.Title tag="h1" view="medium" font="system" style={{ margin: '0 0 8px' }}>{summary.companyName}</Typography.Title>
          <div className="row">
            <StatusBadge status={summary.status} />
            {summary.actionRequired && <Chip color="orange">Требуется действие</Chip>}
            {POLLING_STATUSES.includes(summary.status) && <Chip color="blue">Обработка…</Chip>}
          </div>
        </div>
        {CANCELLABLE_STATUSES.includes(summary.status) && (
          <Button view="outlined" size={48} disabled={busy} onClick={() => setConfirm('cancel')}>Отозвать заявку</Button>
        )}
      </div>

      {error && <ErrorPlate>{error}</ErrorPlate>}
      <Progress status={summary.status} />

      {summary.status === 'APPROVED' && offer && (
        <div className="offer">
          <div>Банк одобрил кредит на условиях:</div>
          <div className="offer-amount">{fmt(offer.amount)}</div>
          <div>{offer.termMonths} мес. · {offer.rate}% годовых</div>
          <div style={{ fontSize: 13, opacity: .8, marginTop: 4 }}>Предложение действует до {fmtDate(offer.validUntil)}</div>
          {offer.termsChanged && <p>Условия изменены относительно первоначальной заявки</p>}
          {detail.clientReasons && detail.clientReasons.length > 0 && (
            <div style={{ marginTop: 12, fontSize: 14, opacity: .85 }}>
              <span style={{ fontWeight: 600 }}>Причины решения: </span>
              {detail.clientReasons.map((r) => r.replace(/\.+$/, '')).join(' ')}
            </div>
          )}
          <div className="row" style={{ marginTop: 16 }}>
            <Button size={48} view="secondary" loading={busy} className="offer-accept-btn"
              onClick={() => run(async () => { await acceptOffer(offer.id); navigate('/contracts'); }, 'Не удалось принять условия.')}>
              Принять условия
            </Button>
            <Button size={48} view="outlined" style={{ color: '#fff', borderColor: 'rgba(255,255,255,.6)' }}
              disabled={busy} onClick={() => setConfirm('decline')}>Отклонить</Button>
          </div>
        </div>
      )}

      {summary.status !== 'APPROVED' && detail.clientReasons && detail.clientReasons.length > 0 && (
        <div className="card" style={{ marginBottom: 16 }}>
          <span style={{ fontWeight: 600 }}>Причины решения: </span>
          {detail.clientReasons.map((r) => r.replace(/\.+$/, '')).join(' ')}
        </div>
      )}

      {summary.status === 'NEEDS_INFO' && open.length > 0 && (
        <div className="card" style={{ marginBottom: 16, borderLeft: '4px solid var(--color-light-text-attention)' }}>
          <CardTitle>Требуются уточнения</CardTitle>
          <div style={{ display: 'grid', gap: 12 }}>
            {open.map((i) => (
              <div key={i.id} style={{ display: 'grid', gap: 2 }}>
                <div style={{ fontWeight: 600 }}>{i.message}</div>
                {i.reason && <div style={{ fontSize: 13, color: 'var(--color-light-text-secondary)', fontStyle: 'normal' }}>{i.reason}</div>}
              </div>
            ))}
          </div>
          <div style={{ marginTop: 16 }}>
            <Link to={`/applications/new?editId=${summary.id}`}>Открыть форму редактирования →</Link>
          </div>
        </div>
      )}

      <div className="card">
        <CardTitle>Параметры кредита</CardTitle>
        <InfoGrid items={[
          ['Сумма', fmt(summary.amount)], ['Срок', `${summary.termMonths} мес.`],
          ['Цель', purposeLabel[summary.purpose] ?? summary.purpose], ['Продукт', summary.productName],
          ...(summary.submittedAt ? [['Дата подачи', fmtDate(summary.submittedAt)] as [string, string]] : []),
          ['Обновлено', fmtDate(summary.updatedAt)],
        ]} />
      </div>

      {detail.fullName && (
        <div className="card">
          <CardTitle>Контактные данные</CardTitle>
          <InfoGrid items={[
            ['ФИО', detail.fullName],
            ...(detail.phone ? [['Телефон', detail.phone] as [string, string]] : []),
            ...(detail.revenue6m != null ? [['Выручка за 6 мес.', fmt(detail.revenue6m)] as [string, string]] : []),
            ...(detail.monthlyExpenses != null ? [['Ежемесячные расходы', fmt(detail.monthlyExpenses)] as [string, string]] : []),
          ]} />
        </div>
      )}

      {summary.status !== 'NEEDS_INFO' && issues.length > 0 && (
        <div className="card">
          <CardTitle>Замечания</CardTitle>
          {issues.map((i) => (
            <div key={i.id} style={{ marginBottom: 12 }}>
              <div className="row"><b>{i.message}</b>{i.resolved && <Chip color="green">Решено</Chip>}</div>
              {i.reason && <Typography.Text view="primary-small" color="secondary" tag="div">{i.reason}</Typography.Text>}
            </div>
          ))}
        </div>
      )}

      {history.length > 0 && (
        <div className="card">
          <CardTitle>История рассмотрения</CardTitle>
          <ul className="history">
            {[...history].reverse().map((h) => (
              <li key={h.id}>
                <span className="dot" />
                <div style={{ flex: 1 }}>
                  <div className="row">
                    {h.fromStatus && <><StatusBadge status={h.fromStatus} /><span>→</span></>}
                    <StatusBadge status={h.toStatus} />
                    <Typography.Text view="primary-small" color="secondary" style={{ marginLeft: 'auto' }}>
                      {actorLabel[h.actorType] ?? h.actorType}
                    </Typography.Text>
                  </div>
                  {(h.reason || h.comment) && (
                    <div style={{ marginTop: 6 }}>
                      {h.reason && <Typography.Text view="primary-small" color="secondary" tag="div">{h.reason}</Typography.Text>}
                      {h.comment && (
                        <ul style={{ margin: '4px 0 0', paddingLeft: 0, listStyle: 'none' }}>
                          {h.comment.split('; ').map((item, idx) => (
                            <li key={idx}><Typography.Text view="primary-small" color="secondary">{item}</Typography.Text></li>
                          ))}
                        </ul>
                      )}
                    </div>
                  )}
                  <Typography.Text view="secondary-small" color="secondary" tag="div">{fmtDateTime(h.createdAt)}</Typography.Text>
                </div>
              </li>
            ))}
          </ul>
        </div>
      )}

      <ConfirmModal open={confirm === 'cancel'} title="Отозвать заявку?" text="После отзыва заявку нельзя будет вернуть в работу."
        confirmText="Отозвать" danger loading={busy} onClose={() => setConfirm(null)}
        onConfirm={() => run(async () => setDetail(await cancelApplication(id!)), 'Не удалось отозвать заявку.')} />
      <ConfirmModal open={confirm === 'decline'} title="Отклонить условия?" text="Банк больше не будет ждать вашего ответа по этому предложению."
        confirmText="Отклонить" danger loading={busy} onClose={() => setConfirm(null)}
        onConfirm={() => run(async () => { await declineOffer(offer!.id); await load(false); }, 'Не удалось отклонить условия.')} />
    </div>
  );
}
