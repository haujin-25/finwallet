import { env } from "@/config/env";
import type { Paginated, Transaction, TransactionListParams, TransferHistoryPage } from "@/types";
import { mockTransactions } from "../mock/mock-transactions";
import { getTransferHistory } from "./transfer-history-rpc";
import { ApiError } from "./api-error";
import { apiClient } from "./client";

export const transactionService = {
  async getTransferHistory(page: number, size: number): Promise<TransferHistoryPage> {
    const result = await getTransferHistory({ data: { page, size } });
    if (!result.ok) throw new ApiError(result.status, result.message);
    return result.data;
  },

  list(params: TransactionListParams = {}): Promise<Paginated<Transaction>> {
    return env.useMockApi
      ? mockTransactions.list(params)
      : apiClient.get<Paginated<Transaction>>("/transactions", { query: { ...params } });
  },

  get(id: string): Promise<Transaction> {
    return env.useMockApi
      ? mockTransactions.get(id)
      : apiClient.get<Transaction>(`/transactions/${encodeURIComponent(id)}`);
  },
};
