import { ArrowDownLeft, ArrowUpRight, Plus } from "lucide-react";
import { cn } from "@/lib/utils";
import type { TransactionType } from "@/types";

const STYLE: Record<TransactionType, { icon: typeof Plus; className: string }> = {
  DEPOSIT: { icon: Plus, className: "bg-accent text-accent-foreground" },
  TRANSFER_IN: { icon: ArrowDownLeft, className: "bg-success-soft text-success" },
  TRANSFER_OUT: { icon: ArrowUpRight, className: "bg-secondary text-primary" },
};

export function TransactionIcon({ type, className }: { type: TransactionType; className?: string  | undefined}) {
  const { icon: Icon, className: tone } = STYLE[type];
  return (
    <span className={cn("inline-flex size-9 shrink-0 items-center justify-center rounded-xl", tone, className)}>
      <Icon className="size-4" />
    </span>
  );
}
