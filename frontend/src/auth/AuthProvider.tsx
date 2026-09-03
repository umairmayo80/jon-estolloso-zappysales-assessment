import { createContext, useCallback, useContext, useMemo } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { authApi } from '../api/auth';
import type { Admin } from '../types';

interface AuthContextValue {
  admin: Admin | undefined;
  isLoading: boolean;
  login: (email: string, password: string) => Promise<Admin>;
  logout: () => Promise<void>;
  refresh: () => Promise<Admin>;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);
const authKey = ['auth', 'me'] as const;
type AuthState = Admin | null;

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const queryClient = useQueryClient();
  const meQuery = useQuery<AuthState>({
    queryKey: authKey,
    queryFn: authApi.me,
    retry: false,
    staleTime: 5 * 60 * 1000,
  });

  const loginMutation = useMutation({
    mutationFn: ({ email, password }: { email: string; password: string }) => authApi.login(email, password),
    onSuccess: (admin) => queryClient.setQueryData<AuthState>(authKey, admin),
  });

  const login = useCallback(
    async (email: string, password: string) => loginMutation.mutateAsync({ email, password }),
    [loginMutation],
  );

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } finally {
      // An active observer can retain its prior data after removeQueries().
      // Set an explicit signed-out state first so protected routes cannot bounce
      // back into the shell while navigation to /login is in progress.
      await queryClient.cancelQueries({ queryKey: authKey });
      queryClient.setQueryData<AuthState>(authKey, null);
      queryClient.removeQueries({ queryKey: ['users'] });
      queryClient.removeQueries({ queryKey: ['user'] });
      queryClient.removeQueries({ queryKey: ['address'] });
    }
  }, [queryClient]);

  const refresh = useCallback(async () => {
    const admin = await authApi.refresh();
    queryClient.setQueryData<AuthState>(authKey, admin);
    return admin;
  }, [queryClient]);

  const value = useMemo<AuthContextValue>(
    () => ({ admin: meQuery.data ?? undefined, isLoading: meQuery.isLoading, login, logout, refresh }),
    [login, logout, meQuery.data, meQuery.isLoading, refresh],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside AuthProvider.');
  return context;
}
