import type { MinorUnits, PaymentMethod, TransactionStatus } from "@/types";

/**
 * Simulated server-side rules. These live in the mock backend, never in UI components,
 * and will be replaced entirely by the NestJS service.
 */
export const TRANSFER_MIN: MinorUnits = 100; // RM 1.00
export const TRANSFER_MAX: MinorUnits = 5_000_000; // RM 50,000.00
export const FREE_TRANSFER_LIMIT: MinorUnits = 50_000; // RM 500.00
export const TRANSFER_FEE: MinorUnits = 100; // RM 1.00
export const TRANSFER_REVIEW_THRESHOLD: MinorUnits = 1_000_000; // RM 10,000.00

export const DEPOSIT_MIN: MinorUnits = 1_000; // RM 10.00
export const DEPOSIT_MAX: MinorUnits = 3_000_000; // RM 30,000.00
export const DEBIT_CARD_LIMIT: MinorUnits = 2_000_000; // RM 20,000.00

export function calculateTransferFee(amount: MinorUnits): MinorUnits {
  return amount > FREE_TRANSFER_LIMIT ? TRANSFER_FEE : 0;
}

export interface Outcome {
  status: TransactionStatus;
  failureReason: string | null;
}

export function decideTransferOutcome(input: {
  amount: MinorUnits;
  totalAmount: MinorUnits;
  availableBalance: MinorUnits;
}): Outcome {
  if (input.totalAmount > input.availableBalance) {
    return { status: "FAILED", failureReason: "Insufficient available balance" };
  }
  if (input.amount >= TRANSFER_REVIEW_THRESHOLD) {
    return { status: "PENDING", failureReason: null };
  }
  return { status: "COMPLETED", failureReason: null };
}

export function decideDepositOutcome(input: { amount: MinorUnits; method: PaymentMethod }): Outcome {
  if (input.method === "DEBIT_CARD" && input.amount > DEBIT_CARD_LIMIT) {
    return { status: "FAILED", failureReason: "Declined by simulated card issuer: daily limit exceeded" };
  }
  if (input.method === "BANK_TRANSFER") return { status: "PENDING", failureReason: null };
  return { status: "COMPLETED", failureReason: null };
}
