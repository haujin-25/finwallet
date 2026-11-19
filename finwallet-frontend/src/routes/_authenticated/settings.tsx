import { createFileRoute } from "@tanstack/react-router";
import { Bell, KeyRound, UserRound } from "lucide-react";
import { PageHeader } from "@/components/common/page-header";
import { PageSkeleton } from "@/components/common/page-skeleton";
import { RouteError, RouteNotFound } from "@/components/common/route-boundaries";
import { NotificationSettings } from "@/components/settings/notification-settings";
import { PasswordForm } from "@/components/settings/password-form";
import { ProfileForm } from "@/components/settings/profile-form";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { notificationPreferencesQuery } from "@/hooks/queries";
import { useCurrentUser } from "@/hooks/use-auth";

export const Route = createFileRoute("/_authenticated/settings")({
  head: () => ({
    meta: [
      { title: "Settings — FinWallet" },
      { name: "description", content: "Manage your FinWallet profile, password and notifications." },
      { property: "og:title", content: "Settings — FinWallet" },
      { property: "og:description", content: "Manage your FinWallet profile, password and notifications." },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary" },
    ],
  }),
  loader: async ({ context: { queryClient } }) => {
    await queryClient.ensureQueryData(notificationPreferencesQuery());
  },
  pendingComponent: PageSkeleton,
  errorComponent: RouteError,
  notFoundComponent: () => <RouteNotFound />,
  component: SettingsPage,
});

function Panel({ title, description, children }: { title: string; description: string; children: React.ReactNode }) {
  return (
    <section className="rounded-2xl border bg-card p-6 shadow-card sm:p-8">
      <h2 className="text-base font-bold">{title}</h2>
      <p className="mb-6 text-sm text-muted-foreground">{description}</p>
      {children}
    </section>
  );
}

function SettingsPage() {
  const user = useCurrentUser();
  return (
    <div className="space-y-6 animate-rise">
      <PageHeader title="Settings" description="Your profile, security and notification preferences." />
      <Tabs defaultValue="profile" className="space-y-6">
        <TabsList className="h-11 rounded-xl bg-secondary p-1">
          <TabsTrigger value="profile" className="h-9 gap-2 rounded-lg px-4 font-semibold">
            <UserRound className="size-4" /> Profile
          </TabsTrigger>
          <TabsTrigger value="security" className="h-9 gap-2 rounded-lg px-4 font-semibold">
            <KeyRound className="size-4" /> Password
          </TabsTrigger>
          <TabsTrigger value="notifications" className="h-9 gap-2 rounded-lg px-4 font-semibold">
            <Bell className="size-4" /> Notifications
          </TabsTrigger>
        </TabsList>
        <TabsContent value="profile">
          <Panel title="Personal information" description="How you appear to people you send money to.">
            {"createdAt" in user && <ProfileForm user={user} />}
          </Panel>
        </TabsContent>
        <TabsContent value="security">
          <Panel title="Password" description="Use a password you don't use anywhere else.">
            <PasswordForm />
          </Panel>
        </TabsContent>
        <TabsContent value="notifications">
          <Panel title="Notification preferences" description="Choose what we tell you about. Delivery is a placeholder until email is connected.">
            <NotificationSettings />
          </Panel>
        </TabsContent>
      </Tabs>
    </div>
  );
}
