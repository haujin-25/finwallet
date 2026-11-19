import { env } from "@/config/env";
import type { CurrentWallet, Wallet, WalletBalance, WalletSummary } from "@/types";
import { mockWallet } from "../mock/mock-wallet";
import { ApiError } from "./api-error";
import { getCurrentWallet } from "./wallet-rpc";
import { apiClient } from "./client";

export const walletService = {
  async getMyWallet(): Promise<CurrentWallet> {
    const result = await getCurrentWallet();
    if (!result.ok) throw new ApiError(result.status, result.message);
    return result.data;
  },

  async getDemoWallet(): Promise<Wallet> {
    if (!env.useMockApi) throw new ApiError(404, "This feature is available in demo mode.");
    return mockWallet.getMyWallet();
  },

  getMyBalance(): Promise<WalletBalance> {
    return env.useMockApi
      ? mockWallet.getMyBalance()
      : apiClient.get<WalletBalance>("/wallets/me/balance");
  },

  getSummary(days = 30): Promise<WalletSummary> {
    return env.useMockApi
      ? mockWallet.getSummary(days)
      : apiClient.get<WalletSummary>("/wallets/me/summary", { query: { days } });
  },
};
