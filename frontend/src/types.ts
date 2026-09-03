export type ProfileStatus = 'active' | 'deleted' | 'all';

export interface Admin {
  id: string;
  email: string;
  displayName: string;
  role: 'ADMIN';
}

export interface Address {
  id: string;
  label: string;
  line1: string;
  line2?: string | null;
  city: string;
  region?: string | null;
  postalCode?: string | null;
  countryCode: string;
  primary: boolean;
  displayOrder: number;
  deleted: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface UserSummary {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  addressCount: number;
  deleted: boolean;
  version: number;
  updatedAt: string;
}

export interface UserDetail {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  deleted: boolean;
  deletedAt?: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
  addresses: Address[];
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  sort: string;
}

export interface ProblemDetailResponse {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  instance?: string;
  code?: string;
  traceId?: string;
  fieldErrors?: Record<string, string>;
}

export interface ApiResult<T> {
  data: T;
  etag: string | null;
}

export interface UserInput {
  email: string;
  firstName: string;
  lastName: string;
}

export interface AddressInput {
  label: string;
  line1: string;
  line2?: string;
  city: string;
  region?: string;
  postalCode?: string;
  countryCode: string;
  primary: boolean;
  displayOrder?: number;
}

export interface NavigationState {
  returnTo?: string;
  scrollY?: number;
  restoreScroll?: number;
}
