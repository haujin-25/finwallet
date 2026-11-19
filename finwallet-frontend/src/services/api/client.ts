import { env } from "@/config/env";
import { ApiError } from "./api-error";

type QueryValue = string | number | boolean | null | undefined;

export interface RequestOptions {
  query?: Record<string, QueryValue>;
  body?: unknown | undefined;
  headers?: Record<string, string>;
  signal?: AbortSignal | undefined;
}

function buildUrl(path: string, query?: Record<string, QueryValue>): string {
  const url = new URL(`${env.apiBaseUrl.replace(/\/$/, "")}${path}`);
  if (query) {
    for (const [key, value] of Object.entries(query)) {
      if (value !== undefined && value !== null && value !== "") url.searchParams.set(key, String(value));
    }
  }
  return url.toString();
}

async function request<T>(method: string, path: string, options: RequestOptions = {}): Promise<T> {
  const { query, body, headers = {}, signal } = options;
  const finalHeaders: Record<string, string> = { Accept: "application/json", ...headers };
  if (body !== undefined) finalHeaders["Content-Type"] = "application/json";
  let response: Response;
  try {
    response = await fetch(buildUrl(path, query), {
      method,
      headers: finalHeaders,
      body: body === undefined ? null : JSON.stringify(body),
      signal: signal ?? null,
    });
  } catch {
    throw new ApiError(0, "Can't reach the FinWallet server. Check your connection and try again.");
  }

  if (!response.ok) {
    const errorBody = await response.json().catch(() => null);
    throw ApiError.fromBody(response.status, errorBody);
  }

  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

/** Legacy transport for service contracts that have yet to migrate to Spring. */
export const apiClient = {
  get: <T>(path: string, options?: Omit<RequestOptions, "body">) => request<T>("GET", path, options),
  post: <T>(path: string, body?: unknown, options?: RequestOptions) => request<T>("POST", path, { ...options, body }),
  put: <T>(path: string, body?: unknown, options?: RequestOptions) => request<T>("PUT", path, { ...options, body }),
  patch: <T>(path: string, body?: unknown, options?: RequestOptions) => request<T>("PATCH", path, { ...options, body }),
  delete: <T>(path: string, options?: RequestOptions) => request<T>("DELETE", path, options),
};
