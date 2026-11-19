import { createFileRoute, redirect } from "@tanstack/react-router";
import { authService } from "@/services";

export const Route = createFileRoute("/")({
  ssr: false,
  head: () => ({
    meta: [
      { title: "FinWallet — Send and manage ringgit" },
      { name: "description", content: "Sign in to FinWallet to view your balance, send money and track transactions." },
      { property: "og:title", content: "FinWallet — Send and manage ringgit" },
      { property: "og:description", content: "A modern digital wallet for sending and managing ringgit." },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
  beforeLoad: async () => {
    const user = await authService.getCurrentUser().catch(() => null);
    throw redirect({ to: user ? "/overview" : "/login", replace: true });
  },
  component: () => null,
});
