import { setResponseHeader } from "@tanstack/react-start/server";
import { z } from "zod";
import type { CurrentWallet } from "@/types";
import { getSessionToken } from "./auth-session.server";
import { spring, type Result } from "./spring-client.server";

const sen = z.number().int().safe().nonnegative();
const walletResponse: z.ZodType<CurrentWallet> = z.object({
  walletId: z.number().int().safe().positive(),
  currency: z.literal("MYR"),
  balance: sen,
  status: z.enum(["ACTIVE", "FROZEN", "SUSPENDED", "CLOSED"]),
  reservedBalance: sen,
  availableBalance: sen,
});

export async function readMyWallet(): Promise<Result<CurrentWallet>> {
  setResponseHeader("Cache-Control", "no-store");
  const token = getSessionToken();
  if (!token) return { ok: false, status: 401, message: "Sign in to view your wallet." };

  const result = await spring("/wallets/me", walletResponse, undefined, token);
  if (result.ok) return result;

  const messages: Record<number, string> = {
    0: "Could not reach FinWallet. Try again shortly.",
    401: "Your session has ended. Sign in again.",
    403: "You do not have permission to view this wallet.",
    404: "There is no wallet associated with your account.",
    503: "The wallet service is temporarily unavailable. Try again shortly.",
  };
  return {
    ok: false,
    status: result.status,
    message: messages[result.status] ?? "We couldn't load your wallet. Try again shortly.",
  };
}
