import type { PaymentMethod, TransactionStatus, TransactionType, WalletStatus } from "@/types";

export const TRANSACTION_TYPE_LABEL: Record<TransactionType, string> = {
  DEPOSIT: "Deposit",
  TRANSFER_IN: "Transfer in",
  TRANSFER_OUT: "Transfer out",
};

export const TRANSACTION_STATUS_LABEL: Record<TransactionStatus, string> = {
  COMPLETED: "Completed",
  PENDING: "Pending",
  FAILED: "Failed",
};

export const WALLET_STATUS_LABEL: Record<WalletStatus, string> = {
  ACTIVE: "Active",
  FROZEN: "Frozen",
  SUSPENDED: "Suspended",
  CLOSED: "Closed",
};

export const PAYMENT_METHOD_LABEL: Record<PaymentMethod, string> = {
  ONLINE_BANKING: "Online Banking",
  BANK_TRANSFER: "Bank Transfer",
  DEBIT_CARD: "Debit Card",
};

export function initialsOf(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? "")
    .join("");
}
