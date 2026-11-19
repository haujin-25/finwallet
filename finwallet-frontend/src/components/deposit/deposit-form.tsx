import { zodResolver } from "@hookform/resolvers/zod";
import { Building2, CreditCard, Landmark, Loader2 } from "lucide-react";
import { Controller, useForm } from "react-hook-form";
import { FormField } from "@/components/common/form-field";
import { AmountInput } from "@/components/money/amount-input";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { PAYMENT_METHODS } from "@/config/payment-methods";
import { cn } from "@/lib/utils";
import type { PaymentMethod } from "@/types";
import { depositFormSchema, type DepositForm as DepositFormValues } from "@/utils/validation";

const ICONS: Record<PaymentMethod, typeof CreditCard> = {
  ONLINE_BANKING: Landmark,
  BANK_TRANSFER: Building2,
  DEBIT_CARD: CreditCard,
};

const PRESETS = ["50", "100", "250", "500"];

export function DepositForm({
  defaultValues,
  onSubmit,
}: {
  defaultValues: DepositFormValues;
  onSubmit: (values: DepositFormValues) => void;
}) {
  const form = useForm<DepositFormValues>({ resolver: zodResolver(depositFormSchema), defaultValues });
  const { errors, isSubmitting } = form.formState;

  return (
    <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-6" noValidate>
      <FormField id="amount" label="Deposit amount" error={errors.amount?.message} hint="Between RM 10.00 and RM 30,000.00">
        <AmountInput id="amount" aria-invalid={!!errors.amount} {...form.register("amount")} />
      </FormField>
      <div className="-mt-3 flex flex-wrap gap-2">
        {PRESETS.map((preset) => (
          <button
            key={preset}
            type="button"
            onClick={() => form.setValue("amount", preset, { shouldValidate: true })}
            className="rounded-full border bg-card px-3 py-1 text-xs font-semibold text-muted-foreground transition-colors hover:border-primary hover:text-foreground"
          >
            RM {preset}
          </button>
        ))}
      </div>

      <fieldset className="space-y-2">
        <legend className="mb-2 text-[13px] font-semibold">Payment method</legend>
        <Controller
          control={form.control}
          name="method"
          render={({ field }) => (
            <div role="radiogroup" className="grid gap-2">
              {PAYMENT_METHODS.map((method) => {
                const Icon = ICONS[method.id];
                const selected = field.value === method.id;
                return (
                  <button
                    key={method.id}
                    type="button"
                    role="radio"
                    aria-checked={selected}
                    onClick={() => field.onChange(method.id)}
                    className={cn(
                      "flex items-center gap-4 rounded-xl border bg-card px-4 py-3.5 text-left transition-all",
                      selected ? "border-primary ring-4 ring-primary/10" : "hover:border-input hover:bg-muted/50",
                    )}
                  >
                    <span
                      className={cn(
                        "inline-flex size-10 items-center justify-center rounded-xl",
                        selected ? "bg-primary text-primary-foreground" : "bg-secondary text-primary",
                      )}
                    >
                      <Icon className="size-4" />
                    </span>
                    <span className="flex-1">
                      <span className="flex items-center gap-2 text-sm font-semibold">
                        {method.label}
                        <Badge variant="simulated" className="px-2 py-0 text-[10px]">
                          Simulated
                        </Badge>
                      </span>
                      <span className="block text-xs text-muted-foreground">{method.description}</span>
                    </span>
                    <span className="hidden text-xs font-medium text-muted-foreground sm:block">{method.processingTime}</span>
                    <span
                      className={cn(
                        "size-4 rounded-full border-2",
                        selected ? "border-primary bg-primary shadow-[inset_0_0_0_2px_var(--card)]" : "border-input",
                      )}
                    />
                  </button>
                );
              })}
            </div>
          )}
        />
      </fieldset>

      <Button type="submit" size="xl" className="w-full" disabled={isSubmitting}>
        {isSubmitting && <Loader2 className="animate-spin" />}
        Review deposit
      </Button>
    </form>
  );
}
