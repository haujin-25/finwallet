import { useQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { Bell } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { TransactionIcon } from "@/components/transactions/transaction-icon";
import { recentTransactionsQuery } from "@/hooks/queries";
import type { Transaction } from "@/types";
import { formatRelative } from "@/utils/date";
import { formatMoney } from "@/utils/money";

function describe(tx: Transaction): string {
  const amount = formatMoney(tx.direction === "CREDIT" ? tx.amount : tx.totalAmount);
  const verb = tx.status === "COMPLETED" ? "completed" : tx.status === "PENDING" ? "is pending" : "failed";
  if (tx.type === "DEPOSIT") return `Deposit of ${amount} ${verb}`;
  if (tx.type === "TRANSFER_IN") return `${tx.counterparty.name} sent you ${amount}`;
  return `Transfer of ${amount} to ${tx.counterparty.name} ${verb}`;
}

export function NotificationsMenu() {
  const { data } = useQuery(recentTransactionsQuery(5));
  const items = data?.data ?? [];
  const attention = items.filter((tx) => tx.status !== "COMPLETED").length;

  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button variant="ghost" size="icon" className="relative rounded-full" aria-label="Notifications">
          <Bell className="size-[18px]" />
          {attention > 0 && <span className="absolute right-2 top-2 size-2 rounded-full bg-success ring-2 ring-card" />}
        </Button>
      </PopoverTrigger>
      <PopoverContent align="end" className="w-80 rounded-2xl p-0 shadow-elevated">
        <div className="flex items-center justify-between border-b px-4 py-3">
          <p className="text-sm font-bold">Notifications</p>
          <span className="text-xs text-muted-foreground">Recent activity</span>
        </div>
        {items.length === 0 ? (
          <p className="px-4 py-8 text-center text-sm text-muted-foreground">You're all caught up.</p>
        ) : (
          <ul className="max-h-80 divide-y overflow-y-auto">
            {items.map((tx) => (
              <li key={tx.id}>
                <Link
                  to="/transactions/$id"
                  params={{ id: tx.id }}
                  className="flex gap-3 px-4 py-3 transition-colors hover:bg-muted"
                >
                  <TransactionIcon type={tx.type} className="size-8" />
                  <div className="min-w-0">
                    <p className="text-sm leading-snug text-foreground">{describe(tx)}</p>
                    <p className="mt-0.5 text-xs text-muted-foreground">{formatRelative(tx.createdAt)}</p>
                  </div>
                </Link>
              </li>
            ))}
          </ul>
        )}
        <div className="border-t p-2">
          <Button asChild variant="ghost" size="sm" className="w-full">
            <Link to="/transactions">View all transactions</Link>
          </Button>
        </div>
      </PopoverContent>
    </Popover>
  );
}
