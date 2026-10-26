import { Status } from '@alfalab/core-components/status';
import type { ApplicationStatus } from '../api/types';
import { STATUS_VIEW, type StatusColor } from '../utils';

export function StatusBadge({ status, label }: { status: ApplicationStatus; label?: string }) {
  const v = STATUS_VIEW[status];
  return <Chip color={v.color}>{label ?? v.text}</Chip>;
}

export function Chip({ color, children }: { color: StatusColor; children: React.ReactNode }) {
  return <Status color={color} view="muted-alt" shape="rounded" size={24} uppercase={false}>{children}</Status>;
}
