import { useSuspenseQuery } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { useState } from "react";
import { toast } from "sonner";
import { PageHeader } from "@/components/common/page-header";
import { PageSkeleton } from "@/components/common/page-skeleton";
import { RouteError, RouteNotFound } from "@/components/common/route-boundaries";
import { SimulatedNotice } from "@/components/common/simulated-notice";
import { StepIndicator } from "@/components/money/step-indicator";
import { LiveTransfer } from "@/components/transfer/live-transfer";
import { useCurrentUser } from "@/hooks/use-auth";
import { TransferConfirm } from "@/components/transfer/transfer-confirm";
import { TransferForm } from "@/components/transfer/transfer-form";
import { TransferResult } from "@/components/transfer/transfer-result";
import { DEMO_MODE } from "@/config/demo";
import { walletQuery } from "@/hooks/queries";
import { useCreateTransfer, useTransferQuote } from "@/hooks/use-money-movement";
import { ApiError, getErrorMessage } from "@/services";
import type { CreateTransferRequest, Transfer, TransferQuote } from "@/types";
import { createIdempotencyKey } from "@/utils/id";
import { parseAmountToMinor } from "@/utils/money";
import type { TransferForm as TransferFormValues } from "@/utils/validation";

type Step =
  | { kind: "form" }
  | { kind: "confirm"; quote: TransferQuote; request: CreateTransferRequest }
  | { kind: "result"; transfer: Transfer };

export const Route = createFileRoute("/_authenticated/transfer")({
  validateSearch: (search: Record<string, unknown>): { to?: string | undefined } => ({
    to: typeof search["to"] === "string" && search["to"].length <= 120 ? search["to"] : undefined,
  }),
  head: () => ({
    meta: [
      { title: "Transfer Money — FinWallet" },
      { name: "description", content: "Send ringgit to another FinWallet user." },
      { property: "og:title", content: "Transfer Money — FinWallet" },
      { property: "og:description", content: "Send ringgit to another FinWallet user." },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary" },
    ],
  }),
  loader: async ({ context: { queryClient } }) => {
    if (DEMO_MODE) await queryClient.ensureQueryData(walletQuery());
  },
  pendingComponent: PageSkeleton,
  errorComponent: RouteError,
  notFoundComponent: () => <RouteNotFound />,
  component: DEMO_MODE ? TransferPage : LiveTransferPage,
});

function LiveTransferPage() {
  const user = useCurrentUser();
  return <LiveTransfer key={user.id} user={user} />;
}

function TransferPage() {
  const search = Route.useSearch();
  const { data: wallet } = useSuspenseQuery(walletQuery());
  const quote = useTransferQuote();
  const create = useCreateTransfer();
  const [step, setStep] = useState<Step>({ kind: "form" });
  const [values, setValues] = useState<TransferFormValues>({
    recipient: search.to ?? "",
    amount: "",
    description: "",
  });

  const quoteError = (() => {
    if (!quote.error) return null;
    const message = getErrorMessage(quote.error);
    if (quote.error instanceof ApiError && quote.error.status === 404)
      return { field: "recipient" as const, message };
    if (quote.error instanceof ApiError && quote.error.status === 422) {
      return {
        field: /own wallet/i.test(message) ? ("recipient" as const) : ("amount" as const),
        message,
      };
    }
    return { field: "root" as const, message };
  })();

  const onReview = (next: TransferFormValues) => {
    setValues(next);
    const amount = parseAmountToMinor(next.amount);
    if (amount === null) return;
    const recipient = next.recipient.trim();
    quote.mutate(
      { recipient, amount },
      {
        onSuccess: (result) =>
          setStep({
            kind: "confirm",
            quote: result,
            request: {
              recipient,
              amount,
              description: next.description?.trim() || undefined,
              idempotencyKey: createIdempotencyKey(),
            },
          }),
      },
    );
  };

  const onConfirm = () => {
    if (step.kind !== "confirm") return;
    create.mutate(step.request, {
      onSuccess: (transfer) => setStep({ kind: "result", transfer }),
      onError: (error) => toast.error("Transfer not sent", { description: getErrorMessage(error) }),
    });
  };

  const reset = () => {
    quote.reset();
    create.reset();
    setValues({ recipient: "", amount: "", description: "" });
    setStep({ kind: "form" });
  };

  if (step.kind === "result") return <TransferResult transfer={step.transfer} onNew={reset} />;

  const stepIndex = step.kind === "form" ? 0 : 1;

  return (
    <div className="space-y-6 animate-rise">
      <PageHeader
        title="Transfer Money"
        description="Send ringgit instantly to another FinWallet user."
      />

      <div className="grid gap-6 lg:grid-cols-5">
        <section className="rounded-2xl border bg-card p-6 shadow-card sm:p-8 lg:col-span-3">
          <div className="mb-6 flex items-center justify-between gap-4">
            <h2 className="text-base font-bold">
              {step.kind === "form" ? "Transfer details" : "Confirm transfer"}
            </h2>
            <StepIndicator steps={["Details", "Confirm", "Done"]} current={stepIndex} />
          </div>
          {step.kind === "form" ? (
            <TransferForm
              defaultValues={values}
              availableBalance={wallet.availableBalance}
              pending={quote.isPending}
              serverError={quoteError}
              onSubmit={onReview}
            />
          ) : (
            <TransferConfirm
              quote={step.quote}
              description={step.request.description}
              pending={create.isPending}
              onBack={() => {
                quote.reset();
                setStep({ kind: "form" });
              }}
              onConfirm={onConfirm}
            />
          )}
        </section>

        <aside className="space-y-4 lg:col-span-2">
          <SimulatedNotice />
          {DEMO_MODE && (
            <div className="rounded-2xl border bg-card p-5 shadow-card">
              <p className="text-sm font-bold">Try each outcome</p>
              <ul className="mt-3 space-y-2.5 text-sm text-muted-foreground">
                <li className="flex gap-2">
                  <span className="mt-1.5 size-1.5 shrink-0 rounded-full bg-success" />
                  Up to your available balance: completes instantly.
                </li>
                <li className="flex gap-2">
                  <span className="mt-1.5 size-1.5 shrink-0 rounded-full bg-warning" />
                  RM 10,000 or more (within your balance): held as pending for review.
                </li>
                <li className="flex gap-2">
                  <span className="mt-1.5 size-1.5 shrink-0 rounded-full bg-destructive" />
                  More than your available balance: fails.
                </li>
              </ul>
              <p className="mt-4 text-xs text-muted-foreground">
                Fees are set by the server and shown before you confirm.
              </p>
            </div>
          )}
        </aside>
      </div>
    </div>
  );
}
