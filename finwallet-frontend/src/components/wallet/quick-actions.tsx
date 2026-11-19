import { Link } from "@tanstack/react-router";
import { ArrowUpRight, ChevronRight, ListOrdered, Plus } from "lucide-react";

const ACTIONS = [
  { to: "/deposit", label: "Deposit", hint: "Top up your wallet", icon: Plus },
  { to: "/transfer", label: "Transfer", hint: "Send to a FinWallet user", icon: ArrowUpRight },
  { to: "/transactions", label: "View transactions", hint: "Full history & filters", icon: ListOrdered },
] as const;

export function QuickActions() {
  return (
    <div className="rounded-2xl border bg-card p-2 shadow-card">
      <p className="px-4 pb-1 pt-3 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
        Quick actions
      </p>
      <ul>
        {ACTIONS.map(({ to, label, hint, icon: Icon }) => (
          <li key={to}>
            <Link
              to={to}
              className="group flex items-center gap-3 rounded-xl px-4 py-3 transition-colors hover:bg-muted"
            >
              <span className="inline-flex size-10 items-center justify-center rounded-xl bg-secondary text-primary transition-colors group-hover:bg-primary group-hover:text-primary-foreground">
                <Icon className="size-4" />
              </span>
              <span className="flex-1">
                <span className="block text-sm font-semibold text-foreground">{label}</span>
                <span className="block text-xs text-muted-foreground">{hint}</span>
              </span>
              <ChevronRight className="size-4 text-muted-foreground transition-transform group-hover:translate-x-0.5" />
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}
