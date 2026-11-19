import { QueryClient } from "@tanstack/react-query";
import { createMemoryHistory, createRouter, rootRouteId } from "@tanstack/react-router";
import { afterEach, describe, expect, it, vi } from "vitest";

import { ApiError, authService, transactionService, walletService } from "@/services";
import { routeTree } from "@/routeTree.gen";

vi.mock("@/config/demo", () => ({ DEMO_MODE: false }));

// Match routes without running loaders or rendering: loaders may need a server or
// network the test run lacks, and jsdom never loads the stylesheets React waits on.
describe("App routing", () => {
  it("matches a page for / instead of falling back to not found", () => {
    const router = createRouter({ routeTree, context: { queryClient: new QueryClient() } });

    const matches = router.matchRoutes("/");

    expect(matches.at(-1)?.routeId).not.toBe(rootRouteId);
  });
});

describe("Protected routing", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("redirects an unauthenticated visit to the overview to sign-in", async () => {
    vi.spyOn(authService, "getCurrentUser").mockResolvedValue(null);
    const history = createMemoryHistory({ initialEntries: ["/overview"] });
    const router = createRouter({
      routeTree,
      history,
      context: { queryClient: new QueryClient() },
    });

    await router.load();

    expect(history.location.pathname).toBe("/login");
    expect(history.location.search).toContain("redirect=%2Foverview");
  });

  it("redirects to sign-in when Spring rejects the session during wallet retrieval", async () => {
    vi.spyOn(authService, "getCurrentUser")
      .mockResolvedValueOnce({ id: "1", email: "a@example.com", fullName: "User Alpha" })
      .mockResolvedValue(null);
    vi.spyOn(walletService, "getMyWallet").mockRejectedValue(new ApiError(401, "Session ended"));
    const history = createMemoryHistory({ initialEntries: ["/overview?walletId=2"] });
    const queryClient = new QueryClient();
    const router = createRouter({ routeTree, history, context: { queryClient } });

    await router.load();

    expect(walletService.getMyWallet).toHaveBeenCalledExactlyOnceWith();
    expect(history.location.pathname).toBe("/login");
    expect(queryClient.getQueryData(["wallet", "me", "1"])).toBeUndefined();
  });

  it.each([404, 403, 0, 500])(
    "surfaces wallet error %i without caching a balance",
    async (status) => {
      vi.spyOn(authService, "getCurrentUser").mockResolvedValue({
        id: "1",
        email: "a@example.com",
        fullName: "User Alpha",
      });
      const error = new ApiError(status, "Wallet unavailable");
      vi.spyOn(walletService, "getMyWallet").mockRejectedValue(error);
      const history = createMemoryHistory({ initialEntries: ["/overview"] });
      const queryClient = new QueryClient();
      const router = createRouter({ routeTree, history, context: { queryClient } });

      await router.load();

      expect(history.location.pathname).toBe("/overview");
      expect(router.state.matches.at(-1)?.error).toBe(error);
      expect(queryClient.getQueryData(["wallet", "me", "1"])).toBeUndefined();
    },
  );
});

describe("Transfer history routing", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("loads the requested zero-based Spring page into the signed-in user's cache", async () => {
    vi.spyOn(authService, "getCurrentUser").mockResolvedValue({
      id: "1",
      email: "a@example.com",
      fullName: "User Alpha",
    });
    const data = { items: [], page: 1, size: 10, totalElements: 11, totalPages: 2, hasNext: false };
    const fetchHistory = vi.spyOn(transactionService, "getTransferHistory").mockResolvedValue(data);
    const history = createMemoryHistory({ initialEntries: ["/transactions?page=2&userId=99"] });
    const queryClient = new QueryClient();
    const router = createRouter({ routeTree, history, context: { queryClient } });

    await router.load();

    expect(fetchHistory).toHaveBeenCalledExactlyOnceWith(1, 10);
    expect(queryClient.getQueryData(["transactions", "transfers", "1", 1, 10])).toEqual(data);
  });

  it("redirects to sign-in if Spring rejects a session during history retrieval", async () => {
    vi.spyOn(authService, "getCurrentUser")
      .mockResolvedValueOnce({ id: "1", email: "a@example.com", fullName: "User Alpha" })
      .mockResolvedValue(null);
    vi.spyOn(transactionService, "getTransferHistory").mockRejectedValue(
      new ApiError(401, "Session ended"),
    );
    const history = createMemoryHistory({ initialEntries: ["/transactions?page=2"] });
    const queryClient = new QueryClient();
    const router = createRouter({ routeTree, history, context: { queryClient } });

    await router.load();

    expect(history.location.pathname).toBe("/login");
    expect(history.location.search).toContain("redirect=%2Ftransactions%3Fpage%3D2");
    expect(queryClient.getQueryCache().findAll({ queryKey: ["transactions"] })).toEqual([]);
  });
});
