import type { DepositRequest, Payment } from "@/types";
import { PAYMENT_METHOD_LABEL } from "@/utils/labels";
import { getDb, saveDb, type MockDb } from "./mock-db";
import { requireUser, requireWallet } from "./mock-ledger";
import { DEPOSIT_MAX, DEPOSIT_MIN, decideDepositOutcome } from "./mock-rules";
import { delay, generateId, generateReference, mockError } from "./mock-utils";

function stripPayment(record: MockDb["payments"][number]): Payment {
  const { walletId: _w, idempotencyKey: _k, ...payment } = record;
  return payment;
}

export const mockPayments = {
  async deposit(req: DepositRequest): Promise<Payment> {
    await delay(800, 1300);
    const db = getDb();
    const wallet = requireWallet(db, requireUser(db).id);

    const existing = db.payments.find((p) => p.idempotencyKey === req.idempotencyKey && p.walletId === wallet.id);
    if (existing) return stripPayment(existing);

    if (!Number.isInteger(req.amount)) throw mockError.unprocessable("Amount must be in whole sen");
    if (req.amount < DEPOSIT_MIN) throw mockError.unprocessable("The minimum deposit is RM 10.00");
    if (req.amount > DEPOSIT_MAX) throw mockError.unprocessable("The maximum per deposit is RM 30,000.00");
    if (wallet.status !== "ACTIVE") throw mockError.unprocessable("Your wallet isn't active", "WALLET_INACTIVE");

    const outcome = decideDepositOutcome(req);
    const now = new Date();
    const reference = generateReference("DEP", now);
    const transactionId = generateId("txn");

    db.transactions.push({
      id: transactionId,
      walletId: wallet.id,
      reference,
      type: "DEPOSIT",
      status: outcome.status,
      direction: "CREDIT",
      amount: req.amount,
      fee: 0,
      totalAmount: req.amount,
      currency: "MYR",
      counterparty: { name: PAYMENT_METHOD_LABEL[req.method], identifier: "Simulated deposit" },
      description: "Wallet top-up",
      paymentMethod: req.method,
      failureReason: outcome.failureReason,
      createdAt: now.toISOString(),
      completedAt: outcome.status === "COMPLETED" ? now.toISOString() : null,
      simulated: true,
    });

    const payment = {
      id: generateId("pay"),
      reference,
      amount: req.amount,
      fee: 0,
      currency: "MYR" as const,
      method: req.method,
      status: outcome.status,
      failureReason: outcome.failureReason,
      transactionId,
      createdAt: now.toISOString(),
      simulated: true,
      walletId: wallet.id,
      idempotencyKey: req.idempotencyKey,
    };
    db.payments.push(payment);
    saveDb(db);
    return stripPayment(payment);
  },

  async get(id: string): Promise<Payment> {
    await delay();
    const db = getDb();
    const wallet = requireWallet(db, requireUser(db).id);
    const record = db.payments.find((p) => p.id === id && p.walletId === wallet.id);
    if (!record) throw mockError.notFound("Payment not found");
    return stripPayment(record);
  },
};
