import { describe, expect, it } from "vitest";
import { formatMoney, parseAmountToMinor, parseTransferAmount } from "./money";

describe("parseAmountToMinor", () => {
  it("turns ringgit text into whole sen without float errors", () => {
    expect(parseAmountToMinor("12.34")).toBe(1234);
    expect(parseAmountToMinor("0.10")).toBe(10);
    expect(parseAmountToMinor("19.99")).toBe(1999);
    expect(parseAmountToMinor("1,250.5")).toBe(125050);
    expect(parseAmountToMinor("100")).toBe(10000);
  });

  it("rejects zero, negatives, more than 2 decimals and junk", () => {
    expect(parseAmountToMinor("0")).toBeNull();
    expect(parseAmountToMinor("-5")).toBeNull();
    expect(parseAmountToMinor("1.234")).toBeNull();
    expect(parseAmountToMinor("abc")).toBeNull();
  });
});

describe("formatMoney", () => {
  it("shows sen as MYR with grouping and two decimals", () => {
    expect(formatMoney(123456)).toBe("RM 1,234.56");
    expect(formatMoney(5)).toBe("RM 0.05");
    expect(formatMoney(2500, "MYR", { sign: "credit" })).toBe("+RM 25.00");
    expect(formatMoney(2500, "MYR", { sign: "debit" })).toBe("−RM 25.00");
  });
});

describe("parseTransferAmount", () => {
  it.each([
    ["0.01", 1],
    ["1", 100],
    ["10.50", 1050],
    ["1,234.56", 123456],
    ["90071992547409.91", Number.MAX_SAFE_INTEGER],
  ])("converts %s exactly to integer sen", (input, expected) => {
    expect(parseTransferAmount(input)).toBe(expected);
  });

  it.each(["", "0", "0.00", "-1", "10.001", "1e3", "1,2", "1.2.3", "90071992547409.92"])(
    "rejects invalid or unrepresentable input %s",
    (input) => expect(parseTransferAmount(input)).toBeNull(),
  );
});
