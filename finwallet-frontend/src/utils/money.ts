import type { CurrencyCode, MinorUnits } from "@/types";

const CURRENCY_SYMBOL: Record<CurrencyCode, string> = { MYR: "RM" };
const groupFormatter = new Intl.NumberFormat("en-MY", { maximumFractionDigits: 0 });

/**
 * Formats integer minor units (sen) for display without floating-point maths.
 * formatMoney(123456) -> "RM 1,234.56"
 */
export function formatMoney(
  minor: MinorUnits,
  currency: CurrencyCode = "MYR",
  options: { sign?: "credit" | "debit" | "none"; symbol?: boolean } = {},
): string {
  const { sign = "none", symbol = true } = options;
  const negative = minor < 0;
  const abs = Math.abs(Math.trunc(minor));
  const whole = Math.floor(abs / 100);
  const cents = abs % 100;
  const body = `${groupFormatter.format(whole)}.${cents.toString().padStart(2, "0")}`;
  const withSymbol = symbol ? `${CURRENCY_SYMBOL[currency]} ${body}` : body;
  if (sign === "credit") return `+${withSymbol}`;
  if (sign === "debit" || negative) return `−${withSymbol}`;
  return withSymbol;
}

/** Splits minor units into display parts, used by the large balance figure. */
export function splitMoney(minor: MinorUnits) {
  const abs = Math.abs(Math.trunc(minor));
  return {
    whole: groupFormatter.format(Math.floor(abs / 100)),
    cents: (abs % 100).toString().padStart(2, "0"),
  };
}

const AMOUNT_PATTERN = /^\d{1,9}(\.\d{0,2})?$/;

/**
 * Parses a user-typed amount ("12", "12.5", "1,250.00") into integer sen using string
 * arithmetic only. Returns null when the input is not a valid positive amount.
 */
export function parseAmountToMinor(input: string): MinorUnits | null {
  const cleaned = input.trim().replace(/,/g, "");
  if (!AMOUNT_PATTERN.test(cleaned)) return null;
  const [wholePart, fractionPart = ""] = cleaned.split(".");
  const minor = Number(wholePart) * 100 + Number(fractionPart.padEnd(2, "0"));
  return minor > 0 ? minor : null;
}

/** Parse a live transfer amount exactly, then convert only safe integers for JSON. */
export function parseTransferAmount(input: string): MinorUnits | null {
  const value = input.trim();
  if (!/^(?:\d+|\d{1,3}(?:,\d{3})+)(?:\.\d{1,2})?$/.test(value)) return null;
  const [whole = "", fraction = ""] = value.replace(/,/g, "").split(".");
  const sen = `${whole}${fraction.padEnd(2, "0")}`.replace(/^0+/, "");
  if (!sen || sen.length > 16 || BigInt(sen) > BigInt(Number.MAX_SAFE_INTEGER)) return null;
  return Number(sen);
}
