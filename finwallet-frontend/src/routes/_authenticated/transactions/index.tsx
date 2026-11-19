import { useSuspenseQuery } from "@tanstack/react-query";
import { createFileRoute, redirect, useNavigate, useRouterState } from "@tanstack/react-router";
import { SearchX } from "lucide-react";
import { useCallback } from "react";
import { EmptyState } from "@/components/common/empty-state";
import { PageHeader } from "@/components/common/page-header";
import { PageSkeleton } from "@/components/common/page-skeleton";
import { RouteError, RouteNotFound } from "@/components/common/route-boundaries";
import {
  TransactionFilters,
  type TransactionFilterValues,
} from "@/components/transactions/transaction-filters";
import { TransactionPagination } from "@/components/transactions/transaction-pagination";
import { TransactionTable } from "@/components/transactions/transaction-table";
import { TransferHistoryList } from "@/components/transactions/transfer-history-list";
import { DEMO_MODE } from "@/config/demo";
import { transactionListQuery, transferHistoryQuery } from "@/hooks/queries";
import { ApiError } from "@/services";
import { cn } from "@/lib/utils";
import type { TransactionListParams, TransactionStatus, TransactionType } from "@/types";

const PAGE_SIZE = 10;
const TYPES: TransactionType[] = ["DEPOSIT", "TRANSFER_IN", "TRANSFER_OUT"];
const STATUSES: TransactionStatus[] = ["COMPLETED", "PENDING", "FAILED"];
const DATE = /^\d{4}-\d{2}-\d{2}$/;

interface TransactionsSearch extends TransactionFilterValues {
  page?: number | undefined;
}

function toParams(search: TransactionsSearch): TransactionListParams {
  return {
    page: search.page ?? 1,
    pageSize: PAGE_SIZE,
    search: search.q,
    type: search.type,
    status: search.status,
    from: search.from,
    to: search.to,
  };
}

export const Route = createFileRoute("/_authenticated/transactions/")({
  validateSearch: (search: Record<string, unknown>): TransactionsSearch => {
    const page = Number(search["page"]);
    return {
      q:
        typeof search["q"] === "string" && search["q"].trim()
          ? search["q"].trim().slice(0, 80)
          : undefined,
      type: TYPES.includes(search["type"] as TransactionType)
        ? (search["type"] as TransactionType)
        : undefined,
      status: STATUSES.includes(search["status"] as TransactionStatus)
        ? (search["status"] as TransactionStatus)
        : undefined,
      from:
        typeof search["from"] === "string" && DATE.test(search["from"])
          ? search["from"]
          : undefined,
      to: typeof search["to"] === "string" && DATE.test(search["to"]) ? search["to"] : undefined,
      page:
        Number.isSafeInteger(page) &&
        page > 1 &&
        (DEMO_MODE || (page - 1) * PAGE_SIZE <= 2_147_483_647)
          ? page
          : undefined,
    };
  },
  loaderDeps: ({ search }) => toParams(search),
  head: () => ({
    meta: [
      { title: "Transactions — FinWallet" },
      {
        name: "description",
        content: "Search and filter your full FinWallet transaction history.",
      },
      { property: "og:title", content: "Transactions — FinWallet" },
      {
        property: "og:description",
        content: "Search and filter your full FinWallet transaction history.",
      },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary" },
    ],
  }),
  loader: async ({ context: { queryClient, user }, deps, location }) => {
    if (DEMO_MODE) {
      await queryClient.ensureQueryData(transactionListQuery(deps));
      return undefined;
    }

    const page = (deps.page ?? 1) - 1;
    const history = await queryClient
      .fetchQuery(transferHistoryQuery(user.id, page, PAGE_SIZE))
      .catch((error: unknown) => {
        if (error instanceof ApiError && error.status === 401) {
          queryClient.clear();
          throw redirect({ to: "/login", search: { redirect: location.href }, replace: true });
        }
        throw error;
      });
    if (page > 0 && page >= history.totalPages) {
      throw redirect({
        to: "/transactions",
        search: { page: history.totalPages > 1 ? history.totalPages : undefined },
        replace: true,
      });
    }
    return history;
  },
  pendingComponent: PageSkeleton,
  errorComponent: RouteError,
  notFoundComponent: () => <RouteNotFound />,
  component: DEMO_MODE ? TransactionsPage : LiveTransactionsPage,
});

function LiveTransactionsPage() {
  const history = Route.useLoaderData();
  const navigate = useNavigate({ from: "/transactions/" });
  const loading = useRouterState({ select: (state) => state.status === "pending" });
  if (!history) return null;

  return (
    <div className="space-y-6 animate-rise">
      <PageHeader
        title="Transactions"
        description="Your incoming and outgoing FinWallet transfers."
      />
      <section
        className={cn("rounded-2xl border bg-card shadow-card", loading && "opacity-60")}
        aria-busy={loading}
      >
        <TransferHistoryList
          history={history}
          onPage={(page) => void navigate({ search: { page: page > 1 ? page : undefined } })}
        />
      </section>
    </div>
  );
}

function TransactionsPage() {
  const search = Route.useSearch();
  const navigate = useNavigate({ from: "/transactions/" });
  const { data } = useSuspenseQuery(transactionListQuery(toParams(search)));
  const loading = useRouterState({ select: (s) => s.status === "pending" });

  const onFilter = useCallback(
    (patch: Partial<TransactionFilterValues>) =>
      navigate({ search: (prev) => ({ ...prev, ...patch, page: undefined }), replace: true }),
    [navigate],
  );

  return (
    <div className="space-y-6 animate-rise">
      <PageHeader title="Transactions" description="Every deposit and transfer in your wallet." />

      <section className="rounded-2xl border bg-card shadow-card">
        <div className="border-b p-4 sm:p-6">
          <TransactionFilters value={search} onChange={onFilter} />
        </div>
        <div className={cn("transition-opacity", loading && "opacity-60")}>
          {data.data.length === 0 ? (
            <EmptyState
              icon={SearchX}
              title="No transactions match"
              description="Try a different search term, or clear the filters to see everything."
            />
          ) : (
            <TransactionTable transactions={data.data} />
          )}
        </div>
        {data.meta.total > 0 && (
          <TransactionPagination
            meta={data.meta}
            onPage={(page) =>
              navigate({ search: (prev) => ({ ...prev, page: page > 1 ? page : undefined }) })
            }
          />
        )}
      </section>
    </div>
  );
}
