import { useQueryClient } from '@tanstack/react-query';
import { createContext, type ReactNode, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { api, onSessionExpired, refreshSession, setAccessToken } from '../api/client';
import type { AuthResponse, AuthUser } from '../api/types';

type Status = 'loading' | 'authenticated' | 'anonymous';

export interface SignupInput {
  username: string;
  email: string;
  password: string;
  displayName: string;
}

interface AuthContextValue {
  status: Status;
  user: AuthUser | null;
  login: (identifier: string, password: string) => Promise<void>;
  signup: (input: SignupInput) => Promise<void>;
  logout: () => Promise<void>;
  /** Forgets the session locally, e.g. after the account was deleted and the server has nothing to log out. */
  forget: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<Status>('loading');
  const [user, setUser] = useState<AuthUser | null>(null);

  const start = useCallback((session: AuthResponse) => {
    setAccessToken(session.accessToken);
    setUser(session.user);
    setStatus('authenticated');
  }, []);

  const end = useCallback(() => {
    setAccessToken(null);
    setUser(null);
    setStatus('anonymous');
    queryClient.clear();
  }, [queryClient]);

  // Restore the session from the refresh cookie on first load.
  useEffect(() => {
    let active = true;
    refreshSession().then((session) => {
      if (!active) return;
      if (session) start(session);
      else setStatus('anonymous');
    });
    return () => {
      active = false;
    };
  }, [start]);

  useEffect(() => onSessionExpired(end), [end]);

  const value = useMemo<AuthContextValue>(
    () => ({
      status,
      user,
      login: async (identifier, password) =>
        start(await api<AuthResponse>('/api/auth/login', { method: 'POST', body: { identifier, password } })),
      signup: async (input) => start(await api<AuthResponse>('/api/auth/signup', { method: 'POST', body: input })),
      logout: async () => {
        try {
          await api<void>('/api/auth/logout', { method: 'POST' });
        } finally {
          end();
        }
      },
      forget: end,
    }),
    [status, user, start, end],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside AuthProvider');
  return context;
}
