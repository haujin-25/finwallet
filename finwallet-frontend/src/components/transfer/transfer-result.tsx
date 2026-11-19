import { Link } from "@tanstack/react-router";
import { CopyButton } from "@/components/common/copy-button";
import { ResultPanel } from "@/components/money/result-panel";
import { Button } from "@/components/ui/button";
import type { Transfer } from "@/types";
import { formatDateTime } from "@/utils/date";
import { formatMoney } from "@/utils/money";

const COPY = {
  COMPLETED: { title: "Transfer sent", message: (t: Transfer) => `${t.recipient.name} has received your money.` },
  PENDING: {
    title: "Transfer pending",
    message: () => "Large transfers are reviewed before release. The amount is on hold in your wallet until then.",
  },
  FAILED: {
    title: "Transfer failed",
    message: (t: Transfer) => `${t.failureReason ?? "The transfer couldn't be completed"}. No money left your wallet.`,
  },
} as const;

export function TransferResult({ transfer, onNew }: { transfer: Transfer; onNew: () => void }) {
  const copy = COPY[transfer.status];
  return (
    <ResultPanel
      status={transfer.status}
      title={copy.title}
      message={copy.message(transfer)}
      amount={formatMoney(transfer.totalAmount, transfer.currency)}
      rows={[
        {
          label: "Reference",
          mono: true,
          value: (
            <span className="inline-flex items-center gap-1">
              {transfer.reference}
              <CopyButton value={transfer.reference} label="Reference" />
            </span>
          ),
        },
        { label: "Recipient", value: transfer.recipient.name },
        { label: "Amount", value: formatMoney(transfer.amount, transfer.currency) },
        { label: "Fee", value: transfer.fee === 0 ? "Free" : formatMoney(transfer.fee, transfer.currency) },
        ...(transfer.description ? [{ label: "Description", value: transfer.description }] : []),
        { label: "Date & time", value: formatDateTime(transfer.createdAt) },
      ]}
      actions={
        <>
          <Button asChild size="lg" className="sm:flex-1">
            <Link to="/transactions/$id" params={{ id: transfer.transactionId }}>
              View transaction
            </Link>
          </Button>
          <Button variant="outline" size="lg" className="sm:flex-1" onClick={onNew}>
            {transfer.status === "FAILED" ? "Try again" : "New transfer"}
          </Button>
        </>
      }
    />
  );
}
