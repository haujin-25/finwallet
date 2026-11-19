import { queryOptions } from "@tanstack/react-query";
import { authService, transactionService, userService, walletService } from "@/services";
import type { TransactionListParams } from "@/types";

export const queryKeys = {
  currentUser: ["auth", "me"] as const,
  wallet: ["wallet", "me"] as const,
  walletSummary: (days: number) => ["wallet", "summary", days] as const,
  transactions: ["transactions"] as const,
  transferHistory: (userId: string) => ["transactions", "transfers", userId] as const,
  transactionList: (params: TransactionListParams) => ["transactions", "list", params] as const,
  transaction: (id: string) => ["transactions", "detail", id] as const,
  notificationPreferences: ["user", "notification-preferences"] as const,
};

export const currentUserQuery = () =>
  queryOptions({
    queryKey: queryKeys.currentUser,
    queryFn: () => authService.getCurrentUser(),
    staleTime: 5 * 60 * 1000,
    retry: false,
  });

export const walletQuery = () =>
  queryOptions({
    queryKey: queryKeys.wallet,
    queryFn: () => walletService.getDemoWallet(),
    staleTime: 30_000,
  });

export const currentWalletQuery = (userId: string) =>
  queryOptions({
    queryKey: [...queryKeys.wallet, userId],
    queryFn: () => walletService.getMyWallet(),
    retry: false,
  });

export const walletSummaryQuery = (days = 30) =>
  queryOptions({
    queryKey: queryKeys.walletSummary(days),
    queryFn: () => walletService.getSummary(days),
    staleTime: 30_000,
  });

export const transferHistoryQuery = (userId: string, page: number, size: number) =>
  queryOptions({
    queryKey: [...queryKeys.transferHistory(userId), page, size],
    queryFn: () => transactionService.getTransferHistory(page, size),
    retry: false,
  });

export const transactionListQuery = (params: TransactionListParams) =>
  queryOptions({
    queryKey: queryKeys.transactionList(params),
    queryFn: () => transactionService.list(params),
    staleTime: 30_000,
  });

export const recentTransactionsQuery = (pageSize = 5) =>
  transactionListQuery({ page: 1, pageSize });

export const transactionQuery = (id: string) =>
  queryOptions({
    queryKey: queryKeys.transaction(id),
    queryFn: () => transactionService.get(id),
    retry: false,
  });

export const notificationPreferencesQuery = () =>
  queryOptions({
    queryKey: queryKeys.notificationPreferences,
    queryFn: () => userService.getNotificationPreferences(),
  });
