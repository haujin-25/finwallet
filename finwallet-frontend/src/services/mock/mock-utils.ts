import { ApiError } from "../api/api-error";

const IS_TEST = import.meta.env.MODE === "test";

/** Simulated network latency so loading states are visible in the MVP. */
export function delay(min = 280, max = 650): Promise<void> {
  if (IS_TEST) return Promise.resolve();
  const ms = Math.round(min + Math.random() * (max - min));
  return new Promise((resolve) => setTimeout(resolve, ms));
}

const ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

export function randomCode(length: number, random: () => number = Math.random): string {
  let out = "";
  for (let i = 0; i < length; i++) out += ALPHABET[Math.floor(random() * ALPHABET.length)];
  return out;
}

export function generateId(prefix: string, random: () => number = Math.random): string {
  return `${prefix}_${randomCode(16, random).toLowerCase()}`;
}

/** e.g. TRF-20261114-7GH2KQ */
export function generateReference(prefix: "TRF" | "DEP", date: Date, random: () => number = Math.random): string {
  const ymd = `${date.getFullYear()}${String(date.getMonth() + 1).padStart(2, "0")}${String(date.getDate()).padStart(2, "0")}`;
  return `${prefix}-${ymd}-${randomCode(6, random)}`;
}

/** Deterministic PRNG so the seeded demo history is stable between resets. */
export function seededRandom(seed: number): () => number {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/**
 * Mock-only password digest (FNV-1a). Not a security measure: the real backend owns
 * password hashing. It only avoids keeping typed demo passwords in plain text.
 */
export function mockPasswordDigest(password: string): string {
  let hash = 0x811c9dc5;
  const input = `finwallet-mock:${password}`;
  for (let i = 0; i < input.length; i++) {
    hash ^= input.charCodeAt(i);
    hash = Math.imul(hash, 0x01000193) >>> 0;
  }
  return hash.toString(16).padStart(8, "0");
}

export const mockError = {
  unauthorized: () => new ApiError(401, "Your session has expired. Please sign in again.", "UNAUTHORIZED"),
  notFound: (message: string) => new ApiError(404, message, "NOT_FOUND"),
  conflict: (message: string) => new ApiError(409, message, "CONFLICT"),
  unprocessable: (message: string, code = "VALIDATION_FAILED") => new ApiError(422, message, code),
};
