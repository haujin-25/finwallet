import type { Paginated, Transaction, TransactionListParams } from "@/types";
import { getDb, type MockTransactionRecord } from "./mock-db";
import { requireUser, requireWallet, walletTransactions } from "./mock-ledger";
import { delay, mockError } from "./mock-utils";

export function toTransaction(record: MockTransactionRecord): Transaction {
  const { walletId: _walletId, ...tx } = record;
  return tx;
}

function startOfDay(ymd: string) {
  return new Date(`${ymd}T00:00:00`).getTime();
}

function endOfDay(ymd: string) {
  return new Date(`${ymd}T23:59:59.999`).getTime();
}

export const mockTransactions = {
  async list(params: TransactionListParams = {}): Promise<Paginated<Transaction>> {
    await delay();
    const db = getDb();
    const wallet = requireWallet(db, requireUser(db).id);
    const page = Math.max(1, params.page ?? 1);
    const pageSize = Math.min(50, Math.max(1, params.pageSize ?? 10));
    const search = params.search?.trim().toLowerCase();

    const filtered = walletTransactions(db, wallet.id)
      .filter((tx) => !params.type || tx.type === params.type)
      .filter((tx) => !params.status || tx.status === params.status)
      .filter((tx) => !params.from || new Date(tx.createdAt).getTime() >= startOfDay(params.from))
      .filter((tx) => !params.to || new Date(tx.createdAt).getTime() <= endOfDay(params.to))
      .filter(
        (tx) =>
          !search ||
          [tx.reference, tx.counterparty.name, tx.counterparty.identifier, tx.description ?? ""].some((field) =>
            field.toLowerCase().includes(search),
          ),
      )
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt));

    const total = filtered.length;
    const totalPages = Math.max(1, Math.ceil(total / pageSize));
    const data = filtered.slice((page - 1) * pageSize, page * pageSize).map(toTransaction);
    return { data, meta: { page, pageSize, total, totalPages } };
  },

  async get(id: string): Promise<Transaction> {
    await delay();
    const db = getDb();
    const wallet = requireWallet(db, requireUser(db).id);
    const record = db.transactions.find((tx) => tx.id === id && tx.walletId === wallet.id);
    if (!record) throw mockError.notFound("Transaction not found");
    return toTransaction(record);
  },
};
