import { CheckCircle2, Clock3, XCircle } from "lucide-react";
import type { ReactNode } from "react";
import { DetailList, DetailRow } from "@/components/common/detail-list";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import type { TransactionStatus } from "@/types";

const LOOK: Record<TransactionStatus, { icon: typeof CheckCircle2; ring: string; tone: string }> = {
  COMPLETED: { icon: CheckCircle2, ring: "bg-success-soft", tone: "text-success" },
  PENDING: { icon: Clock3, ring: "bg-warning-soft", tone: "text-warning" },
  FAILED: { icon: XCircle, ring: "bg-destructive-soft", tone: "text-destructive" },
};

export interface ResultRow {
  label: string;
  value: ReactNode;
  mono?: boolean | undefined;
  emphasis?: boolean | undefined;
}

export function ResultPanel({
  status,
  title,
  message,
  amount,
  rows,
  actions,
  simulated = true,
}: {
  status: TransactionStatus;
  title: string;
  message: string;
  amount: string;
  rows: ResultRow[];
  actions: ReactNode;
  simulated?: boolean;
}) {
  const { icon: Icon, ring, tone } = LOOK[status];
  return (
    <div className="mx-auto max-w-lg animate-rise">
      <div className="rounded-3xl border bg-card p-6 shadow-elevated sm:p-8">
        <div className="flex flex-col items-center text-center">
          <span
            className={cn("inline-flex size-16 items-center justify-center rounded-full", ring)}
          >
            <Icon className={cn("size-8", tone)} strokeWidth={2.2} />
          </span>
          <h1 className="mt-5 text-xl font-bold">{title}</h1>
          <p className="mt-1.5 max-w-sm text-sm text-muted-foreground">{message}</p>
          <p
            className={cn(
              "mt-5 text-4xl font-extrabold tracking-tight tabular",
              status === "FAILED" && "text-muted-foreground",
            )}
          >
            {amount}
          </p>
          {simulated && (
            <Badge variant="simulated" className="mt-3">
              Simulated transaction
            </Badge>
          )}
        </div>
        <DetailList className="mt-6 border-t">
          {rows.map((row) => (
            <DetailRow key={row.label} {...row} />
          ))}
        </DetailList>
        <div className="mt-6 flex flex-col gap-2 sm:flex-row-reverse">{actions}</div>
      </div>
    </div>
  );
}
