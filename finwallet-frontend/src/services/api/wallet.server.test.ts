import { getCookie, setResponseHeader } from "@tanstack/react-start/server";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { readMyWallet } from "./wallet.server";

vi.mock("@tanstack/react-start/server", () => ({
  getCookie: vi.fn(),
  setResponseHeader: vi.fn(),
}));

const fetchSpring = vi.fn<typeof fetch>();
const wallet = {
  walletId: 1,
  currency: "MYR",
  balance: 15050,
  status: "FROZEN",
  reservedBalance: 14000,
  availableBalance: 1050,
};

beforeEach(() => {
  vi.stubEnv("FINWALLET_API_BASE_URL", "http://spring.test/api");
  vi.stubGlobal("fetch", fetchSpring);
  vi.mocked(getCookie).mockReturnValue("account-a.jwt");
});

afterEach(() => {
  vi.resetAllMocks();
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

describe("Spring wallet retrieval", () => {
  it("requests the same endpoint for both sessions and returns only wallet fields", async () => {
    const secondWallet = {
      ...wallet,
      walletId: 2,
      balance: 0,
      reservedBalance: 0,
      availableBalance: 0,
    };
    fetchSpring.mockImplementation(async (_url, options) => {
      const bearer = new Headers(options?.headers).get("Authorization");
      const responseWallet = bearer === "Bearer account-a.jwt" ? wallet : secondWallet;
      return Response.json({ ...responseWallet, accessToken: bearer, userId: 99 });
    });

    expect(await readMyWallet()).toEqual({ ok: true, data: wallet });
    vi.mocked(getCookie).mockReturnValue("account-b.jwt");
    expect(await readMyWallet()).toEqual({ ok: true, data: secondWallet });

    expect(getCookie).toHaveBeenCalledWith("finwallet_session");
    expect(setResponseHeader).toHaveBeenCalledWith("Cache-Control", "no-store");
    for (const [index, token] of ["account-a.jwt", "account-b.jwt"].entries()) {
      expect(fetchSpring).toHaveBeenNthCalledWith(
        index + 1,
        "http://spring.test/api/wallets/me",
        expect.objectContaining({
          method: "GET",
          headers: expect.objectContaining({ Authorization: `Bearer ${token}` }),
        }),
      );
    }
  });

  it("returns 401 without calling Spring when the session cookie is absent", async () => {
    vi.mocked(getCookie).mockReturnValue(undefined);
    expect(await readMyWallet()).toMatchObject({ ok: false, status: 401 });
    expect(fetchSpring).not.toHaveBeenCalled();
  });

  it.each([401, 403, 404, 500, 503])(
    "handles Spring's %i without exposing upstream data",
    async (status) => {
      fetchSpring.mockResolvedValueOnce(Response.json({ message: "account-a.jwt" }, { status }));
      const result = await readMyWallet();
      expect(result).toMatchObject({ ok: false, status });
      expect(JSON.stringify(result)).not.toContain("account-a.jwt");
    },
  );

  it("reports network failures", async () => {
    fetchSpring.mockRejectedValueOnce(new TypeError("network unavailable"));
    expect(await readMyWallet()).toMatchObject({ ok: false, status: 0 });
  });

  it("rejects a balance outside JavaScript's safe integer range", async () => {
    fetchSpring.mockResolvedValueOnce(Response.json({ ...wallet, balance: 2 ** 53 }));
    expect(await readMyWallet()).toMatchObject({ ok: false, status: 502 });
  });
});
