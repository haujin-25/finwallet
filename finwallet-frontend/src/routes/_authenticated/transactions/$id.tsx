import { useSuspenseQuery } from "@tanstack/react-query";
import { Link, createFileRoute, notFound } from "@tanstack/react-router";
import { ArrowLeft, Repeat } from "lucide-react";
import { CopyButton } from "@/components/common/copy-button";
import { DetailList, DetailRow } from "@/components/common/detail-list";
import { PageSkeleton } from "@/components/common/page-skeleton";
import { RouteError, RouteNotFound } from "@/components/common/route-boundaries";
import { StatusBadge } from "@/components/transactions/status-badge";
import { TransactionAmount } from "@/components/transactions/transaction-amount";
import { TransactionIcon } from "@/components/transactions/transaction-icon";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { transactionQuery } from "@/hooks/queries";
import { ApiError } from "@/services";
import { formatDateTime } from "@/utils/date";
import { PAYMENT_METHOD_LABEL, TRANSACTION_TYPE_LABEL } from "@/utils/labels";
import { formatMoney } from "@/utils/money";

export const Route = createFileRoute("/_authenticated/transactions/$id")({
  head: () => ({
    meta: [
      { title: "Transaction details — FinWallet" },
      { name: "description", content: "Reference, amount, status and counterparty for a FinWallet transaction." },
      { property: "og:title", content: "Transaction details — FinWallet" },
      { property: "og:description", content: "Reference, amount, status and counterparty for a FinWallet transaction." },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary" },
      { name: "robots", content: "noindex" },
    ],
  }),
  loader: async ({ context: { queryClient }, params }) => {
    try {
      await queryClient.ensureQueryData(transactionQuery(params.id));
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) throw notFound();
      throw error;
    }
  },
  pendingComponent: PageSkeleton,
  errorComponent: RouteError,
  notFoundComponent: () => (
    <RouteNotFound title="Transaction not found" description="It may belong to another account or no longer exist." />
  ),
  component: TransactionDetailPage,
});

const STATUS_NOTE = {
  COMPLETED: null,
  PENDING: "This transaction is still being processed.",
  FAILED: "This transaction didn't go through. Your balance wasn't affected.",
} as const;

function TransactionDetailPage() {
  const { id } = Route.useParams();
  const { data: tx } = useSuspenseQuery(transactionQuery(id));
  const counterpartyLabel = tx.type === "DEPOSIT" ? "Source" : tx.direction === "DEBIT" ? "Recipient" : "Sender";

  return (
    <div className="mx-auto max-w-2xl space-y-6 animate-rise">
      <Button asChild variant="ghost" size="sm" className="-ml-2 text-muted-foreground">
        <Link to="/transactions">
          <ArrowLeft /> All transactions
        </Link>
      </Button>

      <section className="rounded-3xl border bg-card shadow-card">
        <div className="flex flex-col items-center gap-3 border-b px-6 py-8 text-center">
          <TransactionIcon type={tx.type} className="size-12 rounded-2xl" />
          <p className="text-sm text-muted-foreground">
            {TRANSACTION_TYPE_LABEL[tx.type]} · {tx.counterparty.name}
          </p>
          <TransactionAmount transaction={tx} className="text-4xl font-extrabold tracking-tight" />
          <div className="flex flex-wrap items-center justify-center gap-2">
            <StatusBadge status={tx.status} />
            {tx.simulated && <Badge variant="simulated">Simulated</Badge>}
          </div>
          {STATUS_NOTE[tx.status] && (
            <p className="max-w-sm text-sm text-muted-foreground">
              {tx.failureReason ? `${tx.failureReason}. ` : ""}
              {STATUS_NOTE[tx.status]}
            </p>
          )}
        </div>

        <DetailList className="px-6">
          <DetailRow
            label="Reference"
            mono
            value={
              <span className="inline-flex items-center gap-1">
                {tx.reference}
                <CopyButton value={tx.reference} label="Reference" />
              </span>
            }
          />
          <DetailRow label="Type" value={TRANSACTION_TYPE_LABEL[tx.type]} />
          <DetailRow
            label={counterpartyLabel}
            value={
              <span className="block">
                <span className="block">{tx.counterparty.name}</span>
                <span className="block text-xs font-normal text-muted-foreground">{tx.counterparty.identifier}</span>
              </span>
            }
          />
          {tx.paymentMethod && <DetailRow label="Payment method" value={`${PAYMENT_METHOD_LABEL[tx.paymentMethod]} (simulated)`} />}
          {tx.description && <DetailRow label="Description" value={tx.description} />}
          <DetailRow label="Date & time" value={formatDateTime(tx.createdAt)} />
          {tx.completedAt && <DetailRow label="Completed" value={formatDateTime(tx.completedAt)} />}
          <DetailRow label="Amount" value={formatMoney(tx.amount, tx.currency)} />
          <DetailRow label="Fee" value={tx.fee === 0 ? "Free" : formatMoney(tx.fee, tx.currency)} />
          <DetailRow label="Total" value={formatMoney(tx.totalAmount, tx.currency)} emphasis />
          <DetailRow label="Transaction ID" mono value={tx.id} />
        </DetailList>

        {tx.type === "TRANSFER_OUT" && (
          <div className="border-t p-6">
            <Button asChild variant="outline" size="lg" className="w-full">
              <Link to="/transfer" search={{ to: tx.counterparty.identifier }}>
                <Repeat /> Send to {tx.counterparty.name.split(" ")[0]} again
              </Link>
            </Button>
          </div>
        )}
      </section>
    </div>
  );
}
