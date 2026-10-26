import { createSlice, PayloadAction } from '@reduxjs/toolkit';
import type { Role } from '../api/types';

export interface AuthState {
  token: string | null;
  role: Role | null;
  fullName: string | null;
  companyId: string | null;
}

const empty: AuthState = { token: null, role: null, fullName: null, companyId: null };

function load(): AuthState {
  try {
    const user = localStorage.getItem('user');
    const token = localStorage.getItem('token');
    if (user && token) return { ...empty, token, ...JSON.parse(user) };
  } catch { /* повреждённое хранилище — считаем, что не авторизован */ }
  return empty;
}

const slice = createSlice({
  name: 'auth',
  initialState: load(),
  reducers: {
    loggedIn: (_s, { payload }: PayloadAction<AuthState>) => payload,
    loggedOut: () => empty,
  },
});
export const { loggedIn, loggedOut } = slice.actions;
export default slice.reducer;
