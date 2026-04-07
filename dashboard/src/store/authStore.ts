import { create } from 'zustand';
import { auth, setTokens, clearTokens } from '../api/client';
import type { UserResponse } from '../types';

interface AuthState {
  user: UserResponse | null;
  loading: boolean;
  error: string | null;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string) => Promise<void>;
  logout: () => void;
  loadUser: () => Promise<void>;
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  loading: false,
  error: null,

  login: async (email, password) => {
    set({ loading: true, error: null });
    try {
      const res = await auth.login({ email, password });
      setTokens(res.tokens.access_token, res.tokens.refresh_token);
      set({ user: res.user, loading: false });
    } catch (e) {
      set({ loading: false, error: 'Неверный email или пароль' });
      throw e;
    }
  },

  register: async (email, password) => {
    set({ loading: true, error: null });
    try {
      const res = await auth.register({ email, password });
      setTokens(res.tokens.access_token, res.tokens.refresh_token);
      set({ user: res.user, loading: false });
    } catch (e) {
      set({ loading: false, error: 'Ошибка регистрации' });
      throw e;
    }
  },

  logout: () => {
    clearTokens();
    set({ user: null });
  },

  loadUser: async () => {
    set({ loading: true });
    try {
      const user = await auth.me();
      set({ user, loading: false });
    } catch {
      clearTokens();
      set({ user: null, loading: false });
    }
  },
}));
