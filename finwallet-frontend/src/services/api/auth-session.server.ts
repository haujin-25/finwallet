import {
  getCookie,
  setCookie,
  deleteCookie,
  setResponseHeader,
} from "@tanstack/react-start/server";
import { z } from "zod";
import type { LoginRequest, RegisterRequest } from "@/types";

import { spring, type Result } from "./spring-client.server";

const sessionCookie = "finwallet_session";
const cookieOptions = () =>
  ({
    path: "/",
    httpOnly: true,
    sameSite: "lax",
    secure: process.env["NODE_ENV"] === "production",
  }) as const;

const springUser = z.object({
  id: z.number().int().positive(),
  email: z.string(),
  fullName: z.string(),
  status: z.string(),
});

const springLogin = z.object({
  accessToken: z.string().min(1),
  tokenType: z.literal("Bearer"),
  expiresIn: z.number().int().positive().safe(),
});

export async function registerAccount(input: RegisterRequest): Promise<Result<null>> {
  setResponseHeader("Cache-Control", "no-store");
  const result = await spring("/users/register", springUser, input);
  return result.ok ? { ok: true, data: null } : result;
}

export async function startSession(input: LoginRequest): Promise<Result<null>> {
  setResponseHeader("Cache-Control", "no-store");
  const result = await spring("/auth/login", springLogin, input);
  if (!result.ok) return result;

  const { accessToken, expiresIn } = result.data;
  setCookie(sessionCookie, accessToken, { ...cookieOptions(), maxAge: expiresIn });
  return { ok: true, data: null };
}

export function readSubject(token: string): string | null {
  try {
    const payload = token.split(".")[1];
    if (!payload) return null;
    const bytes = Uint8Array.from(atob(payload.replace(/-/g, "+").replace(/_/g, "/")), (char) =>
      char.charCodeAt(0),
    );
    const claims = JSON.parse(new TextDecoder().decode(bytes)) as { sub?: unknown };
    return typeof claims.sub === "string" && claims.sub.length <= 255 ? claims.sub : null;
  } catch {
    return null;
  }
}

export function getSessionToken() {
  return getCookie(sessionCookie);
}

export async function readSession() {
  setResponseHeader("Cache-Control", "no-store");
  const token = getSessionToken();
  const email = token && readSubject(token);
  if (!token || !email) return { ok: true, data: null } as const;

  // The subject only selects the lookup path. Spring verifies the JWT and account status.
  return spring(`/users/email/${encodeURIComponent(email)}`, springUser, undefined, token);
}

export function endSession() {
  setResponseHeader("Cache-Control", "no-store");
  deleteCookie(sessionCookie, cookieOptions());
}
