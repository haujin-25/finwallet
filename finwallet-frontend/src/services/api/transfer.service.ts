import { env } from "@/config/env";
import type {
  SpringTransferRequest,
  SpringTransferResult,
  CreateTransferRequest,
  Paginated,
  Transfer,
  TransferQuote,
  TransferQuoteRequest,
} from "@/types";
import { mockTransfers } from "../mock/mock-transfers";
import { apiClient } from "./client";
import { postTransfer } from "./transfer-rpc";
import { ApiError } from "./api-error";

const inFlightTransfers = new Map<
  string,
  { payload: string; promise: Promise<SpringTransferResult> }
>();

export const transferService = {
  async send(accountEmail: string, request: SpringTransferRequest): Promise<SpringTransferResult> {
    const key = JSON.stringify([accountEmail, request.idempotencyKey]);
    const payload = JSON.stringify(request);
    const inFlight = inFlightTransfers.get(key);
    if (inFlight) {
      if (payload === inFlight.payload) return inFlight.promise;
      return {
        outcome: "unknown",
        status: 409,
        message:
          "A request with this transfer key is already in progress. Keep the original details.",
      };
    }

    const promise = postTransfer({ data: { accountEmail, request } })
      .catch((): SpringTransferResult => ({
        outcome: "unknown",
        status: 0,
        message: "The connection was interrupted before FinWallet confirmed the transfer.",
      }))
      .finally(() => inFlightTransfers.delete(key));

    inFlightTransfers.set(key, { payload, promise });
    return promise;
  },

  /** Server-side fee and recipient lookup shown on the confirmation screen. */
  quote(req: TransferQuoteRequest): Promise<TransferQuote> {
    if (!env.useMockApi)
      return Promise.reject(new ApiError(404, "Transfer quotes are available in demo mode."));
    return mockTransfers.quote(req);
  },

  create(req: CreateTransferRequest): Promise<Transfer> {
    if (!env.useMockApi)
      return Promise.reject(new ApiError(404, "Simulated transfers are available in demo mode."));
    return mockTransfers.create(req);
  },

  list(page = 1, pageSize = 10): Promise<Paginated<Transfer>> {
    return env.useMockApi
      ? mockTransfers.list(page, pageSize)
      : apiClient.get<Paginated<Transfer>>("/transfers", { query: { page, pageSize } });
  },

  get(id: string): Promise<Transfer> {
    return env.useMockApi
      ? mockTransfers.get(id)
      : apiClient.get<Transfer>(`/transfers/${encodeURIComponent(id)}`);
  },
};
