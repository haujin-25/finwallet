import { env } from "@/config/env";
import type { ForgotPasswordRequest, LoginRequest, RegisterRequest, User } from "@/types";
import { mockAuth } from "../mock/mock-auth";
import { ApiError } from "./api-error";
import { getSession, loginSession, logoutSession, registerSession } from "./auth-session";
import { tokenStorage } from "./token-storage";

export type SessionUser = User | Pick<User, "id" | "email" | "fullName">;

function unwrap<T>(
  result: { ok: true; data: T } | { ok: false; status: number; message: string },
): T {
  if (!result.ok) throw new ApiError(result.status, result.message);
  return result.data;
}

export const authService = {
  async login(req: LoginRequest): Promise<void> {
    if (env.useMockApi) {
      tokenStorage.setTokens((await mockAuth.login(req)).tokens);
    } else {
      unwrap(await loginSession({ data: req }));
    }
  },

  async register(req: RegisterRequest): Promise<void> {
    if (env.useMockApi) {
      tokenStorage.setTokens((await mockAuth.register(req)).tokens);
    } else {
      unwrap(await registerSession({ data: req }));
    }
  },

  async logout(): Promise<void> {
    if (!env.useMockApi) return logoutSession();
    const refreshToken = tokenStorage.getRefreshToken();
    try {
      if (refreshToken) await mockAuth.logout({ refreshToken });
    } finally {
      tokenStorage.clear();
    }
  },

  async getCurrentUser(): Promise<SessionUser | null> {
    if (env.useMockApi) {
      if (!tokenStorage.getAccessToken()) return null;
      try {
        return await mockAuth.me();
      } catch (error) {
        if (!(error instanceof ApiError) || error.status !== 401) throw error;
        tokenStorage.clear();
        return null;
      }
    }

    const result = await getSession();
    if (!result.ok && [401, 403, 404].includes(result.status)) return null;
    const user = unwrap(result);
    if (!user || user.status !== "ACTIVE") return null;
    return { id: String(user.id), email: user.email, fullName: user.fullName };
  },

  forgotPassword(req: ForgotPasswordRequest): Promise<{ message: string }> {
    if (!env.useMockApi) {
      return Promise.reject(new ApiError(501, "Password reset is currently unavailable."));
    }
    return mockAuth.forgotPassword(req);
  },
};
