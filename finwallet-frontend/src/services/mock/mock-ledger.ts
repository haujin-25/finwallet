import type { MinorUnits, Wallet } from "@/types";
import { mockError } from "./mock-utils";
import type { MockDb, MockTransactionRecord, MockUserRecord, MockWalletRecord } from "./mock-db";
import { tokenStorage } from "../api/token-storage";

/** Balances are derived from the transaction ledger, never stored or edited directly. */
export function computeBalances(transactions: MockTransactionRecord[]): {
  balance: MinorUnits;
  availableBalance: MinorUnits;
} {
  let balance = 0;
  let holds = 0;
  for (const tx of transactions) {
    if (tx.status === "COMPLETED") {
      balance += tx.direction === "CREDIT" ? tx.amount : -tx.totalAmount;
    } else if (tx.status === "PENDING" && tx.direction === "DEBIT") {
      holds += tx.totalAmount;
    }
  }
  return { balance, availableBalance: balance - holds };
}

export function walletTransactions(db: MockDb, walletId: string) {
  return db.transactions.filter((tx) => tx.walletId === walletId);
}

export function toWallet(db: MockDb, record: MockWalletRecord): Wallet {
  const txns = walletTransactions(db, record.id);
  const { balance, availableBalance } = computeBalances(txns);
  const latest = txns.reduce((acc, tx) => (tx.createdAt > acc ? tx.createdAt : acc), record.createdAt);
  return { ...record, balance, availableBalance, updatedAt: latest };
}

/** Simulates the backend resolving the bearer token into a user. */
export function requireUser(db: MockDb): MockUserRecord {
  const token = tokenStorage.getAccessToken();
  const session = token ? db.sessions.find((s) => s.accessToken === token) : undefined;
  const user = session ? db.users.find((u) => u.id === session.userId) : undefined;
  if (!user) throw mockError.unauthorized();
  return user;
}

export function requireWallet(db: MockDb, userId: string): MockWalletRecord {
  const wallet = db.wallets.find((w) => w.userId === userId);
  if (!wallet) throw mockError.notFound("Wallet not found");
  return wallet;
}

export function toPublicUser(user: MockUserRecord) {
  const { passwordDigest: _digest, preferences: _prefs, ...publicUser } = user;
  return publicUser;
}
