import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button } from '@alfalab/core-components/button';
import { Typography } from '@alfalab/core-components/typography';
import { getProducts, getPreapproved } from '../api';
import type { PreapprovedOfferDto, ProductDto } from '../api/types';
import { ErrorPlate, PageLoader } from '../components/Feedback';
import { Chip } from '../components/StatusBadge';
import { CardTitle, InfoGrid, PageTitle } from '../components/InfoGrid';
import { fmt, fmtDate, purposeLabel } from '../utils';

export function ProductsPage() {
  const [products, setProducts] = useState<ProductDto[]>([]);
  const [preapproved, setPreapproved] = useState<PreapprovedOfferDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const navigate = useNavigate();

  useEffect(() => {
    let cancelled = false;
    Promise.all([getProducts(), getPreapproved()])
      .then(([p, o]) => { if (!cancelled) { setProducts(p); setPreapproved(o); } })
      .catch(() => { if (!cancelled) setError('Не удалось загрузить данные. Попробуйте обновить страницу.'); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  if (loading) return <PageLoader />;

  return (
    <div className="page">
      <div className="page-header"><PageTitle>Кредитные продукты</PageTitle></div>
      {error && <ErrorPlate>{error}</ErrorPlate>}

      {preapproved.length > 0 && (
        <section>
          <Typography.Title tag="h2" view="small" font="system" style={{ margin: '0 0 16px' }}>Предодобренные предложения</Typography.Title>
          <div className="grid" style={{ marginBottom: 32 }}>
            {preapproved.map((o) => (
              <div key={o.id} className="card preapproved" style={{ marginBottom: 0 }}>
                <div style={{ opacity: .85 }}>{o.productName}</div>
                <div className="offer-amount">{fmt(o.maxAmount)}</div>
                <p>Ставка от {o.rateFrom}% · действует до {fmtDate(o.validUntil)}</p>
                <Button view="secondary" size={48}
                  onClick={() => navigate(`/applications/new?productId=${o.productId}&preapprovedOfferId=${o.id}`)}>
                  Начать заявку
                </Button>
              </div>
            ))}
          </div>
        </section>
      )}

      <Typography.Title tag="h2" view="small" font="system" style={{ margin: '24px 0 16px' }}>Все продукты</Typography.Title>
      <div className="grid">
        {products.map((p) => (
          <div key={p.id} className="card" style={{ marginBottom: 0, display: 'flex', flexDirection: 'column', gap: 16 }}>
            <div>
              <div style={{ marginBottom: 10 }}><Chip color="blue">{purposeLabel[p.suitablePurpose] ?? p.suitablePurpose}</Chip></div>
              <CardTitle>{p.name}</CardTitle>
              {p.description && <Typography.Text view="primary-small" color="secondary" tag="p">{p.description}</Typography.Text>}
            </div>
            <InfoGrid items={[
              ['Сумма', `${fmt(p.minAmount)} — ${fmt(p.maxAmount)}`],
              ['Срок', `${p.minTermMonths} — ${p.maxTermMonths} мес.`],
              ['Ставка от', `${p.baseRate}%`],
            ]} />
            <Button block size={48} view="accent" style={{ marginTop: 'auto' }}
              onClick={() => navigate(`/applications/new?productId=${p.id}`)}>
              Оформить заявку
            </Button>
          </div>
        ))}
      </div>
    </div>
  );
}
