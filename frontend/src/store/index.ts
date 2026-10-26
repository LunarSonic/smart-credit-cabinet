import { configureStore } from '@reduxjs/toolkit';
import { TypedUseSelectorHook, useDispatch, useSelector } from 'react-redux';
import auth from './authSlice';

export const store = configureStore({ reducer: { auth } });

// Токен читает axios-интерцептор (src/api/client.ts), поэтому дублируем состояние в localStorage
store.subscribe(() => {
  const { token, role, fullName, companyId } = store.getState().auth;
  try {
    if (token) {
      localStorage.setItem('token', token);
      localStorage.setItem('user', JSON.stringify({ role, fullName, companyId }));
    } else {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
    }
  } catch { /* приватный режим */ }
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
export const useAppDispatch: () => AppDispatch = useDispatch;
export const useAppSelector: TypedUseSelectorHook<RootState> = useSelector;
