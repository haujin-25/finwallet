import { beforeEach, describe, expect, it } from "vitest";
import { tokenStorage } from "../api/token-storage";
import { mockAuth } from "./mock-auth";
import { resetDb } from "./mock-db";
import { mockPayments } from "./mock-payments";
import { calculateTransferFee } from "./mock-rules";
import { mockTransfers } from "./mock-transfers";
import { mockWallet } from "./mock-wallet";

async function signInFreshUser() {
  const res = await mockAuth.register({ fullName: "Test User", email: `t${Date.now()}${Math.random()}@x.my`, password: "Passw0rd!" });
  tokenStorage.setTokens(res.tokens);
}

async function topUp(amount: number) {
  await mockPayments.deposit({ amount, method: "ONLINE_BANKING", idempotencyKey: `dep-${amount}-${Math.random()}` });
}

beforeEach(() => {
  window.localStorage.clear();
  resetDb();
});

describe("transfer fee", () => {
  it("is free up to RM 500 and RM 1.00 above", () => {
    expect(calculateTransferFee(50_000)).toBe(0);
    expect(calculateTransferFee(50_001)).toBe(100);
  });
});

describe("transfers", () => {
  it("completed transfer deducts amount plus fee from both balances", async () => {
    await signInFreshUser();
    await topUp(200_000);
    const t = await mockTransfers.create({ recipient: "aisyah.rahman@finwallet.my", amount: 60_000, idempotencyKey: "a" });
    expect(t.status).toBe("COMPLETED");
    expect(t.totalAmount).toBe(60_100);
    const w = await mockWallet.getMyWallet();
    expect(w.balance).toBe(139_900);
    expect(w.availableBalance).toBe(139_900);
  });

  it("transfer above available balance fails and leaves balance unchanged", async () => {
    await signInFreshUser();
    await topUp(10_000);
    const t = await mockTransfers.create({ recipient: "daniel.lim@finwallet.my", amount: 20_000, idempotencyKey: "b" });
    expect(t.status).toBe("FAILED");
    const w = await mockWallet.getMyWallet();
    expect(w.balance).toBe(10_000);
    expect(w.availableBalance).toBe(10_000);
  });

  it("RM 10,000+ transfer is pending: held from available, ledger balance unchanged", async () => {
    await signInFreshUser();
    await topUp(3_000_000);
    const t = await mockTransfers.create({ recipient: "FW-USR-1004", amount: 1_000_000, idempotencyKey: "c" });
    expect(t.status).toBe("PENDING");
    const w = await mockWallet.getMyWallet();
    expect(w.balance).toBe(3_000_000);
    expect(w.availableBalance).toBe(1_999_900);
  });

  it("retrying with the same idempotency key does not send twice", async () => {
    await signInFreshUser();
    await topUp(100_000);
    const req = { recipient: "priya.nair@finwallet.my", amount: 1_000, idempotencyKey: "same" };
    const first = await mockTransfers.create(req);
    const second = await mockTransfers.create(req);
    expect(second.id).toBe(first.id);
    expect((await mockWallet.getMyWallet()).balance).toBe(99_000);
  });

  it("rejects unknown recipients", async () => {
    await signInFreshUser();
    await topUp(100_000);
    await expect(mockTransfers.quote({ recipient: "nobody@nowhere.my", amount: 1_000 })).rejects.toMatchObject({ status: 404 });
  });
});

describe("deposits", () => {
  it("bank transfer stays pending and does not add to balance", async () => {
    await signInFreshUser();
    const p = await mockPayments.deposit({ amount: 50_000, method: "BANK_TRANSFER", idempotencyKey: "d" });
    expect(p.status).toBe("PENDING");
    expect((await mockWallet.getMyWallet()).balance).toBe(0);
  });

  it("debit card over RM 20,000 is declined", async () => {
    await signInFreshUser();
    const p = await mockPayments.deposit({ amount: 2_000_001, method: "DEBIT_CARD", idempotencyKey: "e" });
    expect(p.status).toBe("FAILED");
    expect((await mockWallet.getMyWallet()).balance).toBe(0);
  });

  it("enforces RM 10 minimum and RM 30,000 maximum", async () => {
    await signInFreshUser();
    await expect(mockPayments.deposit({ amount: 999, method: "ONLINE_BANKING", idempotencyKey: "f" })).rejects.toMatchObject({ status: 422 });
    await expect(mockPayments.deposit({ amount: 3_000_001, method: "ONLINE_BANKING", idempotencyKey: "g" })).rejects.toMatchObject({ status: 422 });
  });
});

describe("demo account", () => {
  it("signs in and has a positive seeded balance", async () => {
    const res = await mockAuth.login({ email: "demo@finwallet.my", password: "Demo1234!" });
    tokenStorage.setTokens(res.tokens);
    const w = await mockWallet.getMyWallet();
    expect(w.balance).toBeGreaterThan(0);
  });
});
