import { cn } from "@/lib/utils";

export function LogoMark({ className }: { className?: string  | undefined}) {
  return (
    <span
      className={cn(
        "relative inline-flex size-8 shrink-0 items-center justify-center overflow-hidden rounded-[10px] bg-primary text-primary-foreground",
        className,
      )}
      aria-hidden
    >
      <svg viewBox="0 0 24 24" className="size-[18px]" fill="none">
        <path d="M6 18V6h11" stroke="currentColor" strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round" />
        <path d="M6 12h7" stroke="currentColor" strokeWidth="2.6" strokeLinecap="round" />
        <circle cx="17.5" cy="16.5" r="2.5" className="fill-success" />
      </svg>
    </span>
  );
}

export function Logo({ className, tone = "default" }: { className?: string | undefined; tone?: "default" | "ink"  | undefined}) {
  return (
    <span className={cn("inline-flex items-center gap-2.5", className)}>
      <LogoMark className={tone === "ink" ? "bg-ink-foreground text-ink" : undefined} />
      <span className={cn("text-[17px] font-extrabold tracking-tight", tone === "ink" && "text-ink-foreground")}>
        FinWallet
      </span>
    </span>
  );
}
