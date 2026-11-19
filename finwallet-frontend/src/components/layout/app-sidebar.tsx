import { Link, useRouterState } from "@tanstack/react-router";
import {
  ArrowLeftRight,
  FlaskConical,
  LayoutGrid,
  ListOrdered,
  Settings,
  Wallet,
} from "lucide-react";
import { LogoMark, Logo } from "@/components/brand/logo";
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  useSidebar,
} from "@/components/ui/sidebar";

import { DEMO_MODE } from "@/config/demo";

const NAV = [
  { title: "Overview", to: "/overview", icon: LayoutGrid, match: ["/overview"] },
  { title: "My Wallet", to: "/wallet", icon: Wallet, match: ["/wallet", "/deposit"] },
  { title: "Transfer Money", to: "/transfer", icon: ArrowLeftRight, match: ["/transfer"] },
  { title: "Transactions", to: "/transactions", icon: ListOrdered, match: ["/transactions"] },
  { title: "Settings", to: "/settings", icon: Settings, match: ["/settings"] },
] as const;

export function AppSidebar() {
  const { state, setOpenMobile } = useSidebar();
  const collapsed = state === "collapsed";
  const pathname = useRouterState({ select: (s) => s.location.pathname });
  const isActive = (match: readonly string[]) =>
    match.some((p) => pathname === p || pathname.startsWith(`${p}/`));

  return (
    <Sidebar collapsible="icon">
      <SidebarHeader className="h-16 justify-center px-4 group-data-[collapsible=icon]:px-2">
        <Link to="/overview" className="flex items-center" onClick={() => setOpenMobile(false)}>
          {collapsed ? <LogoMark /> : <Logo />}
        </Link>
      </SidebarHeader>

      <SidebarContent className="px-2">
        <SidebarGroup>
          <SidebarGroupLabel className="text-[11px] font-semibold uppercase tracking-wider">
            Menu
          </SidebarGroupLabel>
          <SidebarGroupContent>
            <SidebarMenu className="gap-1">
              {NAV.filter(
                (item) =>
                  DEMO_MODE || ["/overview", "/transfer", "/transactions"].includes(item.to),
              ).map((item) => {
                const active = isActive(item.match);
                return (
                  <SidebarMenuItem key={item.to}>
                    <SidebarMenuButton
                      asChild
                      isActive={active}
                      tooltip={item.title}
                      className="h-10 rounded-lg font-semibold data-[active=true]:bg-primary data-[active=true]:text-primary-foreground data-[active=true]:font-semibold"
                    >
                      <Link to={item.to} onClick={() => setOpenMobile(false)}>
                        <item.icon />
                        <span>{item.title}</span>
                      </Link>
                    </SidebarMenuButton>
                  </SidebarMenuItem>
                );
              })}
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>
      </SidebarContent>

      {DEMO_MODE && (
        <SidebarFooter className="p-3">
          {collapsed ? (
            <span
              className="mx-auto inline-flex size-8 items-center justify-center rounded-lg bg-warning-soft text-warning"
              title="Simulation mode"
            >
              <FlaskConical className="size-4" />
            </span>
          ) : (
            <div className="rounded-xl border border-dashed border-warning/40 bg-warning-soft p-3">
              <p className="flex items-center gap-1.5 text-xs font-bold text-warning">
                <FlaskConical className="size-3.5" /> Simulation mode
              </p>
              <p className="mt-1 text-xs leading-relaxed text-muted-foreground">
                Test data only. No real money or bank connection.
              </p>
            </div>
          )}
        </SidebarFooter>
      )}
    </Sidebar>
  );
}
