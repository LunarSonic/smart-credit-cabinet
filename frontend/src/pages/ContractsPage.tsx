import { useEffect, useState } from 'react';
import { getContracts } from '../api';
import type { ContractDto } from '../api/types';
import { Empty, ErrorPlate, PageLoader } from '../components/Feedback';
import { Chip } from '../components/StatusBadge';
import { PageTitle } from '../components/InfoGrid';
import { fmt, fmtDate, type StatusColor } from '../utils';

const STATUS: Record<string, { text: string; color: StatusColor }> = {
  ACTIVE: { text: 'Активен', color: 'green' },
  CLOSED: { text: 'Закрыт', color: 'grey' },
  PENDING_DISBURSEMENT: { text: 'Ожидает выдачи', color: 'blue' },
};

export function ContractsPage() {
  const [contracts, setContracts] = useState<ContractDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    getContracts()
      .then((d) => { if (!cancelled) setContracts(d); })
      .catch(() => { if (!cancelled) setError('Не удалось загрузить договоры.'); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  if (loading) return <PageLoader />;

  return (
    <div className="page">
      <div className="page-header"><PageTitle>Мои договоры</PageTitle></div>
      {error && <ErrorPlate>{error}</ErrorPlate>}
      {contracts.length === 0 ? (
        <Empty title="Нет активных договоров" text="Договоры появятся здесь после принятия условий кредита" />
      ) : (
        <div className="card table-wrap" style={{ padding: 0 }}>
          <table className="data">
            <thead><tr><th>Сумма</th><th>Срок</th><th>Ставка</th><th>Статус</th><th>Подписан</th><th>Выдан</th></tr></thead>
            <tbody>
              {contracts.map((c) => {
                const s = STATUS[c.status] ?? { text: c.status, color: 'grey' as StatusColor };
                return (
                  <tr key={c.id}>
                    <td><b>{fmt(c.amount)}</b>{c.simulated && <> <Chip color="purple">демо</Chip></>}</td>
                    <td>{c.termMonths} мес.</td>
                    <td>{c.rate}%</td>
                    <td><Chip color={s.color}>{s.text}</Chip></td>
                    <td>{fmtDate(c.signedAt)}</td>
                    <td>{c.disbursedAt ? fmtDate(c.disbursedAt) : 'Ожидается'}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
