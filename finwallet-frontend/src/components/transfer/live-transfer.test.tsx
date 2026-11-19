import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { transferService, walletService } from "@/services";
import type { SpringTransferResult } from "@/types";
import { LiveTransfer } from "./live-transfer";

vi.mock("@tanstack/react-router", () => ({
  Link: ({ children, to }: { children: ReactNode; to: string }) => <a href={to}>{children}</a>,
}));

const user = { id: "1", email: "sender@example.com" };
const confirmed: SpringTransferResult = {
  outcome: "confirmed",
  receipt: {
    transferReference: "TRF-123",
    senderWalletId: 1,
    recipientWalletId: 2,
    amount: 1050,
    currency: "MYR",
    status: "COMPLETED",
    description: "Lunch",
    createdAt: "2026-11-18T12:00:00Z",
  },
};

afterEach(() => {
  sessionStorage.clear();
  vi.restoreAllMocks();
});

function setupWallet() {
  const data = {
    walletId: 1,
    currency: "MYR" as const,
    balance: 2000,
    availableBalance: 2000,
    reservedBalance: 0,
    status: "ACTIVE" as const,
  };
  return vi
    .spyOn(walletService, "getMyWallet")
    .mockResolvedValueOnce(data)
    .mockResolvedValue({ ...data, balance: 950, availableBalance: 950 });
}

function renderTransfer(queryClient: QueryClient) {
  return render(
    <QueryClientProvider client={queryClient}>
      <LiveTransfer user={user} />
    </QueryClientProvider>,
  );
}

async function reviewTransfer() {
  fireEvent.change(await screen.findByLabelText("Recipient email"), {
    target: { value: "other@example.com" },
  });
  fireEvent.change(screen.getByLabelText("Amount"), { target: { value: "10.50" } });
  fireEvent.change(screen.getByLabelText(/Description/), { target: { value: "Lunch" } });
  fireEvent.click(screen.getByRole("button", { name: "Review transfer" }));
  return screen.findByRole("button", { name: "Confirm and send" });
}

describe("Live transfer workflow", () => {
  it("blocks duplicate clicks and invalidates wallet and history data only after confirmation", async () => {
    const fetchWallet = setupWallet();
    let finish!: (result: SpringTransferResult) => void;
    const send = vi.spyOn(transferService, "send").mockReturnValue(
      new Promise((resolve) => {
        finish = resolve;
      }),
    );
    const queryClient = new QueryClient();
    const historyKey = ["transactions", "transfers", "1", 0, 10];
    queryClient.setQueryData(historyKey, { items: [] });
    renderTransfer(queryClient);
    const confirm = await reviewTransfer();

    fireEvent.click(confirm);
    fireEvent.click(confirm);
    expect(send).toHaveBeenCalledTimes(1);
    expect(screen.getByRole("button", { name: "Sending…" })).toBeDisabled();
    expect(screen.queryByText("Transfer sent")).not.toBeInTheDocument();
    expect(fetchWallet).toHaveBeenCalledTimes(1);

    expect(queryClient.getQueryState(historyKey)?.isInvalidated).toBe(false);
    finish(confirmed);
    expect(await screen.findByText("TRF-123")).toBeInTheDocument();
    await waitFor(() =>
      expect(queryClient.getQueryData(["wallet", "me", "1"])).toMatchObject({
        availableBalance: 950,
      }),
    );
    expect(fetchWallet).toHaveBeenCalledTimes(2);
    expect(queryClient.getQueryState(historyKey)?.isInvalidated).toBe(true);
  });

  it("recovers an uncertain request after remount and retries the identical payload and key", async () => {
    setupWallet();
    const send = vi
      .spyOn(transferService, "send")
      .mockResolvedValueOnce({ outcome: "unknown", status: 0, message: "Connection lost" })
      .mockResolvedValueOnce({ outcome: "rejected", status: 401, message: "Sign in again" })
      .mockResolvedValueOnce(confirmed);
    const first = renderTransfer(new QueryClient());
    fireEvent.click(await reviewTransfer());
    expect(await screen.findByText("Connection lost")).toBeInTheDocument();
    first.unmount();

    const second = renderTransfer(new QueryClient());
    fireEvent.click(await screen.findByRole("button", { name: "Retry same transfer" }));
    expect(await screen.findByText("Sign in again")).toBeInTheDocument();
    second.unmount();

    renderTransfer(new QueryClient());
    fireEvent.click(await screen.findByRole("button", { name: "Retry same transfer" }));
    expect(await screen.findByText("Transfer sent")).toBeInTheDocument();
    expect(send).toHaveBeenCalledTimes(3);
    expect(send.mock.calls[1]).toEqual(send.mock.calls[0]);
    expect(send.mock.calls[2]).toEqual(send.mock.calls[0]);
    expect(send.mock.calls[0]?.[1]).toMatchObject({
      recipientEmail: "other@example.com",
      amount: 1050,
      description: "Lunch",
      idempotencyKey: expect.any(String),
    });
    expect(sessionStorage.length).toBe(0);
  });
});
