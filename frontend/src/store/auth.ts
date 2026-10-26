import { useAppDispatch, useAppSelector } from './index';
import { loggedIn, loggedOut } from './authSlice';
import type { Role } from '../api/types';

/** Тот же интерфейс, что был у AuthContext, но поверх Redux */
export function useAuth() {
  const dispatch = useAppDispatch();
  const state = useAppSelector((s) => s.auth);
  return {
    ...state,
    login: (token: string, role: Role, fullName: string, companyId: string | null) =>
      dispatch(loggedIn({ token, role, fullName, companyId })),
    logout: () => dispatch(loggedOut()),
  };
}
