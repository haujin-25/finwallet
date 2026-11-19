import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { Loader2 } from "lucide-react";
import { useRef, useState } from "react";
import { CopyButton } from "@/components/common/copy-button";
import { DetailList, DetailRow } from "@/components/common/detail-list";
import { PageHeader } from "@/components/common/page-header";
import { ResultPanel } from "@/components/money/result-panel";
import { StepIndicator } from "@/components/money/step-indicator";
import { Button } from "@/components/ui/button";
import { currentWalletQuery, queryKeys } from "@/hooks/queries";
import { ApiError, getErrorMessage, transferService } from "@/services";
import {
  clearPendingTransfer,
  readPendingTransfer,
  savePendingTransfer,
} from "@/services/api/pending-transfer";
import type { SpringTransferReceipt, SpringTransferRequest, User } from "@/types";
import { formatDateTime } from "@/utils/date";
import { formatMoney, parseTransferAmount } from "@/utils/money";
import type { TransferForm as TransferFormValues } from "@/utils/validation";
import { TransferForm } from "./transfer-form";

type Issue = { message: string; signIn?: boolean };
type Review = { request: SpringTransferRequest; uncertain: boolean };
const emptyForm = { recipient: "", amount: "", description: "" };
const storageIssue =
  "The saved transfer could not be accessed. Keep this tab open and check your browser storage settings.";

function restoreReview(userId: string): { review: Review | null; issue: Issue | null } {
  try {
    const request = readPendingTransfer(userId);
    return { review: request ? { request, uncertain: true } : null, issue: null };
  } catch {
    return { review: null, issue: { message: storageIssue } };
  }
}

export function LiveTransfer({ user }: { user: Pick<User, "id" | "email"> }) {
  const queryClient = useQueryClient();
  const walletOptions = currentWalletQuery(user.id);
  const wallet = useQuery(walletOptions);
  const [initial] = useState(() => restoreReview(user.id));
  const [review, setReview] = useState(initial.review);
  const [issue, setIssue] = useState(initial.issue);
  const [pending, setPending] = useState(false);
  const [receipt, setReceipt] = useState<SpringTransferReceipt | null>(null);
  const [values, setValues] = useState<TransferFormValues>(emptyForm);
  const submitLock = useRef(false);

  function onReview(next: TransferFormValues) {
    const amount = parseTransferAmount(next.amount);
    if (amount === null) return;
    const recipientEmail = next.recipient.trim().toLowerCase();
    setValues(next);
    if (recipientEmail === user.email.toLowerCase()) {
      setIssue({ message: "Enter another FinWallet user's email." });
      return;
    }
    setIssue(null);
    setReview({
      uncertain: false,
      request: {
        recipientEmail,
        amount,
        description: next.description?.trim() || null,
        idempotencyKey: crypto.randomUUID(),
      },
    });
  }

  async function submit() {
    if (!review || submitLock.current) return;
    submitLock.current = true;
    const { request, uncertain } = review;

    try {
      savePendingTransfer(user.id, request);
    } catch {
      submitLock.current = false;
      setIssue({ message: storageIssue });
      return;
    }

    setPending(true);
    setIssue(null);
    try {
      const result = await transferService.send(user.email, request);
      if (result.outcome === "confirmed") {
        setReceipt(result.receipt);
        void queryClient.invalidateQueries({ queryKey: queryKeys.transferHistory(user.id) });
        const walletFilter = { queryKey: walletOptions.queryKey, exact: true };
        void queryClient
          .cancelQueries(walletFilter)
          .then(() => queryClient.invalidateQueries(walletFilter));
      } else {
        setIssue({ message: result.message, signIn: result.status === 401 });
        if (result.status === 401 && !uncertain) return;
        if (uncertain || result.outcome === "unknown") {
          setReview({ request, uncertain: true });
          return;
        }
      }

      try {
        clearPendingTransfer(user.id, request.idempotencyKey);
      } catch {
        setReview({ request, uncertain: true });
        setIssue({ message: storageIssue });
      }
    } catch {
      setReview({ request, uncertain: true });
      setIssue({
        message: "The connection was interrupted before FinWallet confirmed the transfer.",
      });
    } finally {
      submitLock.current = false;
      setPending(false);
    }
  }

  if (receipt) {
    return (
      <ResultPanel
        simulated={false}
        status={receipt.status}
        title="Transfer sent"
        message="FinWallet confirmed your transfer."
        amount={formatMoney(receipt.amount, receipt.currency)}
        rows={[
          {
            label: "Reference",
            value: (
              <>
                {receipt.transferReference}{" "}
                <CopyButton value={receipt.transferReference} label="Reference" />
              </>
            ),
            mono: true,
          },
          { label: "Recipient wallet ID", value: String(receipt.recipientWalletId) },
          ...(receipt.description ? [{ label: "Description", value: receipt.description }] : []),
          { label: "Date & time", value: formatDateTime(receipt.createdAt) },
        ]}
        actions={
          <>
            <Button asChild size="lg">
              <Link to="/overview">Back to overview</Link>
            </Button>
            <Button
              variant="outline"
              size="lg"
              onClick={() => {
                setReceipt(null);
                setReview(null);
                setValues(emptyForm);
              }}
              disabled={!!issue}
            >
              New transfer
            </Button>
          </>
        }
      />
    );
  }

  return (
    <div className="space-y-6 animate-rise">
      <PageHeader
        title="Transfer Money"
        description="Send MYR to another registered FinWallet user."
      />
      <section className="max-w-2xl rounded-2xl border bg-card p-6 shadow-card sm:p-8">
        <div className="mb-6 flex flex-wrap items-center justify-between gap-4">
          <h2 className="text-base font-bold">
            {review ? "Confirm transfer" : "Transfer details"}
          </h2>
          <StepIndicator steps={["Details", "Confirm", "Done"]} current={review ? 1 : 0} />
        </div>
        {review ? (
          <div className="space-y-6">
            <DetailList>
              <DetailRow
                label="Recipient email"
                value={<span className="break-all">{review.request.recipientEmail}</span>}
              />
              <DetailRow
                label="Transfer amount"
                value={formatMoney(review.request.amount)}
                emphasis
              />
              {review.request.description && (
                <DetailRow
                  label="Description"
                  value={<span className="break-all">{review.request.description}</span>}
                />
              )}
              {wallet.data && (
                <DetailRow
                  label="Available balance"
                  value={formatMoney(wallet.data.availableBalance)}
                />
              )}
            </DetailList>
            {review.uncertain && (
              <p role="status" className="rounded-lg bg-warning-soft p-3 text-sm">
                The outcome of your original request is unknown. Retry the same transfer to recover
                its receipt or complete it if it was never processed.
              </p>
            )}
            {issue && <TransferIssue issue={issue} />}
            <div className="flex flex-col-reverse gap-2 sm:flex-row">
              {!review.uncertain && (
                <Button
                  variant="outline"
                  size="lg"
                  disabled={pending || !!issue?.signIn}
                  onClick={() => {
                    setReview(null);
                    setIssue(null);
                  }}
                >
                  Edit details
                </Button>
              )}
              <Button size="lg" disabled={pending || !!issue?.signIn} onClick={() => void submit()}>
                {pending && <Loader2 className="animate-spin" />}
                {pending
                  ? "Sending…"
                  : review.uncertain
                    ? "Retry same transfer"
                    : "Confirm and send"}
              </Button>
            </div>
          </div>
        ) : initial.issue ? (
          <TransferIssue issue={initial.issue} />
        ) : wallet.isPending ? (
          <p role="status" className="text-sm text-muted-foreground">
            Loading available balance…
          </p>
        ) : wallet.isError ? (
          <div className="space-y-4">
            <TransferIssue
              issue={{
                message: getErrorMessage(wallet.error),
                signIn: wallet.error instanceof ApiError && wallet.error.status === 401,
              }}
            />
            <Button variant="outline" onClick={() => void wallet.refetch()}>
              Retry loading wallet
            </Button>
          </div>
        ) : (
          <TransferForm
            live
            defaultValues={values}
            availableBalance={wallet.data.availableBalance}
            pending={false}
            serverError={issue ? { field: "recipient", message: issue.message } : null}
            onSubmit={onReview}
          />
        )}
      </section>
    </div>
  );
}

function TransferIssue({ issue }: { issue: Issue }) {
  return (
    <div
      role="alert"
      className="space-y-2 rounded-lg bg-destructive-soft p-3 text-sm text-destructive"
    >
      <p>{issue.message}</p>
      {issue.signIn && (
        <Link to="/login" search={{ redirect: "/transfer" }} className="font-semibold underline">
          Sign in to continue
        </Link>
      )}
    </div>
  );
}
