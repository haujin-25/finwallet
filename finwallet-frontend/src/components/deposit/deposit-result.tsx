import { Link } from "@tanstack/react-router";
import { CopyButton } from "@/components/common/copy-button";
import { ResultPanel } from "@/components/money/result-panel";
import { Button } from "@/components/ui/button";
import type { Payment } from "@/types";
import { formatDateTime } from "@/utils/date";
import { PAYMENT_METHOD_LABEL } from "@/utils/labels";
import { formatMoney } from "@/utils/money";

const COPY = {
  COMPLETED: { title: "Deposit successful", message: () => "The money is now in your wallet and ready to use." },
  PENDING: {
    title: "Deposit pending",
    message: () => "We're waiting for the simulated bank to confirm. It will be added once confirmed.",
  },
  FAILED: {
    title: "Deposit failed",
    message: (p: Payment) => `${p.failureReason ?? "The deposit couldn't be completed"}. Nothing was added to your wallet.`,
  },
} as const;

export function DepositResult({ payment, onNew }: { payment: Payment; onNew: () => void }) {
  const copy = COPY[payment.status];
  return (
    <ResultPanel
      status={payment.status}
      title={copy.title}
      message={copy.message(payment)}
      amount={formatMoney(payment.amount, payment.currency, { sign: payment.status === "FAILED" ? "none" : "credit" })}
      rows={[
        {
          label: "Reference",
          mono: true,
          value: (
            <span className="inline-flex items-center gap-1">
              {payment.reference}
              <CopyButton value={payment.reference} label="Reference" />
            </span>
          ),
        },
        { label: "Method", value: `${PAYMENT_METHOD_LABEL[payment.method]} (simulated)` },
        { label: "Fee", value: payment.fee === 0 ? "Free" : formatMoney(payment.fee, payment.currency) },
        { label: "Date & time", value: formatDateTime(payment.createdAt) },
      ]}
      actions={
        <>
          <Button asChild size="lg" className="sm:flex-1">
            <Link to="/transactions/$id" params={{ id: payment.transactionId }}>
              View transaction
            </Link>
          </Button>
          <Button variant="outline" size="lg" className="sm:flex-1" onClick={onNew}>
            {payment.status === "FAILED" ? "Try again" : "New deposit"}
          </Button>
        </>
      }
    />
  );
}
