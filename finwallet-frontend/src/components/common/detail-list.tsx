import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

export function DetailList({ children, className }: { children: ReactNode; className?: string  | undefined}) {
  return <dl className={cn("divide-y divide-border", className)}>{children}</dl>;
}

export function DetailRow({
  label,
  value,
  emphasis,
  mono,
}: {
  label: string;
  value: ReactNode;
  emphasis?: boolean | undefined;
  mono?: boolean | undefined;
}) {
  return (
    <div className="flex items-start justify-between gap-6 py-3.5">
      <dt className="text-sm text-muted-foreground">{label}</dt>
      <dd
        className={cn(
          "text-right text-sm font-medium text-foreground tabular",
          emphasis && "text-base font-bold",
          mono && "font-mono text-[13px]",
        )}
      >
        {value}
      </dd>
    </div>
  );
}
