import { Outlet, createFileRoute, redirect } from "@tanstack/react-router";
import { Loader2 } from "lucide-react";
import { AppSidebar } from "@/components/layout/app-sidebar";
import { TopBar } from "@/components/layout/top-bar";
import { SidebarInset, SidebarProvider } from "@/components/ui/sidebar";
import { currentUserQuery } from "@/hooks/queries";
import { DEMO_MODE } from "@/config/demo";
import { RouteError } from "@/components/common/route-boundaries";

export const Route = createFileRoute("/_authenticated")({
  // Keep protected page loaders client-side while the remaining features migrate.
  ssr: false,
  beforeLoad: async ({ context, location }) => {
    const user = await context.queryClient.fetchQuery({ ...currentUserQuery(), staleTime: 0 });
    if (!user) {
      context.queryClient.clear();
      throw redirect({ to: "/login", search: { redirect: location.href } });
    }
    if (
      !DEMO_MODE &&
      !["/overview", "/transfer", "/transactions"].includes(location.pathname.replace(/\/$/, ""))
    ) {
      throw redirect({ to: "/overview", replace: true });
    }
    return { user };
  },
  pendingComponent: () => (
    <div className="flex min-h-screen items-center justify-center">
      <Loader2 className="size-6 animate-spin text-muted-foreground" />
    </div>
  ),
  errorComponent: RouteError,
  component: AppLayout,
});

function AppLayout() {
  return (
    <SidebarProvider>
      <div className="flex min-h-screen w-full">
        <AppSidebar />
        <SidebarInset className="min-w-0 bg-background">
          <TopBar />
          <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-6 sm:px-6 lg:px-8 lg:py-8">
            <Outlet />
          </main>
        </SidebarInset>
      </div>
    </SidebarProvider>
  );
}
