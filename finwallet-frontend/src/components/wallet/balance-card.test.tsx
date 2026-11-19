import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { BalanceCard } from "./balance-card";

describe("Live wallet balance card", () => {
  it.each([
    [0, "RM 0.00"],
    [100, "RM 1.00"],
    [1050, "RM 10.50"],
    [15050, "RM 150.50"],
  ])("displays %i available sen as %s", (availableBalance, formatted) => {
    render(
      <BalanceCard
        wallet={{
          walletId: 1234,
          currency: "MYR",
          status: "SUSPENDED",
          balance: availableBalance + 500,
          reservedBalance: 500,
          availableBalance,
        }}
        showActions={false}
      />,
    );
    expect(screen.getByText(formatted)).toBeInTheDocument();
    expect(screen.getByText("MYR · Suspended")).toBeInTheDocument();
    expect(screen.getByText("1234")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /deposit|transfer/i })).not.toBeInTheDocument();
  });
});
