import type {
  AuthResponse,
  LoginRequest,
  RegisterRequest,
  TokenResponse,
  DeviceResponse,
  DeviceWithPairingCodeResponse,
  DeviceCreateRequest,
  AppUsageResponse,
  AppUsageStatsResponse,
  CallLogResponse,
  MessageResponse,
  MediaListResponse,
  LocationResponse,
  LocationLatestResponse,
  CommandResponse,
  CommandCreateRequest,
} from '../types';

const BASE = '/api/v1';

let accessToken: string | null = localStorage.getItem('access_token');
let refreshToken: string | null = localStorage.getItem('refresh_token');

export function setTokens(access: string, refresh: string) {
  accessToken = access;
  refreshToken = refresh;
  localStorage.setItem('access_token', access);
  localStorage.setItem('refresh_token', refresh);
}

export function clearTokens() {
  accessToken = null;
  refreshToken = null;
  localStorage.removeItem('access_token');
  localStorage.removeItem('refresh_token');
}

export function getAccessToken() {
  return accessToken;
}

async function request<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const headers: Record<string, string> = {
    ...(options.headers as Record<string, string>),
  };

  if (accessToken) {
    headers['Authorization'] = `Bearer ${accessToken}`;
  }

  if (!(options.body instanceof FormData)) {
    headers['Content-Type'] = 'application/json';
  }

  const res = await fetch(`${BASE}${path}`, { ...options, headers });

  if (res.status === 401 && refreshToken) {
    const refreshed = await tryRefresh();
    if (refreshed) {
      headers['Authorization'] = `Bearer ${accessToken}`;
      const retry = await fetch(`${BASE}${path}`, { ...options, headers });
      if (!retry.ok) throw new ApiError(retry.status, await retry.text());
      if (retry.status === 204) return undefined as T;
      return retry.json();
    }
    clearTokens();
    window.location.href = '/login';
    throw new ApiError(401, 'Session expired');
  }

  if (!res.ok) {
    const body = await res.text();
    throw new ApiError(res.status, body);
  }

  if (res.status === 204) return undefined as T;
  return res.json();
}

async function tryRefresh(): Promise<boolean> {
  try {
    const res = await fetch(`${BASE}/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refresh_token: refreshToken }),
    });
    if (!res.ok) return false;
    const data: TokenResponse = await res.json();
    setTokens(data.access_token, data.refresh_token);
    return true;
  } catch {
    return false;
  }
}

export class ApiError extends Error {
  status: number;
  body: string;
  constructor(status: number, body: string) {
    super(`API Error ${status}`);
    this.status = status;
    this.body = body;
  }
}

// ── Auth ─────────────────────────────────────────────────────────────

export const auth = {
  register: (data: RegisterRequest) =>
    request<AuthResponse>('/auth/register', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  login: (data: LoginRequest) =>
    request<AuthResponse>('/auth/login', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  me: () => request<AuthResponse['user']>('/auth/me'),
};

// ── Devices ──────────────────────────────────────────────────────────

export const devices = {
  list: () => request<DeviceResponse[]>('/devices'),

  get: (id: string) => request<DeviceResponse>(`/devices/${id}`),

  create: (data: DeviceCreateRequest) =>
    request<DeviceWithPairingCodeResponse>('/devices', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  remove: (id: string) =>
    request<void>(`/devices/${id}`, { method: 'DELETE' }),
};

// ── App Usage ────────────────────────────────────────────────────────

export const appUsage = {
  list: (deviceId: string, params?: { date_from?: string; date_to?: string; page?: number }) => {
    const q = new URLSearchParams();
    if (params?.date_from) q.set('date_from', params.date_from);
    if (params?.date_to) q.set('date_to', params.date_to);
    if (params?.page) q.set('page', String(params.page));
    return request<AppUsageResponse[]>(`/devices/${deviceId}/apps?${q}`);
  },

  stats: (deviceId: string, params?: { date_from?: string; date_to?: string }) => {
    const q = new URLSearchParams();
    if (params?.date_from) q.set('date_from', params.date_from);
    if (params?.date_to) q.set('date_to', params.date_to);
    return request<AppUsageStatsResponse[]>(`/devices/${deviceId}/apps/usage?${q}`);
  },
};

// ── Call Logs ────────────────────────────────────────────────────────

export const calls = {
  list: (deviceId: string, params?: { call_type?: string; date_from?: string; date_to?: string; page?: number }) => {
    const q = new URLSearchParams();
    if (params?.call_type) q.set('call_type', params.call_type);
    if (params?.date_from) q.set('date_from', params.date_from);
    if (params?.date_to) q.set('date_to', params.date_to);
    if (params?.page) q.set('page', String(params.page));
    return request<CallLogResponse[]>(`/devices/${deviceId}/calls?${q}`);
  },
};

// ── Messages ─────────────────────────────────────────────────────────

export const messages = {
  list: (deviceId: string, params?: { message_type?: string; contact?: string; date_from?: string; date_to?: string; page?: number }) => {
    const q = new URLSearchParams();
    if (params?.message_type) q.set('message_type', params.message_type);
    if (params?.contact) q.set('contact', params.contact);
    if (params?.date_from) q.set('date_from', params.date_from);
    if (params?.date_to) q.set('date_to', params.date_to);
    if (params?.page) q.set('page', String(params.page));
    return request<MessageResponse[]>(`/devices/${deviceId}/messages?${q}`);
  },
};

// ── Media ────────────────────────────────────────────────────────────

export const media = {
  list: (deviceId: string, params?: { file_type?: string; page?: number }) => {
    const q = new URLSearchParams();
    if (params?.file_type) q.set('file_type', params.file_type);
    if (params?.page) q.set('page', String(params.page));
    return request<MediaListResponse>(`/devices/${deviceId}/media?${q}`);
  },

  downloadUrl: (mediaId: string) => `${BASE}/media/${mediaId}/download`,
  thumbnailUrl: (mediaId: string) => `${BASE}/media/${mediaId}/thumbnail`,
};

// ── Location ─────────────────────────────────────────────────────────

export const location = {
  history: (deviceId: string, params?: { date_from?: string; date_to?: string; page?: number }) => {
    const q = new URLSearchParams();
    if (params?.date_from) q.set('date_from', params.date_from);
    if (params?.date_to) q.set('date_to', params.date_to);
    if (params?.page) q.set('page', String(params.page));
    return request<LocationResponse[]>(`/devices/${deviceId}/location?${q}`);
  },

  latest: (deviceId: string) =>
    request<LocationLatestResponse>(`/devices/${deviceId}/location/latest`),
};

// ── Commands ─────────────────────────────────────────────────────────

export const commands = {
  send: (deviceId: string, data: CommandCreateRequest) =>
    request<CommandResponse>(`/devices/${deviceId}/commands`, {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  list: (deviceId: string, params?: { page?: number }) => {
    const q = new URLSearchParams();
    if (params?.page) q.set('page', String(params.page));
    return request<CommandResponse[]>(`/devices/${deviceId}/commands?${q}`);
  },
};
