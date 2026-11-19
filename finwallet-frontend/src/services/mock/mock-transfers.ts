import type { CreateTransferRequest, Paginated, Transfer, TransferQuote, TransferQuoteRequest } from "@/types";
import { getDb, saveDb, type MockDb, type MockUserRecord } from "./mock-db";
import { computeBalances, requireUser, requireWallet, walletTransactions } from "./mock-ledger";
import {
  TRANSFER_MAX,
  TRANSFER_MIN,
  calculateTransferFee,
  decideTransferOutcome,
} from "./mock-rules";
import { delay, generateId, generateReference, mockError } from "./mock-utils";

function resolveRecipient(db: MockDb, sender: MockUserRecord, identifier: string): MockUserRecord {
  const needle = identifier.trim().toLowerCase();
  const recipient = db.users.find((u) => u.email.toLowerCase() === needle || u.userCode.toLowerCase() === needle);
  if (!recipient) throw mockError.notFound("We couldn't find a FinWallet user with that email or user ID");
  if (recipient.id === sender.id) throw mockError.unprocessable("You can't send money to your own wallet");
  return recipient;
}

function validateAmount(amount: number) {
  if (!Number.isInteger(amount)) throw mockError.unprocessable("Amount must be in whole sen");
  if (amount < TRANSFER_MIN) throw mockError.unprocessable("The minimum transfer is RM 1.00");
  if (amount > TRANSFER_MAX) throw mockError.unprocessable("The maximum per transfer is RM 50,000.00");
}

function stripTransfer(record: MockDb["transfers"][number]): Transfer {
  const { senderWalletId: _w, idempotencyKey: _k, ...transfer } = record;
  return transfer;
}

export const mockTransfers = {
  async quote(req: TransferQuoteRequest): Promise<TransferQuote> {
    await delay();
    const db = getDb();
    const sender = requireUser(db);
    validateAmount(req.amount);
    const recipient = resolveRecipient(db, sender, req.recipient);
    const fee = calculateTransferFee(req.amount);
    return {
      sender: { name: sender.fullName, identifier: sender.email },
      recipient: { name: recipient.fullName, identifier: recipient.email },
      amount: req.amount,
      fee,
      totalAmount: req.amount + fee,
      currency: "MYR",
      expiresAt: new Date(Date.now() + 10 * 60 * 1000).toISOString(),
    };
  },

  async create(req: CreateTransferRequest): Promise<Transfer> {
    await delay(700, 1200);
    const db = getDb();
    const sender = requireUser(db);
    const senderWallet = requireWallet(db, sender.id);

    const existing = db.transfers.find((t) => t.idempotencyKey === req.idempotencyKey && t.senderWalletId === senderWallet.id);
    if (existing) return stripTransfer(existing);

    validateAmount(req.amount);
    if (senderWallet.status !== "ACTIVE") throw mockError.unprocessable("Your wallet isn't active", "WALLET_INACTIVE");
    const recipient = resolveRecipient(db, sender, req.recipient);
    const recipientWallet = requireWallet(db, recipient.id);

    const fee = calculateTransferFee(req.amount);
    const totalAmount = req.amount + fee;
    const { availableBalance } = computeBalances(walletTransactions(db, senderWallet.id));
    const outcome = decideTransferOutcome({ amount: req.amount, totalAmount, availableBalance });

    const now = new Date();
    const reference = generateReference("TRF", now);
    const description = req.description?.trim() || null;
    const senderParty = { name: sender.fullName, identifier: sender.email };
    const recipientParty = { name: recipient.fullName, identifier: recipient.email };
    const completedAt = outcome.status === "COMPLETED" ? now.toISOString() : null;
    const senderTxId = generateId("txn");

    db.transactions.push({
      id: senderTxId,
      walletId: senderWallet.id,
      reference,
      type: "TRANSFER_OUT",
      status: outcome.status,
      direction: "DEBIT",
      amount: req.amount,
      fee,
      totalAmount,
      currency: "MYR",
      counterparty: recipientParty,
      description,
      paymentMethod: null,
      failureReason: outcome.failureReason,
      createdAt: now.toISOString(),
      completedAt,
      simulated: true,
    });

    if (outcome.status !== "FAILED") {
      db.transactions.push({
        id: generateId("txn"),
        walletId: recipientWallet.id,
        reference,
        type: "TRANSFER_IN",
        status: outcome.status,
        direction: "CREDIT",
        amount: req.amount,
        fee: 0,
        totalAmount: req.amount,
        currency: "MYR",
        counterparty: senderParty,
        description,
        paymentMethod: null,
        failureReason: null,
        createdAt: now.toISOString(),
        completedAt,
        simulated: true,
      });
    }

    const transfer = {
      id: generateId("trf"),
      reference,
      status: outcome.status,
      amount: req.amount,
      fee,
      totalAmount,
      currency: "MYR" as const,
      sender: senderParty,
      recipient: recipientParty,
      description,
      failureReason: outcome.failureReason,
      transactionId: senderTxId,
      createdAt: now.toISOString(),
      simulated: true,
      senderWalletId: senderWallet.id,
      idempotencyKey: req.idempotencyKey,
    };
    db.transfers.push(transfer);
    saveDb(db);
    return stripTransfer(transfer);
  },

  async list(page = 1, pageSize = 10): Promise<Paginated<Transfer>> {
    await delay();
    const db = getDb();
    const wallet = requireWallet(db, requireUser(db).id);
    const all = db.transfers
      .filter((t) => t.senderWalletId === wallet.id)
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
    return {
      data: all.slice((page - 1) * pageSize, page * pageSize).map(stripTransfer),
      meta: { page, pageSize, total: all.length, totalPages: Math.max(1, Math.ceil(all.length / pageSize)) },
    };
  },

  async get(id: string): Promise<Transfer> {
    await delay();
    const db = getDb();
    const wallet = requireWallet(db, requireUser(db).id);
    const record = db.transfers.find((t) => t.id === id && t.senderWalletId === wallet.id);
    if (!record) throw mockError.notFound("Transfer not found");
    return stripTransfer(record);
  },
};
