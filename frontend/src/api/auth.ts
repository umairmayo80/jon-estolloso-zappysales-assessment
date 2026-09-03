import { apiClient } from './client';
import type { Admin } from '../types';

export const authApi = {
  me: async (): Promise<Admin> => (await apiClient.get<Admin>('/auth/me')).data,
  login: async (email: string, password: string): Promise<Admin> =>
    (await apiClient.post<{ admin: Admin }>('/auth/login', { email, password })).data.admin,
  refresh: async (): Promise<Admin> =>
    (await apiClient.post<{ admin: Admin }>('/auth/refresh')).data.admin,
  logout: async (): Promise<void> => {
    await apiClient.post<void>('/auth/logout');
  },
};
