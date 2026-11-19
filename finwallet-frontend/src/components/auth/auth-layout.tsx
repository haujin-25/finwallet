import { ArrowDownLeft, ArrowUpRight, ShieldCheck } from "lucide-react";
import type { ReactNode } from "react";
import { Logo } from "@/components/brand/logo";

function PreviewCard() {
  return (
    <div className="relative w-full max-w-sm">
      <div className="rounded-3xl border border-ink-foreground/10 bg-ink-foreground/[0.06] p-6 backdrop-blur-sm">
        <p className="text-[11px] font-semibold uppercase tracking-[0.14em] text-ink-muted">Total balance</p>
        <p className="mt-2 text-4xl font-extrabold tracking-tight tabular">
          <span className="mr-1.5 text-lg text-ink-muted">RM</span>12,480<span className="text-2xl text-ink-muted">.50</span>
        </p>
        <div className="mt-6 space-y-3">
          {[
            { name: "Aisyah Rahman", note: "Trip refund", amount: "+RM 180.00", icon: ArrowDownLeft, credit: true },
            { name: "Priya Nair", note: "Nasi lemak breakfast", amount: "−RM 64.50", icon: ArrowUpRight, credit: false },
          ].map((row) => (
            <div key={row.name} className="flex items-center gap-3 rounded-2xl bg-ink-foreground/[0.06] px-3 py-2.5">
              <span className="inline-flex size-8 items-center justify-center rounded-xl bg-ink-foreground/10">
                <row.icon className="size-4" />
              </span>
              <div className="flex-1">
                <p className="text-sm font-semibold">{row.name}</p>
                <p className="text-xs text-ink-muted">{row.note}</p>
              </div>
              <p className={row.credit ? "text-sm font-bold text-success tabular" : "text-sm font-bold tabular"}>
                {row.amount}
              </p>
            </div>
          ))}
        </div>
      </div>
      <div className="absolute -bottom-5 -right-4 flex items-center gap-2 rounded-2xl bg-card px-4 py-3 text-foreground shadow-elevated">
        <span className="inline-flex size-7 items-center justify-center rounded-full bg-success-soft text-success">
          <ShieldCheck className="size-4" />
        </span>
        <span className="text-xs font-semibold">Transfer completed</span>
      </div>
    </div>
  );
}

export function AuthLayout({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  return (
    <div className="grid min-h-screen lg:grid-cols-[1.05fr_1fr]">
      <aside className="relative hidden overflow-hidden surface-ink lg:flex lg:flex-col lg:justify-between lg:p-12">
        <div className="pointer-events-none absolute inset-0 ink-grid" />
        <Logo tone="ink" className="relative" />
        <div className="relative space-y-10">
          <div className="max-w-md space-y-4">
            <h2 className="text-4xl font-extrabold leading-[1.1] tracking-tight">Your money, moved with clarity.</h2>
            <p className="text-base leading-relaxed text-ink-muted">
              Send ringgit to anyone on FinWallet, top up in seconds and see every sen accounted for.
            </p>
          </div>
          <PreviewCard />
        </div>
        <p className="relative text-xs text-ink-muted">
          Prototype environment. All balances and transactions are simulated.
        </p>
      </aside>

      <main className="flex items-center justify-center px-5 py-12 sm:px-8">
        <div className="w-full max-w-[400px] animate-rise">
          <Logo className="mb-10 lg:hidden" />
          <h1 className="text-[28px] font-extrabold text-foreground">{title}</h1>
          <p className="mt-2 text-sm text-muted-foreground">{subtitle}</p>
          <div className="mt-8">{children}</div>
        </div>
      </main>
    </div>
  );
}
