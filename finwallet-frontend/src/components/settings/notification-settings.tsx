import { useSuspenseQuery } from "@tanstack/react-query";
import { toast } from "sonner";
import { Switch } from "@/components/ui/switch";
import { notificationPreferencesQuery } from "@/hooks/queries";
import { useUpdateNotificationPreferences } from "@/hooks/use-settings";
import { getErrorMessage } from "@/services";
import type { NotificationPreferences } from "@/types";

const OPTIONS: { key: keyof NotificationPreferences; title: string; description: string }[] = [
  { key: "transactionAlerts", title: "Transaction alerts", description: "Every deposit, transfer in and transfer out." },
  { key: "loginAlerts", title: "New sign-in alerts", description: "When your account is accessed from a new device." },
  { key: "monthlyStatements", title: "Monthly statements", description: "A summary of your wallet at the start of each month." },
  { key: "productUpdates", title: "Product updates", description: "New features and occasional tips." },
];

export function NotificationSettings() {
  const { data: prefs } = useSuspenseQuery(notificationPreferencesQuery());
  const update = useUpdateNotificationPreferences();

  return (
    <ul className="divide-y">
      {OPTIONS.map((option) => (
        <li key={option.key} className="flex items-center justify-between gap-6 py-4">
          <label htmlFor={option.key} className="cursor-pointer">
            <span className="block text-sm font-semibold">{option.title}</span>
            <span className="block text-xs text-muted-foreground">{option.description}</span>
          </label>
          <Switch
            id={option.key}
            checked={prefs[option.key]}
            disabled={update.isPending}
            onCheckedChange={(checked) =>
              update.mutate(
                { ...prefs, [option.key]: checked },
                {
                  onSuccess: () => toast.success("Preferences saved"),
                  onError: (error) => toast.error(getErrorMessage(error)),
                },
              )
            }
          />
        </li>
      ))}
    </ul>
  );
}
