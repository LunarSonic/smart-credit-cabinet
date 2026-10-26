import { Navigate } from 'react-router-dom';
import { useAuth } from '../store/auth';
import type { Role } from '../api/types';
import { ErrorPlate } from './Feedback';

export function ProtectedRoute({ children, role }: { children: React.ReactNode; role?: Role }) {
  const auth = useAuth();
  if (!auth.token) return <Navigate to="/login" replace />;
  if (role && auth.role !== role) {
    return <div className="page"><ErrorPlate>Нет доступа. Требуется роль: {role}</ErrorPlate></div>;
  }
  return <>{children}</>;
}
