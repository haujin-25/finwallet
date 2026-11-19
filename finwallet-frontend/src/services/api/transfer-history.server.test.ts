import { getCookie, setResponseHeader } from "@tanstack/react-start/server";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { readTransferHistory } from "./transfer-history.server";

vi.mock("@tanstack/react-start/server", () => ({
  getCookie: vi.fn(),
  setResponseHeader: vi.fn(),
}));

const fetchSpring = vi.fn<typeof fetch>();
const payload = {
  items: [
    {
      transferReference: "TRF-1",
      direction: "INCOMING",
      amount: 1050,
      currency: "MYR",
      status: "COMPLETED",
      description: null,
      counterpartyName: "Aisha",
      createdAt: "2026-11-18T10:00:00.123456+08:00",
      operationType: "TRANSFER",
    },
  ],
  page: 1,
  size: 10,
  totalElements: 11,
  totalPages: 2,
  hasNext: false,
};

beforeEach(() => {
  vi.stubEnv("FINWALLET_API_BASE_URL", "http://spring.test/api");
  vi.stubGlobal("fetch", fetchSpring);
  vi.mocked(getCookie).mockReturnValue("private.jwt.value");
});

afterEach(() => {
  vi.resetAllMocks();
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

describe("Spring transfer history", () => {
  it("forwards only pagination and the session cookie credential and projects the response", async () => {
    fetchSpring.mockResolvedValueOnce(
      Response.json({
        ...payload,
        items: [{ ...payload.items[0], accessToken: "private.jwt.value" }],
      }),
    );

    expect(await readTransferHistory({ page: 1, size: 10, userId: 99, walletId: 99 })).toEqual({
      ok: true,
      data: payload,
    });
    expect(getCookie).toHaveBeenCalledWith("finwallet_session");
    expect(setResponseHeader).toHaveBeenCalledWith("Cache-Control", "no-store");
    expect(fetchSpring).toHaveBeenCalledExactlyOnceWith(
      "http://spring.test/api/transfers/me?page=1&size=10",
      expect.objectContaining({
        method: "GET",
        headers: expect.objectContaining({ Authorization: "Bearer private.jwt.value" }),
      }),
    );
  });

  it.each([
    { page: -1, size: 20 },
    { page: 0, size: 101 },
    { page: 214748365, size: 10 },
  ])("rejects invalid pagination without calling Spring", async (input) => {
    expect(await readTransferHistory(input)).toMatchObject({ ok: false, status: 400 });
    expect(fetchSpring).not.toHaveBeenCalled();
  });

  it("requires an authenticated session", async () => {
    vi.mocked(getCookie).mockReturnValue(undefined);
    expect(await readTransferHistory({ page: 0, size: 10 })).toMatchObject({
      ok: false,
      status: 401,
    });
    expect(fetchSpring).not.toHaveBeenCalled();
  });

  it.each([400, 401, 403, 500])(
    "handles HTTP %s without exposing upstream errors",
    async (status) => {
      fetchSpring.mockResolvedValueOnce(
        Response.json({ message: "private exception" }, { status }),
      );
      const result = await readTransferHistory({ page: 0, size: 10 });
      expect(result).toMatchObject({ ok: false, status });
      expect(JSON.stringify(result)).not.toContain("private exception");
    },
  );

  it("distinguishes network and malformed response errors", async () => {
    fetchSpring.mockRejectedValueOnce(new Error("connection refused"));
    fetchSpring.mockResolvedValueOnce(Response.json({ items: [] }));
    expect(await readTransferHistory({ page: 0, size: 10 })).toMatchObject({
      ok: false,
      status: 0,
    });
    expect(await readTransferHistory({ page: 0, size: 10 })).toMatchObject({
      ok: false,
      status: 502,
    });
  });
});
