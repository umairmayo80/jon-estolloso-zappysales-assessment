import { apiClient } from './client';
import type { Address, AddressInput, ApiResult, PageResponse, ProfileStatus, UserDetail, UserInput, UserSummary } from '../types';

export interface UserListParams {
  query?: string;
  status?: ProfileStatus;
  sort?: string;
  page?: number;
  size?: number;
}

function serializeListParams(params: UserListParams): string {
  const search = new URLSearchParams();
  if (params.query) search.set('query', params.query);
  search.set('status', params.status ?? 'active');
  search.set('sort', params.sort ?? 'lastName,asc');
  search.set('page', String(params.page ?? 0));
  search.set('size', String(params.size ?? 20));
  return search.toString();
}

const etagHeader = (etag: string) => ({ headers: { 'If-Match': etag } });

export const usersApi = {
  list: async (params: UserListParams): Promise<PageResponse<UserSummary>> =>
    (await apiClient.get<PageResponse<UserSummary>>(`/users?${serializeListParams(params)}`)).data,
  get: async (id: string): Promise<ApiResult<UserDetail>> => apiClient.get<UserDetail>(`/users/${id}`),
  create: async (input: UserInput): Promise<ApiResult<UserDetail>> => apiClient.post<UserDetail>('/users', input),
  update: async (id: string, input: UserInput, etag: string): Promise<ApiResult<UserDetail>> =>
    apiClient.patch<UserDetail>(`/users/${id}`, input, etagHeader(etag)),
  remove: async (id: string, etag: string): Promise<ApiResult<void>> => apiClient.delete<void>(`/users/${id}`, etagHeader(etag)),
  restore: async (id: string, etag: string): Promise<ApiResult<void>> =>
    apiClient.post<void>(`/users/${id}/restore`, undefined, etagHeader(etag)),
  createAddress: async (userId: string, input: AddressInput): Promise<ApiResult<Address>> =>
    apiClient.post<Address>(`/users/${userId}/addresses`, input),
  getAddress: async (userId: string, addressId: string): Promise<ApiResult<Address>> =>
    apiClient.get<Address>(`/users/${userId}/addresses/${addressId}`),
  updateAddress: async (userId: string, addressId: string, input: AddressInput, etag: string): Promise<ApiResult<Address>> =>
    apiClient.patch<Address>(`/users/${userId}/addresses/${addressId}`, input, etagHeader(etag)),
  removeAddress: async (userId: string, addressId: string, etag: string): Promise<ApiResult<void>> =>
    apiClient.delete<void>(`/users/${userId}/addresses/${addressId}`, etagHeader(etag)),
  restoreAddress: async (userId: string, addressId: string, etag: string): Promise<ApiResult<void>> =>
    apiClient.post<void>(`/users/${userId}/addresses/${addressId}/restore`, undefined, etagHeader(etag)),
};
