import { Link, useNavigate } from "@tanstack/react-router";
import { ChevronRight } from "lucide-react";
import type { Transaction } from "@/types";
import { formatDateTime } from "@/utils/date";
import { TRANSACTION_TYPE_LABEL } from "@/utils/labels";
import { StatusBadge } from "./status-badge";
import { TransactionAmount } from "./transaction-amount";
import { TransactionIcon } from "./transaction-icon";

export function TransactionTable({ transactions }: { transactions: Transaction[] }) {
  const navigate = useNavigate();
  const open = (id: string) => navigate({ to: "/transactions/$id", params: { id } });

  return (
    <>
      {/* Desktop */}
      <div className="hidden overflow-x-auto md:block">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground">
              <th className="px-6 py-3 font-semibold">Counterparty</th>
              <th className="px-4 py-3 font-semibold">Reference</th>
              <th className="px-4 py-3 font-semibold">Type</th>
              <th className="px-4 py-3 font-semibold">Date & time</th>
              <th className="px-4 py-3 font-semibold">Status</th>
              <th className="px-6 py-3 text-right font-semibold">Amount</th>
            </tr>
          </thead>
          <tbody className="divide-y">
            {transactions.map((tx) => (
              <tr
                key={tx.id}
                onClick={() => open(tx.id)}
                className="group cursor-pointer transition-colors hover:bg-muted/60"
              >
                <td className="px-6 py-3.5">
                  <div className="flex items-center gap-3">
                    <TransactionIcon type={tx.type} />
                    <div className="min-w-0">
                      <p className="truncate font-semibold text-foreground">{tx.counterparty.name}</p>
                      <p className="truncate text-xs text-muted-foreground">
                        {tx.description ?? tx.counterparty.identifier}
                      </p>
                    </div>
                  </div>
                </td>
                <td className="px-4 py-3.5">
                  <Link
                    to="/transactions/$id"
                    params={{ id: tx.id }}
                    onClick={(e) => e.stopPropagation()}
                    className="font-mono text-xs text-muted-foreground hover:text-foreground hover:underline"
                  >
                    {tx.reference}
                  </Link>
                </td>
                <td className="px-4 py-3.5 text-muted-foreground">{TRANSACTION_TYPE_LABEL[tx.type]}</td>
                <td className="px-4 py-3.5 whitespace-nowrap text-muted-foreground tabular">
                  {formatDateTime(tx.createdAt)}
                </td>
                <td className="px-4 py-3.5">
                  <StatusBadge status={tx.status} />
                </td>
                <td className="px-6 py-3.5 text-right">
                  <TransactionAmount transaction={tx} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Mobile */}
      <ul className="divide-y md:hidden">
        {transactions.map((tx) => (
          <li key={tx.id}>
            <Link
              to="/transactions/$id"
              params={{ id: tx.id }}
              className="flex items-center gap-3 px-4 py-3.5 transition-colors active:bg-muted"
            >
              <TransactionIcon type={tx.type} />
              <div className="min-w-0 flex-1">
                <div className="flex items-baseline justify-between gap-2">
                  <p className="truncate text-sm font-semibold text-foreground">{tx.counterparty.name}</p>
                  <TransactionAmount transaction={tx} className="text-sm" />
                </div>
                <div className="mt-1 flex items-center justify-between gap-2">
                  <p className="truncate font-mono text-[11px] text-muted-foreground">
                    {tx.reference} · {formatDateTime(tx.createdAt)}
                  </p>
                  <StatusBadge status={tx.status} />
                </div>
              </div>
              <ChevronRight className="size-4 shrink-0 text-muted-foreground" />
            </Link>
          </li>
        ))}
      </ul>
    </>
  );
}
