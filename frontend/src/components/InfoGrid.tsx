import type { ReactNode } from 'react';
import { Typography } from '@alfalab/core-components/typography';

export function InfoGrid({ items }: { items: [string, ReactNode][] }) {
  return (
    <div className="info-grid">
      {items.map(([label, value]) => (
        <div key={label}>
          <Typography.Text view="primary-small" color="secondary" tag="div">{label}</Typography.Text>
          <Typography.Text view="primary-medium" weight="medium" tag="div">{value}</Typography.Text>
        </div>
      ))}
    </div>
  );
}

export function PageTitle({ children }: { children: ReactNode }) {
  return <Typography.Title tag="h1" view="medium" font="system" style={{ margin: 0 }}>{children}</Typography.Title>;
}

export function CardTitle({ children }: { children: ReactNode }) {
  return <Typography.Title tag="h3" view="xsmall" font="system" className="card-title">{children}</Typography.Title>;
}
