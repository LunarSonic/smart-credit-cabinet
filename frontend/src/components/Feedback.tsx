import type { ReactNode } from 'react';
import { Plate } from '@alfalab/core-components/plate';
import { Spinner } from '@alfalab/core-components/spinner';

export function PageLoader() {
  return <div className="center" role="status" aria-label="Загрузка"><Spinner visible preset={48} /></div>;
}

export function ErrorPlate({ children, title }: { children: ReactNode; title?: string }) {
  return <div role="alert" style={{ marginBottom: 16 }}><Plate view="negative" title={title}>{children}</Plate></div>;
}

export function InfoPlate({ children, title, view = 'common' }: { children: ReactNode; title?: string; view?: 'common' | 'attention' | 'positive' }) {
  return <Plate view={view} title={title}>{children}</Plate>;
}

export function Empty({ title, text, action }: { title: string; text?: string; action?: ReactNode }) {
  return (
    <div className="card" style={{ textAlign: 'center', padding: 48 }}>
      <h3 style={{ margin: '0 0 8px' }}>{title}</h3>
      {text && <p style={{ margin: '0 0 16px', color: 'var(--color-light-text-secondary)' }}>{text}</p>}
      {action}
    </div>
  );
}
