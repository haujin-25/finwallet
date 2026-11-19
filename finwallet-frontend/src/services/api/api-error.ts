import type { ApiErrorBody } from "@/types";

export class ApiError extends Error {
  readonly status: number;
  readonly code?: string | undefined;

  constructor(status: number, message: string, code?: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
  }

  static fromBody(status: number, body: Partial<ApiErrorBody> | null): ApiError {
    const raw = body?.message;
    const message = Array.isArray(raw) ? raw.join(", ") : raw || "Something went wrong";
    return new ApiError(status, message, body?.code);
  }
}

export function getErrorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  if (error instanceof Error) return error.message;
  return "Something went wrong. Please try again.";
}
