import type { CurrencyCode, IsoDateString, MinorUnits } from "./api";
import type { Counterparty, TransactionStatus } from "./transaction";

/** POST /transfers/quote (proposed endpoint): fee and recipient lookup before confirming */
export interface TransferQuoteRequest {
  /** Recipient email or user code */
  recipient: string;
  amount: MinorUnits;
}

export interface TransferQuote {
  sender: Counterparty;
  recipient: Counterparty;
  amount: MinorUnits;
  fee: MinorUnits;
  totalAmount: MinorUnits;
  currency: CurrencyCode;
  expiresAt: IsoDateString;
}

/** POST /transfers */
export interface CreateTransferRequest {
  recipient: string;
  amount: MinorUnits;
  description?: string | undefined;
  idempotencyKey: string;
}

/** Response of POST /transfers, items of GET /transfers, GET /transfers/:id */
export interface Transfer {
  id: string;
  reference: string;
  status: TransactionStatus;
  amount: MinorUnits;
  fee: MinorUnits;
  totalAmount: MinorUnits;
  currency: CurrencyCode;
  sender: Counterparty;
  recipient: Counterparty;
  description: string | null;
  failureReason: string | null;
  transactionId: string;
  createdAt: IsoDateString;
  simulated: boolean;
}

/** POST /api/transfers, with amounts represented in integer sen. */
export interface SpringTransferRequest {
  recipientEmail: string;
  amount: MinorUnits;
  description: string | null;
  idempotencyKey: string;
}

export interface SpringTransferReceipt {
  transferReference: string;
  senderWalletId: number;
  recipientWalletId: number;
  amount: MinorUnits;
  currency: CurrencyCode;
  status: "COMPLETED";
  description: string | null;
  createdAt: IsoDateString;
}

export type SpringTransferResult =
  | { outcome: "confirmed"; receipt: SpringTransferReceipt }
  | { outcome: "rejected" | "unknown"; status: number; message: string };
