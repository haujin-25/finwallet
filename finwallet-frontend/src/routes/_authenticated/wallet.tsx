import { useSuspenseQuery } from "@tanstack/react-query";
import { Link, createFileRoute } from "@tanstack/react-router";
import { ArrowRight, Lock, Receipt } from "lucide-react";
import { CopyButton } from "@/components/common/copy-button";
import { DetailList, DetailRow } from "@/components/common/detail-list";
import { EmptyState } from "@/components/common/empty-state";
import { PageHeader } from "@/components/common/page-header";
import { PageSkeleton } from "@/components/common/page-skeleton";
import { RouteError, RouteNotFound } from "@/components/common/route-boundaries";
import { TransactionTable } from "@/components/transactions/transaction-table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { BalanceCard } from "@/components/wallet/balance-card";
import { SummaryStats } from "@/components/wallet/summary-stats";
import { recentTransactionsQuery, walletQuery, walletSummaryQuery } from "@/hooks/queries";
import { formatDate, formatDateTime } from "@/utils/date";
import { WALLET_STATUS_LABEL } from "@/utils/labels";
import { formatMoney } from "@/utils/money";

export const Route = createFileRoute("/_authenticated/wallet")({
  head: () => ({
    meta: [
      { title: "My Wallet — FinWallet" },
      { name: "description", content: "Wallet balance, wallet ID, status and recent activity." },
      { property: "og:title", content: "My Wallet — FinWallet" },
      { property: "og:description", content: "Wallet balance, wallet ID, status and recent activity." },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary" },
    ],
  }),
  loader: async ({ context: { queryClient } }) => {
    await Promise.all([
      queryClient.ensureQueryData(walletQuery()),
      queryClient.ensureQueryData(walletSummaryQuery(30)),
      queryClient.ensureQueryData(recentTransactionsQuery(5)),
    ]);
  },
  pendingComponent: PageSkeleton,
  errorComponent: RouteError,
  notFoundComponent: () => <RouteNotFound />,
  component: WalletPage,
});

function WalletPage() {
  const { data: wallet } = useSuspenseQuery(walletQuery());
  const { data: summary } = useSuspenseQuery(walletSummaryQuery(30));
  const { data: recent } = useSuspenseQuery(recentTransactionsQuery(5));
  const held = wallet.balance - wallet.availableBalance;

  return (
    <div className="space-y-6 animate-rise">
      <PageHeader title="My Wallet" description="Your ringgit wallet and everything moving through it." />

      <div className="grid gap-4 lg:grid-cols-5">
        <div className="lg:col-span-3">
          <BalanceCard wallet={wallet} />
        </div>
        <section className="rounded-2xl border bg-card px-6 py-4 shadow-card lg:col-span-2">
          <h2 className="pb-1 pt-1 text-base font-bold">Wallet details</h2>
          <DetailList>
            <DetailRow
              label="Wallet ID"
              mono
              value={
                <span className="inline-flex items-center gap-1">
                  {wallet.walletNumber}
                  <CopyButton value={wallet.walletNumber.replace(/\s/g, "")} label="Wallet ID" />
                </span>
              }
            />
            <DetailRow
              label="Status"
              value={<Badge variant={wallet.status === "ACTIVE" ? "success" : "danger"}>{WALLET_STATUS_LABEL[wallet.status]}</Badge>}
            />
            <DetailRow label="Currency" value="Malaysian Ringgit (MYR)" />
            <DetailRow label="Ledger balance" value={formatMoney(wallet.balance)} />
            <DetailRow label="Available balance" value={formatMoney(wallet.availableBalance)} emphasis />
            {held > 0 && <DetailRow label="On hold (pending)" value={formatMoney(held)} />}
            <DetailRow label="Opened" value={formatDate(wallet.createdAt)} />
            <DetailRow label="Last activity" value={formatDateTime(wallet.updatedAt)} />
          </DetailList>
        </section>
      </div>

      <div className="flex items-start gap-3 rounded-xl bg-muted px-4 py-3 text-sm text-muted-foreground">
        <Lock className="mt-0.5 size-4 shrink-0" />
        <p>
          Your balance is calculated from your transaction history and can't be edited. To change it, make a deposit or a
          transfer.
        </p>
      </div>

      <SummaryStats summary={summary} />

      <section className="rounded-2xl border bg-card shadow-card">
        <div className="flex items-center justify-between px-6 py-5">
          <h2 className="text-base font-bold">Recent activity</h2>
          <Button asChild variant="ghost" size="sm">
            <Link to="/transactions">
              Full history <ArrowRight />
            </Link>
          </Button>
        </div>
        {recent.data.length === 0 ? (
          <EmptyState icon={Receipt} title="No activity yet" description="Deposits and transfers will show up here." />
        ) : (
          <div className="border-t">
            <TransactionTable transactions={recent.data} />
          </div>
        )}
      </section>
    </div>
  );
}
