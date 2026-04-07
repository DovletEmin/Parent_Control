// ── Auth ─────────────────────────────────────────────────────────────

export interface RegisterRequest {
  email: string;
  password: string;
  full_name?: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface TokenResponse {
  access_token: string;
  refresh_token: string;
  token_type: string;
}

export interface UserResponse {
  id: string;
  email: string;
  is_active: boolean;
  created_at: string;
}

export interface AuthResponse {
  user: UserResponse;
  tokens: TokenResponse;
}

// ── Device ───────────────────────────────────────────────────────────

export interface DeviceCreateRequest {
  name: string;
}

export interface DeviceResponse {
  id: string;
  user_id: string;
  name: string;
  device_model: string | null;
  android_version: string | null;
  is_online: boolean;
  is_paired: boolean;
  last_seen_at: string | null;
  created_at: string;
}

export interface DeviceWithPairingCodeResponse extends DeviceResponse {
  pairing_code: string | null;
}

// ── App Usage ────────────────────────────────────────────────────────

export interface AppUsageResponse {
  id: string;
  device_id: string;
  package_name: string;
  app_name: string;
  usage_seconds: number;
  date: string;
  started_at: string | null;
  ended_at: string | null;
  created_at: string;
}

export interface AppUsageStatsResponse {
  package_name: string;
  app_name: string;
  total_seconds: number;
  date: string;
}

// ── Call Log ─────────────────────────────────────────────────────────

export interface CallLogResponse {
  id: string;
  device_id: string;
  phone_number: string;
  contact_name: string | null;
  call_type: string;
  duration_seconds: number;
  called_at: string;
  created_at: string;
}

// ── Messages ─────────────────────────────────────────────────────────

export interface MessageResponse {
  id: string;
  device_id: string;
  sender: string;
  receiver: string | null;
  body: string;
  message_type: string;
  is_incoming: boolean;
  sent_at: string;
  created_at: string;
}

// ── Media ────────────────────────────────────────────────────────────

export interface MediaItem {
  id: string;
  device_id: string;
  file_type: string;
  file_path: string;
  thumbnail_path: string | null;
  original_filename: string | null;
  file_size: number;
  mime_type: string | null;
  created_at_device: string | null;
  uploaded_at: string;
}

export interface MediaListResponse {
  items: MediaItem[];
  total: number;
  page: number;
  page_size: number;
}

// ── Location ─────────────────────────────────────────────────────────

export interface LocationResponse {
  id: string;
  device_id: string;
  latitude: number;
  longitude: number;
  accuracy: number | null;
  altitude: number | null;
  speed: number | null;
  recorded_at: string;
  created_at: string;
}

export interface LocationLatestResponse {
  latitude: number;
  longitude: number;
  accuracy: number | null;
  altitude: number | null;
  speed: number | null;
  recorded_at: string;
}

// ── Commands ─────────────────────────────────────────────────────────

export type CommandType =
  | 'request_location'
  | 'request_camera'
  | 'play_sound'
  | 'sync_now'
  | 'lock_device'
  | 'block_app'
  | 'unblock_app';

export interface CommandCreateRequest {
  command_type: CommandType;
  payload?: string;
}

export interface CommandResponse {
  id: string;
  device_id: string;
  command_type: string;
  payload: string | null;
  status: string;
  created_at: string;
  executed_at: string | null;
}

// ── WebSocket ────────────────────────────────────────────────────────

export interface WsMessage {
  type: string;
  device_id?: string;
  status?: string;
  sdp?: string;
  candidate?: string;
  command_id?: string;
  latitude?: number;
  longitude?: number;
  accuracy?: number;
  recorded_at?: string;
  [key: string]: unknown;
}
