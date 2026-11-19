import type { z } from "zod";

export type Result<T> = { ok: true; data: T } | { ok: false; status: number; message: string };

export async function spring<T>(
  path: string,
  schema: z.ZodType<T>,
  body?: object,
  token?: string,
  expectedStatus?: number,
): Promise<Result<T>> {
  const baseUrl = process.env["FINWALLET_API_BASE_URL"] ?? "http://localhost:8080/api";
  let response: Response;
  try {
    response = await fetch(baseUrl.replace(/\/$/, "") + path, {
      method: body ? "POST" : "GET",
      headers: {
        Accept: "application/json, text/plain",
        ...(body && { "Content-Type": "application/json" }),
        ...(token && { Authorization: `Bearer ${token}` }),
      },
      ...(body && { body: JSON.stringify(body) }),
      redirect: "error",
      signal: AbortSignal.timeout(10_000),
    });
  } catch {
    return {
      ok: false,
      status: 0,
      message: "Can't reach the FinWallet server. Try again shortly.",
    };
  }
  if (!response.ok) {
    let message = "Something went wrong. Please try again.";
    if (response.status < 500) {
      const body = await response.text().catch(() => "");
      if (response.headers.get("content-type")?.includes("application/json")) {
        try {
          const error = JSON.parse(body) as { message?: unknown };
          if (typeof error.message === "string") message = error.message;
        } catch {
          // Use the fallback when the upstream body is malformed.
        }
      } else if (response.headers.get("content-type")?.startsWith("text/plain") && body.trim()) {
        message = body;
      }
    }
    return { ok: false, status: response.status, message };
  }
  if (expectedStatus !== undefined && response.status !== expectedStatus) {
    return { ok: false, status: 502, message: "FinWallet returned an unexpected status." };
  }
  try {
    return { ok: true, data: schema.parse(await response.json()) };
  } catch {
    return { ok: false, status: 502, message: "FinWallet returned an invalid response." };
  }
}
