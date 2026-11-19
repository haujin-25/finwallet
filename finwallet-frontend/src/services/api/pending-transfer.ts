import type { SpringTransferRequest } from "@/types";
import { springTransferRequest } from "./transfer-contract";

function storageKey(userId: string) {
  return `finwallet.pending-transfer.${userId}`;
}

export function readPendingTransfer(userId: string): SpringTransferRequest | null {
  const saved = sessionStorage.getItem(storageKey(userId));
  return saved === null ? null : springTransferRequest.parse(JSON.parse(saved));
}

export function savePendingTransfer(userId: string, request: SpringTransferRequest) {
  const pending = readPendingTransfer(userId);
  if (pending && JSON.stringify(pending) !== JSON.stringify(request)) {
    throw new Error("Another transfer is awaiting confirmation.");
  }
  sessionStorage.setItem(storageKey(userId), JSON.stringify(request));
}

export function clearPendingTransfer(userId: string, idempotencyKey: string) {
  if (readPendingTransfer(userId)?.idempotencyKey === idempotencyKey) {
    sessionStorage.removeItem(storageKey(userId));
  }
}
