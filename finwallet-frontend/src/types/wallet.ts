import type { CurrencyCode, IsoDateString, MinorUnits } from "./api";

export type WalletStatus = "ACTIVE" | "FROZEN" | "SUSPENDED" | "CLOSED";

/** Spring GET /api/wallets/me. All three balances are integer sen. */
export interface CurrentWallet {
  walletId: number;
  currency: CurrencyCode;
  balance: MinorUnits;
  status: WalletStatus;
  reservedBalance: MinorUnits;
  availableBalance: MinorUnits;
}

/** Demo wallet with fields used by the simulated money flows. */
export interface Wallet {
  id: string;
  /** Human-readable wallet number, e.g. FW 8823 4410 2291 */
  walletNumber: string;
  userId: string;
  currency: CurrencyCode;
  /** Ledger balance: all completed transactions */
  balance: MinorUnits;
  /** Balance minus holds from pending outgoing transactions */
  availableBalance: MinorUnits;
  status: WalletStatus;
  createdAt: IsoDateString;
  updatedAt: IsoDateString;
}

/** GET /wallets/me/balance */
export interface WalletBalance {
  walletId: string;
  currency: CurrencyCode;
  balance: MinorUnits;
  availableBalance: MinorUnits;
  asOf: IsoDateString;
}

/** GET /wallets/me/summary?days=30 (proposed endpoint) */
export interface WalletSummary {
  periodDays: number;
  currency: CurrencyCode;
  moneyIn: MinorUnits;
  moneyOut: MinorUnits;
  completedCount: number;
  pendingCount: number;
  failedCount: number;
}
