import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { api, ApiError, refreshCsrf } from '@/api/client';
import type { components } from '@/api/schema';
type CurrentUser = components['schemas']['CurrentUser'];
type Auth = {
  user: CurrentUser | null;
  loading: boolean;
  error: Error | null;
  reload: () => Promise<void>;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
};
const Context = createContext<Auth | null>(null);
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const query = useQueryClient();
  async function reload() {
    setLoading(true);
    setError(null);
    try {
      await refreshCsrf();
      const { data } = await api.GET('/api/v1/auth/me');
      setUser(data ?? null);
    } catch (e) {
      setUser(null);
      if (!(e instanceof ApiError && e.status === 401))
        setError(e instanceof Error ? e : new Error('Unable to load your session.'));
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => {
    let active = true;
    async function load() {
      try {
        await refreshCsrf();
        const { data } = await api.GET('/api/v1/auth/me');
        if (active) setUser(data ?? null);
      } catch (e) {
        if (active && !(e instanceof ApiError && e.status === 401))
          setError(e instanceof Error ? e : new Error('Unable to load your session.'));
      } finally {
        if (active) setLoading(false);
      }
    }
    void load();
    const expire = () => {
      setUser(null);
      query.clear();
    };
    window.addEventListener('serms:unauthenticated', expire);
    return () => {
      active = false;
      window.removeEventListener('serms:unauthenticated', expire);
    };
  }, [query]);
  async function login(email: string, password: string) {
    const { data } = await api.POST('/api/v1/auth/login', { body: { email, password } });
    query.clear();
    setUser(data ?? null);
    setError(null);
    await refreshCsrf();
  }
  async function logout() {
    await api.POST('/api/v1/auth/logout');
    setUser(null);
    query.clear();
    await refreshCsrf();
  }
  return (
    <Context.Provider value={{ user, loading, error, reload, login, logout }}>
      {children}
    </Context.Provider>
  );
}
// This context is intentionally exported independently of feature pages.
export function useAuth() {
  const context = useContext(Context);
  if (!context) throw new Error('AuthProvider is required');
  return context;
}
