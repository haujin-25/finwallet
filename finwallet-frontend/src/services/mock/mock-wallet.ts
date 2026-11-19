import type { Wallet, WalletBalance, WalletSummary } from "@/types";
import { getDb } from "./mock-db";
import { requireUser, requireWallet, toWallet, walletTransactions } from "./mock-ledger";
import { delay } from "./mock-utils";

export const mockWallet = {
  async getMyWallet(): Promise<Wallet> {
    await delay();
    const db = getDb();
    const user = requireUser(db);
    return toWallet(db, requireWallet(db, user.id));
  },

  async getMyBalance(): Promise<WalletBalance> {
    await delay(150, 300);
    const db = getDb();
    const wallet = toWallet(db, requireWallet(db, requireUser(db).id));
    return {
      walletId: wallet.id,
      currency: wallet.currency,
      balance: wallet.balance,
      availableBalance: wallet.availableBalance,
      asOf: new Date().toISOString(),
    };
  },

  async getSummary(days = 30): Promise<WalletSummary> {
    await delay();
    const db = getDb();
    const wallet = requireWallet(db, requireUser(db).id);
    const since = Date.now() - days * 24 * 60 * 60 * 1000;
    const recent = walletTransactions(db, wallet.id).filter((tx) => new Date(tx.createdAt).getTime() >= since);
    const completed = recent.filter((tx) => tx.status === "COMPLETED");
    return {
      periodDays: days,
      currency: "MYR",
      moneyIn: completed.filter((tx) => tx.direction === "CREDIT").reduce((sum, tx) => sum + tx.amount, 0),
      moneyOut: completed.filter((tx) => tx.direction === "DEBIT").reduce((sum, tx) => sum + tx.totalAmount, 0),
      completedCount: completed.length,
      pendingCount: recent.filter((tx) => tx.status === "PENDING").length,
      failedCount: recent.filter((tx) => tx.status === "FAILED").length,
    };
  },
};
