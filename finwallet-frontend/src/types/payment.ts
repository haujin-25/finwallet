import type { CurrencyCode, IsoDateString, MinorUnits } from "./api";
import type { TransactionStatus } from "./transaction";

/** Simulated payment methods only. No real gateway or card data is ever collected. */
export type PaymentMethod = "ONLINE_BANKING" | "BANK_TRANSFER" | "DEBIT_CARD";

/** POST /payments/deposit */
export interface DepositRequest {
  amount: MinorUnits;
  method: PaymentMethod;
  /** Client-generated key so a retried request never creates a second deposit */
  idempotencyKey: string;
}

/** Response of POST /payments/deposit and GET /payments/:id */
export interface Payment {
  id: string;
  reference: string;
  amount: MinorUnits;
  fee: MinorUnits;
  currency: CurrencyCode;
  method: PaymentMethod;
  status: TransactionStatus;
  failureReason: string | null;
  transactionId: string;
  createdAt: IsoDateString;
  simulated: boolean;
}
