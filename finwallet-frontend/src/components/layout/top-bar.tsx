import { Link, useNavigate } from "@tanstack/react-router";
import { LogOut, Search, Settings, User as UserIcon } from "lucide-react";
import { useState, type FormEvent } from "react";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Input } from "@/components/ui/input";
import { SidebarTrigger } from "@/components/ui/sidebar";
import { DEMO_MODE } from "@/config/demo";
import { useCurrentUser, useLogout } from "@/hooks/use-auth";
import { initialsOf } from "@/utils/labels";
import { NotificationsMenu } from "./notifications-menu";

export function TopBar() {
  const user = useCurrentUser();
  const logout = useLogout();
  const navigate = useNavigate();
  const [query, setQuery] = useState("");

  const onSearch = (event: FormEvent) => {
    event.preventDefault();
    navigate({ to: "/transactions", search: { q: query.trim() || undefined } });
  };

  return (
    <header className="sticky top-0 z-20 flex h-16 items-center gap-3 border-b bg-card/85 px-4 backdrop-blur sm:px-6">
      <SidebarTrigger className="-ml-1 size-9 rounded-lg" />

      {DEMO_MODE && <form onSubmit={onSearch} className="relative hidden max-w-md flex-1 md:block" role="search">
        <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search transactions, references, people…"
          className="h-10 rounded-xl border-transparent bg-muted pl-9 shadow-none focus-visible:border-input focus-visible:bg-card"
          aria-label="Search transactions"
        />
      </form>}

      <div className="ml-auto flex items-center gap-1">
        {DEMO_MODE && <Button asChild variant="ghost" size="icon" className="rounded-full md:hidden" aria-label="Search">
          <Link to="/transactions">
            <Search className="size-[18px]" />
          </Link>
        </Button>}
        {DEMO_MODE && <NotificationsMenu />}

        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <button
              type="button"
              className="ml-1 flex items-center gap-2.5 rounded-full py-1 pl-1 pr-1 transition-colors hover:bg-muted sm:pr-3"
            >
              <Avatar className="size-8">
                <AvatarFallback className="bg-primary text-xs font-bold text-primary-foreground">
                  {initialsOf(user.fullName)}
                </AvatarFallback>
              </Avatar>
              <span className="hidden text-left sm:block">
                <span className="block text-sm font-semibold leading-tight text-foreground">{user.fullName}</span>
                {"userCode" in user && (
                  <span className="block font-mono text-[11px] leading-tight text-muted-foreground">
                    {user.userCode}
                  </span>
                )}
              </span>
            </button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="w-60 rounded-xl">
            <DropdownMenuLabel className="font-normal">
              <p className="text-sm font-semibold">{user.fullName}</p>
              <p className="truncate text-xs text-muted-foreground">{user.email}</p>
            </DropdownMenuLabel>
            <DropdownMenuSeparator />
            {DEMO_MODE && <>
            <DropdownMenuItem asChild>
              <Link to="/settings">
                <UserIcon className="size-4" /> Profile
              </Link>
            </DropdownMenuItem>
            <DropdownMenuItem asChild>
              <Link to="/settings">
                <Settings className="size-4" /> Settings
              </Link>
            </DropdownMenuItem>
            <DropdownMenuSeparator />
            </>}
            <DropdownMenuItem onSelect={logout} className="text-destructive focus:text-destructive">
              <LogOut className="size-4" /> Log out
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>

        <Button variant="ghost" size="sm" onClick={logout} className="hidden text-muted-foreground lg:inline-flex">
          <LogOut /> Log out
        </Button>
      </div>
    </header>
  );
}
