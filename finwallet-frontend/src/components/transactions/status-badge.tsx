import { CheckCircle2, Clock3, XCircle } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import type { TransactionStatus } from "@/types";
import { TRANSACTION_STATUS_LABEL } from "@/utils/labels";

const VARIANT = { COMPLETED: "success", PENDING: "warning", FAILED: "danger" } as const;
const ICON = { COMPLETED: CheckCircle2, PENDING: Clock3, FAILED: XCircle } as const;

export function StatusBadge({ status }: { status: TransactionStatus }) {
  const Icon = ICON[status];
  return (
    <Badge variant={VARIANT[status]} className="font-semibold">
      <Icon className="size-3" />
      {TRANSACTION_STATUS_LABEL[status]}
    </Badge>
  );
}
