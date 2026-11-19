import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2, UserRound } from "lucide-react";
import { useForm } from "react-hook-form";
import { FormField } from "@/components/common/form-field";
import { AmountInput } from "@/components/money/amount-input";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { DEMO_MODE, DEMO_RECIPIENTS } from "@/config/demo";
import { formatMoney } from "@/utils/money";
import {
  liveTransferFormSchema,
  transferFormSchema,
  type TransferForm as TransferFormValues,
} from "@/utils/validation";

export function TransferForm({
  defaultValues,
  availableBalance,
  pending,
  serverError,
  onSubmit,
  live = false,
}: {
  defaultValues: TransferFormValues;
  availableBalance: number;
  live?: boolean;
  pending: boolean;
  serverError?: { field: "recipient" | "amount" | "root"; message: string } | null;
  onSubmit: (values: TransferFormValues) => void;
}) {
  const form = useForm<TransferFormValues>({
    resolver: zodResolver(live ? liveTransferFormSchema : transferFormSchema),
    defaultValues,
  });
  const { errors } = form.formState;
  const fieldError = (field: "recipient" | "amount") =>
    errors[field]?.message ?? (serverError?.field === field ? serverError.message : undefined);

  return (
    <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-6" noValidate>
      <FormField
        id="recipient"
        label={live ? "Recipient email" : "Recipient"}
        error={fieldError("recipient")}
        hint={
          live
            ? "Enter the recipient’s registered FinWallet email."
            : "Their FinWallet email or user ID, e.g. FW-USR-1004"
        }
      >
        <div className="relative">
          <UserRound className="pointer-events-none absolute left-3.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            id="recipient"
            placeholder={live ? "name@example.com" : "name@example.com or FW-USR-0000"}
            type={live ? "email" : "text"}
            className="pl-10"
            autoComplete="off"
            aria-invalid={!!fieldError("recipient")}
            {...form.register("recipient")}
          />
        </div>
      </FormField>

      {DEMO_MODE && !live && (
        <div className="-mt-3 flex flex-wrap gap-2">
          {DEMO_RECIPIENTS.map((r) => (
            <button
              key={r.identifier}
              type="button"
              onClick={() => form.setValue("recipient", r.identifier, { shouldValidate: true })}
              className="rounded-full border bg-card px-3 py-1 text-xs font-semibold text-muted-foreground transition-colors hover:border-primary hover:text-foreground"
            >
              {r.name}
            </button>
          ))}
        </div>
      )}

      <FormField
        id="amount"
        label="Amount"
        error={fieldError("amount")}
        hint={
          <>
            Available:{" "}
            <span className="font-semibold text-foreground">{formatMoney(availableBalance)}</span>
          </>
        }
      >
        <AmountInput
          id="amount"
          aria-invalid={!!fieldError("amount")}
          {...form.register("amount")}
        />
      </FormField>

      <FormField
        id="description"
        label="Description / reference (optional)"
        error={errors.description?.message}
      >
        <Input
          id="description"
          placeholder="e.g. Dinner split"
          maxLength={live ? undefined : 80}
          {...form.register("description")}
        />
      </FormField>

      {serverError?.field === "root" && (
        <p
          className="rounded-lg bg-destructive-soft px-3 py-2.5 text-sm font-medium text-destructive"
          role="alert"
        >
          {serverError.message}
        </p>
      )}

      <Button type="submit" size="xl" className="w-full" disabled={pending}>
        {pending && <Loader2 className="animate-spin" />}
        Review transfer
      </Button>
    </form>
  );
}
