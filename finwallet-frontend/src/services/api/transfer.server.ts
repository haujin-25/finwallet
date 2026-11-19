import { setResponseHeader } from "@tanstack/react-start/server";
import { z } from "zod";
import type { SpringTransferReceipt, SpringTransferResult } from "@/types";
import { getSessionToken, readSubject } from "./auth-session.server";
import { spring } from "./spring-client.server";
import { springTransferRequest } from "./transfer-contract";

const positiveInteger = z.number().int().safe().positive();
const transferReceipt: z.ZodType<SpringTransferReceipt> = z.object({
  transferReference: z.string().min(1),
  senderWalletId: positiveInteger,
  recipientWalletId: positiveInteger,
  amount: positiveInteger,
  currency: z.literal("MYR"),
  status: z.literal("COMPLETED"),
  description: z.string().nullable(),
  createdAt: z.string().datetime({ offset: true }),
});

const submission = z.object({
  accountEmail: z.string().email().max(255),
  request: springTransferRequest,
});

export async function sendTransfer(input: unknown): Promise<SpringTransferResult> {
  setResponseHeader("Cache-Control", "no-store");
  const parsed = submission.safeParse(input);
  if (!parsed.success) {
    return {
      outcome: "rejected",
      status: 400,
      message: "Check the recipient, amount and description.",
    };
  }

  const token = getSessionToken();
  const { request, accountEmail } = parsed.data;
  // This comparison prevents a stale tab from posting under a newly signed-in account.
  // Spring authenticates the token and chooses the sender.
  if (!token || readSubject(token) !== accountEmail) {
    return {
      outcome: "rejected",
      status: 401,
      message: "Sign in as the original sender to continue.",
    };
  }

  const result = await spring("/transfers", transferReceipt, request, token, 201);
  if (
    result.ok &&
    result.data.amount === request.amount &&
    result.data.description === request.description
  ) {
    return { outcome: "confirmed", receipt: result.data };
  }
  if (result.ok) {
    return {
      outcome: "unknown",
      status: 502,
      message: "The receipt did not match the reviewed transfer.",
    };
  }

  const { status, message } = result;
  switch (status) {
    case 400:
      return {
        outcome: "rejected",
        status,
        message:
          message === "Cannot transfer money to your own wallet"
            ? "Enter another FinWallet user's email."
            : "Spring rejected the transfer details. Check the recipient, amount and description.",
      };
    case 401:
      return {
        outcome: "rejected",
        status,
        message: "Your session has ended. Sign in again to continue.",
      };
    case 403:
      return {
        outcome: "rejected",
        status,
        message: "You do not have permission to send this transfer.",
      };
    case 404:
      return {
        outcome: "rejected",
        status,
        message:
          message === "Sender wallet not found"
            ? "Your wallet could not be found."
            : "The recipient or their wallet could not be found. Check the email address.",
      };
    case 409:
      if (
        message === "Sender wallet is not active" ||
        message === "Recipient wallet is not active"
      ) {
        return { outcome: "rejected", status, message: `${message}. Both wallets must be active.` };
      }
      return {
        outcome: "unknown",
        status,
        message:
          message === "Idempotency key already used for a different transfer"
            ? "This transfer key is already linked to different details. Check with support before sending again."
            : "FinWallet reported a conflict. Keep the original details and key.",
      };
    case 422:
      return {
        outcome: "rejected",
        status,
        message: "Insufficient available balance. Review the amount and try again.",
      };
    default:
      return { outcome: "unknown", status, message: "FinWallet could not confirm the transfer." };
  }
}
