import { Search, X } from "lucide-react";
import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import type { TransactionStatus, TransactionType } from "@/types";
import { TRANSACTION_STATUS_LABEL, TRANSACTION_TYPE_LABEL } from "@/utils/labels";

export interface TransactionFilterValues {
  q?: string | undefined;
  type?: TransactionType | undefined;
  status?: TransactionStatus | undefined;
  from?: string | undefined;
  to?: string | undefined;
}

export function TransactionFilters({
  value,
  onChange,
}: {
  value: TransactionFilterValues;
  onChange: (patch: Partial<TransactionFilterValues>) => void;
}) {
  const [query, setQuery] = useState(value.q ?? "");

  useEffect(() => setQuery(value.q ?? ""), [value.q]);

  useEffect(() => {
    const trimmed = query.trim();
    if (trimmed === (value.q ?? "")) return;
    const id = setTimeout(() => onChange({ q: trimmed || undefined }), 300);
    return () => clearTimeout(id);
  }, [query, value.q, onChange]);

  const hasFilters = !!(value.q || value.type || value.status || value.from || value.to);

  return (
    <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-[1.6fr_1fr_1fr_1fr_1fr_auto]">
      <div className="relative md:col-span-2 xl:col-span-1">
        <Search className="pointer-events-none absolute left-3.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search reference, name, note…"
          className="pl-10"
          aria-label="Search transactions"
        />
      </div>

      <Select
        value={value.type ?? "all"}
        onValueChange={(v) => onChange({ type: v === "all" ? undefined : (v as TransactionType) })}
      >
        <SelectTrigger className="h-11 rounded-lg bg-card" aria-label="Filter by type">
          <SelectValue placeholder="All types" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="all">All types</SelectItem>
          {(Object.keys(TRANSACTION_TYPE_LABEL) as TransactionType[]).map((type) => (
            <SelectItem key={type} value={type}>
              {TRANSACTION_TYPE_LABEL[type]}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>

      <Select
        value={value.status ?? "all"}
        onValueChange={(v) => onChange({ status: v === "all" ? undefined : (v as TransactionStatus) })}
      >
        <SelectTrigger className="h-11 rounded-lg bg-card" aria-label="Filter by status">
          <SelectValue placeholder="All statuses" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="all">All statuses</SelectItem>
          {(Object.keys(TRANSACTION_STATUS_LABEL) as TransactionStatus[]).map((status) => (
            <SelectItem key={status} value={status}>
              {TRANSACTION_STATUS_LABEL[status]}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>

      <Input
        type="date"
        value={value.from ?? ""}
        max={value.to}
        onChange={(e) => onChange({ from: e.target.value || undefined })}
        aria-label="From date"
        className="text-sm"
      />
      <Input
        type="date"
        value={value.to ?? ""}
        min={value.from}
        onChange={(e) => onChange({ to: e.target.value || undefined })}
        aria-label="To date"
        className="text-sm"
      />

      <Button
        variant="ghost"
        className="h-11"
        disabled={!hasFilters}
        onClick={() => {
          setQuery("");
          onChange({ q: undefined, type: undefined, status: undefined, from: undefined, to: undefined });
        }}
      >
        <X /> Clear
      </Button>
    </div>
  );
}
