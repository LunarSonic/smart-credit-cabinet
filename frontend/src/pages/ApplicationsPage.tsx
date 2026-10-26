import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button } from '@alfalab/core-components/button';
import { Typography } from '@alfalab/core-components/typography';
import { getApplications } from '../api';
import type { ApplicationSummaryDto } from '../api/types';
import { Empty, ErrorPlate, PageLoader } from '../components/Feedback';
import { Chip, StatusBadge } from '../components/StatusBadge';
import { PageTitle } from '../components/InfoGrid';
import { fmt, fmtDate } from '../utils';

export function ApplicationsPage() {
  const [apps, setApps] = useState<ApplicationSummaryDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const navigate = useNavigate();

  useEffect(() => {
    let cancelled = false;
    getApplications()
      .then((d) => { if (!cancelled) setApps([...d].sort((a, b) => +new Date(b.updatedAt) - +new Date(a.updatedAt))); })
      .catch(() => { if (!cancelled) setError('Не удалось загрузить заявки.'); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  if (loading) return <PageLoader />;
  const open = (id: string) => navigate(`/applications/${id}`);

  return (
    <div className="page">
      <div className="page-header">
        <PageTitle>Мои заявки</PageTitle>
        <Button size={48} view="accent" onClick={() => navigate('/applications/new')}>Создать заявку</Button>
      </div>
      {error && <ErrorPlate>{error}</ErrorPlate>}
      {apps.length === 0 ? (
        <Empty title="Заявок пока нет" text="Выберите продукт и подайте первую заявку"
          action={<Button size={48} view="accent" onClick={() => navigate('/products')}>К продуктам</Button>} />
      ) : apps.map((a) => (
        <div key={a.id} className="card clickable" role="link" tabIndex={0}
          onClick={() => open(a.id)} onKeyDown={(e) => e.key === 'Enter' && open(a.id)}>
          <div className="page-header" style={{ marginBottom: 0 }}>
            <div>
              <Typography.Text view="primary-large" weight="bold" tag="div">{a.companyName}</Typography.Text>
              <Typography.Text view="primary-small" color="secondary" tag="div">{a.productName}</Typography.Text>
            </div>
            <div className="row">
              <Typography.Text view="primary-large" weight="bold">{fmt(a.amount)}</Typography.Text>
              <StatusBadge status={a.status} />
              {a.actionRequired && <Chip color="orange">Действие</Chip>}
              <Typography.Text view="primary-small" color="secondary">{fmtDate(a.updatedAt)}</Typography.Text>
            </div>
          </div>
        </div>
      ))}
    </div>
  );
}
