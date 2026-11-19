import { ArrowDown, Loader2 } from "lucide-react";
import { DetailList, DetailRow } from "@/components/common/detail-list";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import type { Counterparty, TransferQuote } from "@/types";
import { initialsOf } from "@/utils/labels";
import { formatMoney } from "@/utils/money";

function Party({ label, party }: { label: string; party: Counterparty }) {
  return (
    <div className="flex items-center gap-3">
      <Avatar className="size-10">
        <AvatarFallback className="bg-secondary text-xs font-bold text-primary">{initialsOf(party.name)}</AvatarFallback>
      </Avatar>
      <div className="min-w-0">
        <p className="text-xs text-muted-foreground">{label}</p>
        <p className="truncate text-sm font-semibold">{party.name}</p>
        <p className="truncate text-xs text-muted-foreground">{party.identifier}</p>
      </div>
    </div>
  );
}

export function TransferConfirm({
  quote,
  description,
  pending,
  onBack,
  onConfirm,
}: {
  quote: TransferQuote;
  description?: string | undefined;
  pending: boolean;
  onBack: () => void;
  onConfirm: () => void;
}) {
  return (
    <div className="space-y-6">
      <div className="space-y-3 rounded-2xl bg-muted p-4">
        <Party label="From" party={quote.sender} />
        <div className="flex pl-3 text-muted-foreground">
          <ArrowDown className="size-4" />
        </div>
        <Party label="To" party={quote.recipient} />
      </div>

      <DetailList>
        <DetailRow label="Transfer amount" value={formatMoney(quote.amount, quote.currency)} />
        <DetailRow label="Transaction fee" value={quote.fee === 0 ? "Free" : formatMoney(quote.fee, quote.currency)} />
        {description && <DetailRow label="Description" value={description} />}
        <DetailRow label="Total amount" value={formatMoney(quote.totalAmount, quote.currency)} emphasis />
      </DetailList>

      <div className="flex flex-col-reverse gap-2 sm:flex-row">
        <Button type="button" variant="outline" size="xl" className="sm:flex-1" onClick={onBack} disabled={pending}>
          Edit details
        </Button>
        <Button type="button" size="xl" className="sm:flex-[2]" onClick={onConfirm} disabled={pending}>
          {pending && <Loader2 className="animate-spin" />}
          {pending ? "Sending…" : `Confirm and send ${formatMoney(quote.totalAmount, quote.currency)}`}
        </Button>
      </div>
    </div>
  );
}
