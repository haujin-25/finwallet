import type { ReactNode } from "react";
import { Label } from "@/components/ui/label";

export function FormField({
  id,
  label,
  error,
  hint,
  children,
  labelAction,
}: {
  id: string;
  label: string;
  error?: string | undefined;
  hint?: ReactNode | undefined;
  children: ReactNode;
  labelAction?: ReactNode | undefined;
}) {
  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between">
        <Label htmlFor={id} className="text-[13px] font-semibold text-foreground">
          {label}
        </Label>
        {labelAction}
      </div>
      {children}
      {error ? (
        <p id={`${id}-error`} className="text-xs font-medium text-destructive">
          {error}
        </p>
      ) : hint ? (
        <p className="text-xs text-muted-foreground">{hint}</p>
      ) : null}
    </div>
  );
}
