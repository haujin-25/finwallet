import { FlaskConical } from "lucide-react";
import { cn } from "@/lib/utils";

export function SimulatedNotice({ children, className }: { children?: React.ReactNode | undefined; className?: string  | undefined}) {
  return (
    <div
      className={cn(
        "flex items-start gap-3 rounded-xl border border-dashed border-warning/40 bg-warning-soft px-4 py-3 text-sm text-foreground",
        className,
      )}
      role="note"
    >
      <FlaskConical className="mt-0.5 size-4 shrink-0 text-warning" />
      <p className="leading-relaxed">
        {children ?? (
          <>
            <span className="font-semibold">Simulation only.</span> No real money moves and no bank or card is
            connected. All transactions here are test data.
          </>
        )}
      </p>
    </div>
  );
}
