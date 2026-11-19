import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { TransferHistoryItem, TransferHistoryPage } from "@/types";
import { formatDateTime } from "@/utils/date";
import { TransferHistoryList } from "./transfer-history-list";

const incoming: TransferHistoryItem = {
  transferReference: "TRF-1",
  direction: "INCOMING",
  amount: 1050,
  currency: "MYR",
  status: "COMPLETED",
  description: "Lunch",
  counterpartyName: "Aisha",
  createdAt: "2026-11-18T10:00:00Z",
  operationType: "TRANSFER",
};
const history: TransferHistoryPage = {
  items: [
    incoming,
    {
      ...incoming,
      transferReference: "TRF-2",
      direction: "OUTGOING",
      amount: 10000,
      counterpartyName: null,
      status: "FAILED",
      description: null,
      operationType: "REVERSAL",
      reversalOfReference: "TRF-0",
    },
  ],
  page: 1,
  size: 2,
  totalElements: 6,
  totalPages: 3,
  hasNext: true,
};

describe("Transfer history list", () => {
  it("renders signed MYR amounts, direction, status, counterparty, description and timestamp", () => {
    render(<TransferHistoryList history={history} onPage={vi.fn()} />);
    expect(screen.getByText("+RM 10.50")).toBeInTheDocument();
    expect(screen.getByText("−RM 100.00")).toBeInTheDocument();
    expect(screen.getByText("Incoming transfer · Aisha")).toBeInTheDocument();
    expect(screen.getByText("Outgoing reversal")).toBeInTheDocument();
    expect(screen.getByText("Completed")).toBeInTheDocument();
    expect(screen.getByText("Failed")).toBeInTheDocument();
    expect(screen.getByText("Lunch")).toBeInTheDocument();
    expect(screen.getByText("Reversal of TRF-0")).toBeInTheDocument();
    expect(screen.getAllByText(formatDateTime(incoming.createdAt))[0]).toHaveAttribute(
      "datetime",
      incoming.createdAt,
    );
  });

  it("shows the server page as one-based and navigates to adjacent pages", () => {
    const onPage = vi.fn();
    render(<TransferHistoryList history={history} onPage={onPage} />);
    expect(screen.getByRole("button", { current: "page" })).toHaveTextContent("2");
    fireEvent.click(screen.getByRole("button", { name: "Previous page" }));
    fireEvent.click(screen.getByRole("button", { name: "Next page" }));
    expect(onPage.mock.calls).toEqual([[1], [3]]);
  });

  it("disables Next on the final page", () => {
    render(
      <TransferHistoryList history={{ ...history, page: 2, hasNext: false }} onPage={vi.fn()} />,
    );
    expect(screen.getByRole("button", { name: "Next page" })).toBeDisabled();
  });

  it("shows an empty state without pagination", () => {
    render(<TransferHistoryList history={{ ...history, items: [] }} onPage={vi.fn()} />);
    expect(screen.getByText("No transfers yet")).toBeInTheDocument();
    expect(screen.queryByRole("navigation")).not.toBeInTheDocument();
  });
});
