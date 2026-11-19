import { cn } from "@/lib/utils";
import type { Transaction } from "@/types";
import { formatMoney } from "@/utils/money";

export function TransactionAmount({
  transaction,
  className,
}: {
  transaction: Pick<Transaction, "direction" | "totalAmount" | "amount" | "status" | "currency">;
  className?: string | undefined;
}) {
  const credit = transaction.direction === "CREDIT";
  const value = credit ? transaction.amount : transaction.totalAmount;
  return (
    <span
      className={cn(
        "font-semibold tabular whitespace-nowrap",
        credit ? "text-success" : "text-foreground",
        transaction.status === "FAILED" && "text-muted-foreground line-through decoration-1",
        className,
      )}
    >
      {formatMoney(value, transaction.currency, { sign: credit ? "credit" : "debit" })}
    </span>
  );
}
