import { setResponseHeader } from "@tanstack/react-start/server";
import { z } from "zod";
import type { TransferHistoryPage } from "@/types";
import { getSessionToken } from "./auth-session.server";
import { spring, type Result } from "./spring-client.server";

const integer = z.number().int().safe();
const pagination = z.object({ page: integer.nonnegative(), size: integer.min(1).max(100) });
const item = z.object({
  transferReference: z.string().min(1),
  direction: z.enum(["INCOMING", "OUTGOING"]),
  amount: integer.positive(),
  currency: z.literal("MYR"),
  status: z.enum(["COMPLETED", "FAILED"]),
  description: z.string().nullable(),
  counterpartyName: z.string().nullable(),
  createdAt: z.string().datetime({ offset: true }),
  operationType: z.enum(["TRANSFER", "REVERSAL"]),
  reversalOfReference: z.string().optional(),
});
const response: z.ZodType<TransferHistoryPage> = z.object({
  items: z.array(item),
  page: integer.nonnegative(),
  size: integer.min(1).max(100),
  totalElements: integer.nonnegative(),
  totalPages: integer.nonnegative(),
  hasNext: z.boolean(),
});

export async function readTransferHistory(input: unknown): Promise<Result<TransferHistoryPage>> {
  setResponseHeader("Cache-Control", "no-store");
  const token = getSessionToken();
  if (!token) return { ok: false, status: 401, message: "Sign in to view your transfers." };

  const parsed = pagination.safeParse(input);
  if (!parsed.success || parsed.data.page * parsed.data.size > 2_147_483_647) {
    return { ok: false, status: 400, message: "That transfer history page is unavailable." };
  }
  const { page, size } = parsed.data;
  const result = await spring(
    `/transfers/me?page=${page}&size=${size}`,
    response,
    undefined,
    token,
    200,
  );
  if (result.ok) return result;

  const messages: Record<number, string> = {
    0: "Could not reach FinWallet. Try again shortly.",
    400: "That transfer history page is unavailable.",
    401: "Your session has ended. Sign in again.",
    403: "You do not have permission to view transfer history.",
  };
  return {
    ok: false,
    status: result.status,
    message: messages[result.status] ?? "We couldn't load transfer history. Try again shortly.",
  };
}
