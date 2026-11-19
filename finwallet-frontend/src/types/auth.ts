import type { IsoDateString } from "./api";

export interface User {
  id: string;
  /** Public user code that others can send money to, e.g. FW-USR-1001 */
  userCode: string;
  fullName: string;
  email: string;
  phone: string | null;
  createdAt: IsoDateString;
}

/** POST /auth/register */
export interface RegisterRequest {
  fullName: string;
  email: string;
  password: string;
}

/** POST /auth/login */
export interface LoginRequest {
  email: string;
  password: string;
}

export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
  /** Access token lifetime in seconds */
  expiresIn: number;
}

/** Response of POST /auth/register and POST /auth/login */
export interface AuthResponse {
  user: User;
  tokens: AuthTokens;
}

/** POST /auth/refresh */
export interface RefreshTokenRequest {
  refreshToken: string;
}

/** POST /auth/logout */
export interface LogoutRequest {
  refreshToken: string;
}

/** POST /auth/forgot-password (proposed endpoint) */
export interface ForgotPasswordRequest {
  email: string;
}

/** PATCH /users/me (proposed endpoint) */
export interface UpdateProfileRequest {
  fullName: string;
  phone: string | null;
}

/** POST /users/me/password (proposed endpoint) */
export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

/** GET/PUT /users/me/notification-preferences (proposed endpoint) */
export interface NotificationPreferences {
  transactionAlerts: boolean;
  loginAlerts: boolean;
  monthlyStatements: boolean;
  productUpdates: boolean;
}
