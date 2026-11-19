import * as React from "react";
import { cn } from "@/lib/utils";

/** Large ringgit amount field. Emits the raw string; parsing to sen happens on submit. */
export const AmountInput = React.forwardRef<HTMLInputElement, React.ComponentProps<"input">>(
  ({ className, ...props }, ref) => (
    <div
      className={cn(
        "flex h-16 items-center rounded-xl border border-input bg-card px-4 transition-colors focus-within:border-ring focus-within:ring-4 focus-within:ring-ring/15 has-[[aria-invalid=true]]:border-destructive",
        className,
      )}
    >
      <span className="mr-3 text-lg font-bold text-muted-foreground">RM</span>
      <input
        ref={ref}
        inputMode="decimal"
        autoComplete="off"
        placeholder="0.00"
        className="w-full bg-transparent text-2xl font-bold text-foreground tabular outline-none placeholder:text-muted-foreground/50"
        {...props}
      />
    </div>
  ),
);
AmountInput.displayName = "AmountInput";
