import type { CurrencyCode, IsoDateString, MinorUnits } from "./api";
import type { PaymentMethod } from "./payment";

export type TransactionType = "DEPOSIT" | "TRANSFER_IN" | "TRANSFER_OUT";
export type TransactionStatus = "COMPLETED" | "PENDING" | "FAILED";
export type TransactionDirection = "CREDIT" | "DEBIT";

export interface Counterparty {
  name: string;
  /** Email, user code or payment channel label */
  identifier: string;
}

/** Item of GET /transactions and response of GET /transactions/:id */
export interface Transaction {
  id: string;
  reference: string;
  type: TransactionType;
  status: TransactionStatus;
  direction: TransactionDirection;
  amount: MinorUnits;
  fee: MinorUnits;
  /** amount + fee for debits, amount for credits */
  totalAmount: MinorUnits;
  currency: CurrencyCode;
  counterparty: Counterparty;
  description: string | null;
  paymentMethod: PaymentMethod | null;
  failureReason: string | null;
  createdAt: IsoDateString;
  completedAt: IsoDateString | null;
  /** True for every transaction in this MVP: nothing here is a real financial transaction */
  simulated: boolean;
}

/** Query string of GET /transactions */
export interface TransactionListParams {
  page?: number | undefined;
  pageSize?: number | undefined;
  search?: string | undefined;
  type?: TransactionType | undefined;
  status?: TransactionStatus | undefined;
  /** Inclusive start date, YYYY-MM-DD */
  from?: string | undefined;
  /** Inclusive end date, YYYY-MM-DD */
  to?: string | undefined;
}

/** Spring GET /api/transfers/me. Page indices from Spring are zero-based. */
export interface TransferHistoryItem {
  transferReference: string;
  direction: "INCOMING" | "OUTGOING";
  amount: MinorUnits;
  currency: CurrencyCode;
  status: TransactionStatus;
  description: string | null;
  counterpartyName: string | null;
  createdAt: IsoDateString;
  operationType: "TRANSFER" | "REVERSAL";
  reversalOfReference?: string | undefined;
}

export interface TransferHistoryPage {
  items: TransferHistoryItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}
