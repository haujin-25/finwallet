import { useSuspenseQuery } from "@tanstack/react-query";
import { Link, createFileRoute, redirect, type ErrorComponentProps } from "@tanstack/react-router";
import { ArrowRight, LockKeyhole, Receipt, Wallet } from "lucide-react";
import { EmptyState } from "@/components/common/empty-state";
import { PageHeader } from "@/components/common/page-header";
import { PageSkeleton } from "@/components/common/page-skeleton";
import { RouteError, RouteNotFound } from "@/components/common/route-boundaries";
import { TransactionTable } from "@/components/transactions/transaction-table";
import { Button } from "@/components/ui/button";
import { BalanceCard } from "@/components/wallet/balance-card";
import { QuickActions } from "@/components/wallet/quick-actions";
import { SummaryStats } from "@/components/wallet/summary-stats";
import {
  currentWalletQuery,
  recentTransactionsQuery,
  walletQuery,
  walletSummaryQuery,
} from "@/hooks/queries";
import { useCurrentUser } from "@/hooks/use-auth";
import { DEMO_MODE } from "@/config/demo";
import { ApiError } from "@/services";
import { greetingFor } from "@/utils/date";

export const Route = createFileRoute("/_authenticated/overview")({
  head: () => ({
    meta: [
      { title: "Overview — FinWallet" },
      { name: "description", content: "Your FinWallet balance and wallet status." },
      { property: "og:title", content: "Overview — FinWallet" },
      { property: "og:description", content: "Your FinWallet balance and wallet status." },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary" },
    ],
  }),
  loader: async ({ context: { queryClient, user } }) => {
    if (!DEMO_MODE) {
      try {
        return await queryClient.fetchQuery(currentWalletQuery(user.id));
      } catch (error) {
        if (error instanceof ApiError && error.status === 401) {
          queryClient.clear();
          throw redirect({ to: "/login", search: { redirect: "/overview" }, replace: true });
        }
        throw error;
      }
    }
    await Promise.all([
      queryClient.ensureQueryData(walletQuery()),
      queryClient.ensureQueryData(walletSummaryQuery(30)),
      queryClient.ensureQueryData(recentTransactionsQuery(5)),
    ]);
    return undefined;
  },
  pendingComponent: PageSkeleton,
  errorComponent: OverviewError,
  notFoundComponent: () => <RouteNotFound />,
  component: DEMO_MODE ? OverviewPage : SignedInOverview,
});

function SignedInOverview() {
  const user = useCurrentUser();
  const wallet = Route.useLoaderData();

  return (
    <div className="space-y-6 animate-rise">
      <PageHeader
        eyebrow={<p className="text-sm font-medium text-muted-foreground">{greetingFor()},</p>}
        title={user.fullName.split(" ")[0] ?? user.fullName}
        description="Here's where your money stands today."
      />
      {wallet && <BalanceCard wallet={wallet} showActions={false} />}
    </div>
  );
}

function OverviewError(props: ErrorComponentProps) {
  const { error } = props;
  if (!DEMO_MODE && error instanceof ApiError) {
    if (error.status === 404) {
      return <EmptyState icon={Wallet} title="Wallet not found" description={error.message} />;
    }
    if (error.status === 403) {
      return (
        <EmptyState icon={LockKeyhole} title="Wallet access denied" description={error.message} />
      );
    }
  }
  return <RouteError {...props} />;
}

function OverviewPage() {
  const user = useCurrentUser();
  const { data: wallet } = useSuspenseQuery(walletQuery());
  const { data: summary } = useSuspenseQuery(walletSummaryQuery(30));
  const { data: recent } = useSuspenseQuery(recentTransactionsQuery(5));

  return (
    <div className="space-y-6 animate-rise">
      <PageHeader
        eyebrow={<p className="text-sm font-medium text-muted-foreground">{greetingFor()},</p>}
        title={user.fullName.split(" ")[0] ?? user.fullName}
        description="Here's where your money stands today."
      />

      <div className="grid gap-4 lg:grid-cols-3">
        <div className="lg:col-span-2">
          <BalanceCard wallet={wallet} />
        </div>
        <QuickActions />
      </div>

      <SummaryStats summary={summary} />

      <section className="rounded-2xl border bg-card shadow-card">
        <div className="flex items-center justify-between px-6 py-5">
          <div>
            <h2 className="text-base font-bold">Recent transactions</h2>
            <p className="text-xs text-muted-foreground">Your latest 5 transactions</p>
          </div>
          <Button asChild variant="ghost" size="sm">
            <Link to="/transactions">
              View all <ArrowRight />
            </Link>
          </Button>
        </div>
        {recent.data.length === 0 ? (
          <EmptyState
            icon={Receipt}
            title="No transactions yet"
            description="Make your first deposit to get started."
            action={
              <Button asChild>
                <Link to="/deposit">Deposit money</Link>
              </Button>
            }
          />
        ) : (
          <div className="border-t">
            <TransactionTable transactions={recent.data} />
          </div>
        )}
      </section>
    </div>
  );
}
