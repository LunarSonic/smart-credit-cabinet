import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button } from '@alfalab/core-components/button';
import { Pagination } from '@alfalab/core-components/pagination';
import { Segment, SegmentedControl } from '@alfalab/core-components/segmented-control';
import { bankGetQueue } from '../api';
import type { ApplicationSummaryDto } from '../api/types';
import { Empty, ErrorPlate, PageLoader } from '../components/Feedback';
import { PageTitle } from '../components/InfoGrid';
import { StatusBadge } from '../components/StatusBadge';
import { fmt, fmtDate } from '../utils';

const FILTERS = [
  { id: 'ALL', title: 'Все' },
  { id: 'MANUAL_REVIEW', title: 'На рассмотрении' },
  { id: 'NEEDS_INFO', title: 'Нужна информация' },
];
const PAGE_SIZE = 20;

export function BankQueuePage() {
  const [filter, setFilter] = useState('ALL');
  const [apps, setApps] = useState<ApplicationSummaryDto[]>([]);
  const [pages, setPages] = useState(0);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const navigate = useNavigate();

  const load = useCallback(async (f: string, p: number) => {
    setLoading(true); setError('');
    try {
      const d = await bankGetQueue(f === 'ALL' ? undefined : f, p, PAGE_SIZE);
      setApps(d.content); setPages(d.page.totalPages); setTotal(d.page.totalElements);
    } catch { setError('Не удалось загрузить очередь заявок.'); }
    finally { setLoading(false); }
  }, []);

  useEffect(() => { load(filter, page); }, [filter, page, load]);

  return (
    <div className="page">
      <div className="page-header">
        <PageTitle>Очередь заявок</PageTitle>
        {!loading && <span style={{ color: 'var(--color-light-text-secondary)' }}>Всего: {total}</span>}
      </div>

      <div style={{ marginBottom: 16 }}>
        <SegmentedControl selectedId={filter} onChange={(id) => { setFilter(String(id)); setPage(0); }}>
          {FILTERS.map((f) => <Segment key={f.id} id={f.id} title={f.title} />)}
        </SegmentedControl>
      </div>

      {error && <ErrorPlate>{error}</ErrorPlate>}
      {loading ? <PageLoader /> : apps.length === 0 ? <Empty title="Нет заявок по выбранному фильтру" /> : (
        <>
          <div className="card table-wrap" style={{ padding: 0 }}>
            <table className="data">
              <thead><tr><th>Компания</th><th>Продукт</th><th className="num">Сумма</th><th>Статус</th><th>Обновлено</th><th /></tr></thead>
              <tbody>
                {apps.map((a) => (
                  <tr key={a.id} className="clickable" onClick={() => navigate(`/bank/applications/${a.id}`)}>
                    <td><b>{a.companyName}</b></td>
                    <td>{a.productName}</td>
                    <td className="num"><b>{fmt(a.amount)}</b></td>
                    <td><StatusBadge status={a.status} /></td>
                    <td>{fmtDate(a.updatedAt)}</td>
                    <td className="num">
                      <Button size={40} view="accent" onClick={(e: React.MouseEvent) => { e.stopPropagation(); navigate(`/bank/applications/${a.id}`); }}>Открыть</Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {pages > 1 && (
            <div className="row" style={{ justifyContent: 'center', marginTop: 16 }}>
              <Pagination currentPageIndex={page} pagesCount={pages} onPageChange={setPage} />
            </div>
          )}
        </>
      )}
    </div>
  );
}
