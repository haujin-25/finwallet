import { useMutation, useQueryClient } from "@tanstack/react-query";
import { paymentService, transferService } from "@/services";
import type { CreateTransferRequest, DepositRequest, TransferQuoteRequest } from "@/types";
import { queryKeys } from "./queries";

function useInvalidateMoney() {
  const queryClient = useQueryClient();
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: ["wallet"] }),
      queryClient.invalidateQueries({ queryKey: queryKeys.transactions }),
    ]);
}

export function useTransferQuote() {
  return useMutation({ mutationFn: (req: TransferQuoteRequest) => transferService.quote(req) });
}

export function useCreateTransfer() {
  const invalidate = useInvalidateMoney();
  return useMutation({
    mutationFn: (req: CreateTransferRequest) => transferService.create(req),
    onSuccess: () => invalidate(),
  });
}

export function useDeposit() {
  const invalidate = useInvalidateMoney();
  return useMutation({
    mutationFn: (req: DepositRequest) => paymentService.deposit(req),
    onSuccess: () => invalidate(),
  });
}
