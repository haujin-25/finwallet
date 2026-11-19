import { ArrowDownLeft, ArrowUpRight, Clock3 } from "lucide-react";
import type { WalletSummary } from "@/types";
import { formatMoney } from "@/utils/money";

export function SummaryStats({ summary }: { summary: WalletSummary }) {
  const items = [
    {
      label: "Money in",
      value: formatMoney(summary.moneyIn),
      icon: ArrowDownLeft,
      tone: "bg-success-soft text-success",
    },
    {
      label: "Money out",
      value: formatMoney(summary.moneyOut),
      icon: ArrowUpRight,
      tone: "bg-secondary text-primary",
    },
    {
      label: "Pending",
      value: `${summary.pendingCount} ${summary.pendingCount === 1 ? "transaction" : "transactions"}`,
      icon: Clock3,
      tone: "bg-warning-soft text-warning",
    },
  ];

  return (
    <div className="rounded-2xl border bg-card shadow-card">
      <div className="flex items-center justify-between px-6 pt-5">
        <p className="text-sm font-semibold text-foreground">Last {summary.periodDays} days</p>
        <p className="text-xs text-muted-foreground">
          {summary.completedCount} completed · {summary.failedCount} failed
        </p>
      </div>
      <div className="grid divide-y p-2 sm:grid-cols-3 sm:divide-x sm:divide-y-0">
        {items.map(({ label, value, icon: Icon, tone }) => (
          <div key={label} className="flex items-center gap-3 px-4 py-4">
            <span className={`inline-flex size-10 items-center justify-center rounded-xl ${tone}`}>
              <Icon className="size-4" />
            </span>
            <div className="min-w-0">
              <p className="text-xs text-muted-foreground">{label}</p>
              <p className="truncate text-base font-bold text-foreground tabular">{value}</p>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
