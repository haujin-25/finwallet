import { getCookie, setResponseHeader } from "@tanstack/react-start/server";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { sendTransfer } from "./transfer.server";

vi.mock("@tanstack/react-start/server", () => ({
  getCookie: vi.fn(),
  setResponseHeader: vi.fn(),
}));

const fetchSpring = vi.fn<typeof fetch>();
const accountEmail = "sender@example.com";
const token = `header.${btoa(JSON.stringify({ sub: accountEmail }))}.signature`;
const request = {
  recipientEmail: "recipient@example.com",
  amount: 1050,
  description: null,
  idempotencyKey: "intent-1",
};
const receipt = {
  transferReference: "TRF-20261118-ABC12345",
  senderWalletId: 1,
  recipientWalletId: 2,
  amount: 1050,
  currency: "MYR",
  status: "COMPLETED",
  description: null,
  createdAt: "2026-11-18T12:00:00.123456Z",
};

beforeEach(() => {
  vi.stubEnv("FINWALLET_API_BASE_URL", "http://spring.test/api");
  vi.stubGlobal("fetch", fetchSpring);
  vi.mocked(getCookie).mockReturnValue(token);
});

afterEach(() => {
  vi.resetAllMocks();
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

describe("Spring transfer submission", () => {
  it("posts the precise contract and returns a projected receipt", async () => {
    fetchSpring.mockResolvedValueOnce(
      Response.json({ ...receipt, accessToken: token }, { status: 201 }),
    );
    expect(
      await sendTransfer({
        accountEmail,
        senderUserId: 999,
        request: { ...request, senderWalletId: 999, accessToken: "override" },
      }),
    ).toEqual({ outcome: "confirmed", receipt });
    expect(getCookie).toHaveBeenCalledWith("finwallet_session");
    expect(setResponseHeader).toHaveBeenCalledWith("Cache-Control", "no-store");
    expect(fetchSpring).toHaveBeenCalledWith(
      "http://spring.test/api/transfers",
      expect.objectContaining({
        method: "POST",
        headers: {
          Accept: "application/json, text/plain",
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify(request),
      }),
    );
  });

  it("blocks a stale browser tab whose account no longer matches the session", async () => {
    expect(await sendTransfer({ accountEmail: "other@example.com", request })).toMatchObject({
      outcome: "rejected",
      status: 401,
    });
    expect(fetchSpring).not.toHaveBeenCalled();
  });

  it("rejects a zero amount before sending", async () => {
    expect(await sendTransfer({ accountEmail, request: { ...request, amount: 0 } })).toMatchObject({
      outcome: "rejected",
      status: 400,
    });
    expect(fetchSpring).not.toHaveBeenCalled();
  });

  it.each([
    [400, "Cannot transfer money to your own wallet", "another FinWallet user"],
    [400, "amount: Amount must be positive", "Check the recipient"],
    [401, "Authentication required", "Sign in again"],
    [403, "Access denied", "permission"],
    [404, "No user found with email: recipient@example.com", "Check the email"],
    [409, "Sender wallet is not active", "Both wallets must be active"],
    [409, "Recipient wallet is not active", "Both wallets must be active"],
    [422, "Insufficient funds in sender wallet", "Insufficient available balance"],
  ])("maps Spring's %i rejection", async (status, body, expected) => {
    fetchSpring.mockResolvedValueOnce(new Response(body, { status }));
    const result = await sendTransfer({ accountEmail, request });
    expect(result).toMatchObject({ outcome: "rejected", status });
    expect(JSON.stringify(result)).toContain(expected);
  });

  it("keeps a mismatched idempotency key unresolved", async () => {
    fetchSpring.mockResolvedValueOnce(
      new Response("Idempotency key already used for a different transfer", { status: 409 }),
    );
    expect(await sendTransfer({ accountEmail, request })).toMatchObject({
      outcome: "unknown",
      status: 409,
      message: expect.stringContaining("linked to different details"),
    });
  });

  it("keeps the outcome unknown on connection loss, server errors and an invalid receipt", async () => {
    fetchSpring.mockRejectedValueOnce(new Error("socket closed"));
    fetchSpring.mockResolvedValueOnce(
      new Response("Unable to complete the request", { status: 500 }),
    );
    fetchSpring.mockResolvedValueOnce(Response.json({ ...receipt, amount: 1 }, { status: 201 }));
    for (const status of [0, 500, 502]) {
      expect(await sendTransfer({ accountEmail, request })).toMatchObject({
        outcome: "unknown",
        status,
      });
    }
  });
});
