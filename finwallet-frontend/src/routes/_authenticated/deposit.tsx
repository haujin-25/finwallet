import { createFileRoute } from "@tanstack/react-router";
import { Loader2 } from "lucide-react";
import { useState } from "react";
import { DetailList, DetailRow } from "@/components/common/detail-list";
import { PageHeader } from "@/components/common/page-header";
import { SimulatedNotice } from "@/components/common/simulated-notice";
import { DepositForm } from "@/components/deposit/deposit-form";
import { DepositResult } from "@/components/deposit/deposit-result";
import { StepIndicator } from "@/components/money/step-indicator";
import { Button } from "@/components/ui/button";
import { DEMO_MODE } from "@/config/demo";
import { PAYMENT_METHODS } from "@/config/payment-methods";
import { useDeposit } from "@/hooks/use-money-movement";
import { getErrorMessage } from "@/services";
import type { DepositRequest, Payment } from "@/types";
import { createIdempotencyKey } from "@/utils/id";
import { formatMoney, parseAmountToMinor } from "@/utils/money";
import type { DepositForm as DepositFormValues } from "@/utils/validation";

type Step = { kind: "form" } | { kind: "confirm"; request: DepositRequest } | { kind: "result"; payment: Payment };

export const Route = createFileRoute("/_authenticated/deposit")({
  head: () => ({
    meta: [
      { title: "Deposit — FinWallet" },
      { name: "description", content: "Top up your FinWallet with a simulated payment method." },
      { property: "og:title", content: "Deposit — FinWallet" },
      { property: "og:description", content: "Top up your FinWallet with a simulated payment method." },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary" },
    ],
  }),
  component: DepositPage,
});

function DepositPage() {
  const deposit = useDeposit();
  const [step, setStep] = useState<Step>({ kind: "form" });
  const [values, setValues] = useState<DepositFormValues>({ amount: "", method: "ONLINE_BANKING" });

  const onReview = (next: DepositFormValues) => {
    setValues(next);
    const amount = parseAmountToMinor(next.amount);
    if (amount === null) return;
    deposit.reset();
    setStep({ kind: "confirm", request: { amount, method: next.method, idempotencyKey: createIdempotencyKey() } });
  };

  const onConfirm = () => {
    if (step.kind !== "confirm") return;
    deposit.mutate(step.request, { onSuccess: (payment) => setStep({ kind: "result", payment }) });
  };

  const reset = () => {
    deposit.reset();
    setValues({ amount: "", method: "ONLINE_BANKING" });
    setStep({ kind: "form" });
  };

  if (step.kind === "result") return <DepositResult payment={step.payment} onNew={reset} />;

  const method = step.kind === "confirm" ? PAYMENT_METHODS.find((m) => m.id === step.request.method) : undefined;

  return (
    <div className="space-y-6 animate-rise">
      <PageHeader title="Deposit" description="Add money to your wallet using a simulated payment method." />

      <div className="grid gap-6 lg:grid-cols-5">
        <section className="rounded-2xl border bg-card p-6 shadow-card sm:p-8 lg:col-span-3">
          <div className="mb-6 flex items-center justify-between gap-4">
            <h2 className="text-base font-bold">{step.kind === "form" ? "Deposit details" : "Confirm deposit"}</h2>
            <StepIndicator steps={["Details", "Confirm", "Done"]} current={step.kind === "form" ? 0 : 1} />
          </div>

          {step.kind === "form" ? (
            <DepositForm defaultValues={values} onSubmit={onReview} />
          ) : (
            <div className="space-y-6">
              <div className="rounded-2xl bg-muted p-5 text-center">
                <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">You're depositing</p>
                <p className="mt-2 text-4xl font-extrabold tracking-tight tabular">{formatMoney(step.request.amount)}</p>
              </div>
              <DetailList>
                <DetailRow label="Payment method" value={`${method?.label} (simulated)`} />
                <DetailRow label="Processing time" value={method?.processingTime} />
                <DetailRow label="Deposit to" value="Your FinWallet (MYR)" />
              </DetailList>
              {deposit.isError && (
                <p className="rounded-lg bg-destructive-soft px-3 py-2.5 text-sm font-medium text-destructive" role="alert">
                  {getErrorMessage(deposit.error)}
                </p>
              )}
              <div className="flex flex-col-reverse gap-2 sm:flex-row">
                <Button variant="outline" size="xl" className="sm:flex-1" onClick={() => setStep({ kind: "form" })} disabled={deposit.isPending}>
                  Edit details
                </Button>
                <Button size="xl" className="sm:flex-[2]" onClick={onConfirm} disabled={deposit.isPending}>
                  {deposit.isPending && <Loader2 className="animate-spin" />}
                  {deposit.isPending ? "Processing…" : "Confirm deposit"}
                </Button>
              </div>
            </div>
          )}
        </section>

        <aside className="space-y-4 lg:col-span-2">
          <SimulatedNotice>
            <span className="font-semibold">Simulated payment methods.</span> We never ask for card numbers or bank
            logins, and no real payment gateway is connected.
          </SimulatedNotice>
          {DEMO_MODE && (
            <div className="rounded-2xl border bg-card p-5 shadow-card">
              <p className="text-sm font-bold">Try each outcome</p>
              <ul className="mt-3 space-y-2.5 text-sm text-muted-foreground">
                <li className="flex gap-2">
                  <span className="mt-1.5 size-1.5 shrink-0 rounded-full bg-success" />
                  Online Banking or Debit Card: completes instantly.
                </li>
                <li className="flex gap-2">
                  <span className="mt-1.5 size-1.5 shrink-0 rounded-full bg-warning" />
                  Bank Transfer: stays pending.
                </li>
                <li className="flex gap-2">
                  <span className="mt-1.5 size-1.5 shrink-0 rounded-full bg-destructive" />
                  Debit Card over RM 20,000: declined.
                </li>
              </ul>
            </div>
          )}
        </aside>
      </div>
    </div>
  );
}
