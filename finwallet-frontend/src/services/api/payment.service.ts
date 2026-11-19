import { env } from "@/config/env";
import type { DepositRequest, Payment } from "@/types";
import { mockPayments } from "../mock/mock-payments";
import { apiClient } from "./client";

/** Simulated deposits only. No real payment gateway is connected. */
export const paymentService = {
  deposit(req: DepositRequest): Promise<Payment> {
    return env.useMockApi
      ? mockPayments.deposit(req)
      : apiClient.post<Payment>("/payments/deposit", req, { headers: { "Idempotency-Key": req.idempotencyKey } });
  },

  get(id: string): Promise<Payment> {
    return env.useMockApi ? mockPayments.get(id) : apiClient.get<Payment>(`/payments/${encodeURIComponent(id)}`);
  },
};
