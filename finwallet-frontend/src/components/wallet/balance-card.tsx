import { Link } from "@tanstack/react-router";
import { ArrowUpRight, Eye, EyeOff, Plus } from "lucide-react";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { CopyButton } from "@/components/common/copy-button";
import type { CurrentWallet, Wallet } from "@/types";
import { formatMoney, splitMoney } from "@/utils/money";
import { WALLET_STATUS_LABEL } from "@/utils/labels";

export function BalanceCard({
  wallet,
  showActions = true,
}: {
  wallet: Wallet | CurrentWallet;
  showActions?: boolean | undefined;
}) {
  const [hidden, setHidden] = useState(false);
  const { whole, cents } = splitMoney(wallet.balance);
  const walletIdentifier = "walletNumber" in wallet ? wallet.walletNumber : String(wallet.walletId);

  return (
    <div className="relative overflow-hidden rounded-3xl surface-ink p-6 shadow-balance sm:p-8">
      <div className="pointer-events-none absolute inset-0 ink-grid" />
      <div className="relative flex h-full flex-col gap-8">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.14em] text-ink-muted">
              Total balance
            </p>
            <div className="mt-3 flex items-baseline gap-2">
              <span className="text-lg font-semibold text-ink-muted">RM</span>
              <span
                className="text-4xl font-extrabold tracking-tight tabular sm:text-5xl"
                aria-live="polite"
              >
                {hidden ? "••••••" : whole}
              </span>
              {!hidden && (
                <span className="text-2xl font-bold text-ink-muted tabular">.{cents}</span>
              )}
              <button
                type="button"
                onClick={() => setHidden((v) => !v)}
                className="ml-1 inline-flex size-8 items-center justify-center rounded-full text-ink-muted transition hover:bg-ink-foreground/10 hover:text-ink-foreground"
                aria-label={hidden ? "Show balance" : "Hide balance"}
              >
                {hidden ? <Eye className="size-4" /> : <EyeOff className="size-4" />}
              </button>
            </div>
          </div>
          <span className="inline-flex items-center gap-1.5 rounded-full bg-ink-foreground/10 px-3 py-1 text-xs font-semibold">
            <span
              className={
                wallet.status === "ACTIVE"
                  ? "size-1.5 rounded-full bg-success"
                  : "size-1.5 rounded-full bg-ink-muted"
              }
            />
            {wallet.currency} · {WALLET_STATUS_LABEL[wallet.status]}
          </span>
        </div>

        <div className="grid gap-6 sm:grid-cols-2">
          <div>
            <p className="text-xs text-ink-muted">Available to spend</p>
            <p className="mt-1 text-lg font-bold tabular">
              {hidden ? "RM ••••" : formatMoney(wallet.availableBalance, wallet.currency)}
            </p>
          </div>
          <div>
            <p className="text-xs text-ink-muted">Wallet ID</p>
            <p className="mt-1 flex items-center gap-1 font-mono text-sm tracking-wide">
              {walletIdentifier}
              <CopyButton value={walletIdentifier.replace(/\s/g, "")} label="Wallet ID" />
            </p>
          </div>
        </div>

        {showActions && (
          <div className="flex flex-wrap gap-2">
            <Button asChild variant="inkSolid" size="lg">
              <Link to="/deposit">
                <Plus /> Deposit
              </Link>
            </Button>
            <Button asChild variant="ink" size="lg">
              <Link to="/transfer">
                <ArrowUpRight /> Transfer
              </Link>
            </Button>
          </div>
        )}
      </div>
    </div>
  );
}
