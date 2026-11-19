import { ArrowLeftRight } from "lucide-react";
import { EmptyState } from "@/components/common/empty-state";
import type { TransferHistoryPage } from "@/types";
import { formatDateTime } from "@/utils/date";
import { StatusBadge } from "./status-badge";
import { TransactionAmount } from "./transaction-amount";
import { TransactionIcon } from "./transaction-icon";
import { TransactionPagination } from "./transaction-pagination";

export function TransferHistoryList({
  history,
  onPage,
}: {
  history: TransferHistoryPage;
  onPage: (page: number) => void;
}) {
  if (history.items.length === 0) {
    return (
      <EmptyState
        icon={ArrowLeftRight}
        title="No transfers yet"
        description="Incoming and outgoing transfers will appear here."
      />
    );
  }

  return (
    <>
      <ul className="divide-y" aria-label="Transfers">
        {history.items.map((item) => {
          const incoming = item.direction === "INCOMING";
          return (
            <li key={item.transferReference} className="flex gap-3 p-4 sm:px-6">
              <TransactionIcon type={incoming ? "TRANSFER_IN" : "TRANSFER_OUT"} />
              <div className="grid min-w-0 flex-1 gap-2 sm:grid-cols-[minmax(0,1fr)_auto]">
                <div className="min-w-0 space-y-1 text-sm">
                  <p className="break-words font-semibold">
                    {incoming ? "Incoming" : "Outgoing"}
                    {item.operationType === "REVERSAL" ? " reversal" : " transfer"}
                    {item.counterpartyName && ` · ${item.counterpartyName}`}
                  </p>
                  {item.description && (
                    <p className="break-words text-muted-foreground">{item.description}</p>
                  )}
                  <p className="break-all font-mono text-xs text-muted-foreground">
                    {item.transferReference}
                  </p>
                  {item.reversalOfReference && (
                    <p className="break-all text-xs text-muted-foreground">
                      Reversal of {item.reversalOfReference}
                    </p>
                  )}
                  <time className="text-xs text-muted-foreground" dateTime={item.createdAt}>
                    {formatDateTime(item.createdAt)}
                  </time>
                </div>
                <div className="flex items-center gap-2 sm:flex-col sm:items-end">
                  <TransactionAmount
                    transaction={{
                      direction: incoming ? "CREDIT" : "DEBIT",
                      amount: item.amount,
                      totalAmount: item.amount,
                      currency: item.currency,
                      status: item.status,
                    }}
                  />
                  <StatusBadge status={item.status} />
                </div>
              </div>
            </li>
          );
        })}
      </ul>
      <TransactionPagination
        meta={{
          page: history.page + 1,
          pageSize: history.size,
          total: history.totalElements,
          totalPages: history.totalPages,
        }}
        hasNext={history.hasNext}
        onPage={onPage}
      />
    </>
  );
}
