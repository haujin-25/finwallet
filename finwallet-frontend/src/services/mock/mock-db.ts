import type { NotificationPreferences, Payment, Transaction, Transfer, User, WalletStatus } from "@/types";
import { generateId, generateReference, mockPasswordDigest, seededRandom } from "./mock-utils";

export interface MockUserRecord extends User {
  /** Empty string means the account cannot sign in (directory-only contact) */
  passwordDigest: string;
  preferences: NotificationPreferences;
}

export interface MockWalletRecord {
  id: string;
  walletNumber: string;
  userId: string;
  currency: "MYR";
  status: WalletStatus;
  createdAt: string;
}

export type MockTransactionRecord = Transaction & { walletId: string };

export interface MockSession {
  accessToken: string;
  refreshToken: string;
  userId: string;
}

export interface MockDb {
  version: 1;
  users: MockUserRecord[];
  wallets: MockWalletRecord[];
  transactions: MockTransactionRecord[];
  transfers: (Transfer & { senderWalletId: string; idempotencyKey: string })[];
  payments: (Payment & { walletId: string; idempotencyKey: string })[];
  sessions: MockSession[];
}

const STORAGE_KEY = "finwallet.mock-db.v1";

export const DEMO_CREDENTIALS = { email: "demo@finwallet.my", password: "Demo1234!" } as const;

const DEFAULT_PREFERENCES: NotificationPreferences = {
  transactionAlerts: true,
  loginAlerts: true,
  monthlyStatements: true,
  productUpdates: false,
};

const CONTACTS = [
  { code: "FW-USR-1002", name: "Aisyah Rahman", email: "aisyah.rahman@finwallet.my" },
  { code: "FW-USR-1003", name: "Daniel Lim", email: "daniel.lim@finwallet.my" },
  { code: "FW-USR-1004", name: "Priya Nair", email: "priya.nair@finwallet.my" },
  { code: "FW-USR-1005", name: "Wong Mei Ling", email: "meiling.wong@finwallet.my" },
  { code: "FW-USR-1006", name: "Haziq Azman", email: "haziq.azman@finwallet.my" },
  { code: "FW-USR-1007", name: "Kavitha Raj", email: "kavitha.raj@finwallet.my" },
];

const OUT_NOTES = [
  "Dinner at Jalan Alor",
  "Rent share",
  "Badminton court booking",
  "Grab ride split",
  "Birthday gift",
  "Utilities split",
  "Movie tickets",
  "Groceries",
  "Concert tickets",
  "Weekend trip to Melaka",
  "Coffee run",
  null,
];
const IN_NOTES = ["Lunch payback", "Thanks for covering!", "Trip refund", "Shared Netflix", "Futsal", null];

export function walletNumberFor(seed: number): string {
  const digits = String(882344102291 + seed * 7919).padStart(12, "0").slice(-12);
  return `FW ${digits.slice(0, 4)} ${digits.slice(4, 8)} ${digits.slice(8)}`;
}

function buildSeed(now = new Date()): MockDb {
  const random = seededRandom(20261114);
  const day = 24 * 60 * 60 * 1000;
  const iso = (offsetDays: number, hour: number, minute: number) => {
    const d = new Date(now.getTime() - offsetDays * day);
    d.setHours(hour, minute, 0, 0);
    return d;
  };

  const demoUser: MockUserRecord = {
    id: "usr_demo",
    userCode: "FW-USR-1001",
    fullName: "Farid Iskandar",
    email: DEMO_CREDENTIALS.email,
    phone: "+60 12-345 6789",
    createdAt: iso(120, 10, 12).toISOString(),
    passwordDigest: mockPasswordDigest(DEMO_CREDENTIALS.password),
    preferences: { ...DEFAULT_PREFERENCES },
  };

  const users: MockUserRecord[] = [demoUser];
  const wallets: MockWalletRecord[] = [
    {
      id: "wal_demo",
      walletNumber: walletNumberFor(0),
      userId: demoUser.id,
      currency: "MYR",
      status: "ACTIVE",
      createdAt: demoUser.createdAt,
    },
  ];

  CONTACTS.forEach((contact, index) => {
    const id = `usr_${contact.code.slice(-4)}`;
    users.push({
      id,
      userCode: contact.code,
      fullName: contact.name,
      email: contact.email,
      phone: null,
      createdAt: iso(200, 9, 0).toISOString(),
      passwordDigest: "",
      preferences: { ...DEFAULT_PREFERENCES },
    });
    wallets.push({
      id: `wal_${contact.code.slice(-4)}`,
      walletNumber: walletNumberFor(index + 1),
      userId: id,
      currency: "MYR",
      status: "ACTIVE",
      createdAt: iso(200, 9, 0).toISOString(),
    });
  });

  const transactions: MockTransactionRecord[] = [];
  let balance = 0;
  const pick = <T>(items: readonly T[]) => items[Math.floor(random() * items.length)] as T;
  const amountBetween = (minRm: number, maxRm: number) =>
    Math.round((minRm + random() * (maxRm - minRm)) * 100 / 50) * 50;

  const push = (tx: Omit<MockTransactionRecord, "id" | "reference" | "currency" | "simulated" | "walletId" | "completedAt">, at: Date) => {
    const prefix = tx.type === "DEPOSIT" ? "DEP" : "TRF";
    const record: MockTransactionRecord = {
      ...tx,
      id: generateId("txn", random),
      reference: generateReference(prefix, at, random),
      currency: "MYR",
      walletId: "wal_demo",
      simulated: true,
      createdAt: at.toISOString(),
      completedAt: tx.status === "COMPLETED" ? new Date(at.getTime() + 4000).toISOString() : null,
    };
    transactions.push(record);
    if (record.status === "COMPLETED") {
      balance += record.direction === "CREDIT" ? record.amount : -record.totalAmount;
    }
  };

  push(
    {
      type: "DEPOSIT",
      status: "COMPLETED",
      direction: "CREDIT",
      amount: 500_000,
      fee: 0,
      totalAmount: 500_000,
      counterparty: { name: "Online Banking", identifier: "Simulated deposit" },
      description: "Initial wallet top-up",
      paymentMethod: "ONLINE_BANKING",
      failureReason: null,
      createdAt: "",
    },
    iso(90, 9, 24),
  );

  for (let offset = 89; offset >= 2; offset--) {
    const count = random() < 0.45 ? 0 : random() < 0.75 ? 1 : 2;
    for (let i = 0; i < count; i++) {
      const at = iso(offset, 8 + Math.floor(random() * 14), Math.floor(random() * 60));
      const roll = random();
      const failed = random() < 0.06;
      if (roll < 0.52) {
        const contact = pick(CONTACTS);
        const amount = amountBetween(12, 420);
        const fee = amount > 50_000 ? 100 : 0;
        if (!failed && amount + fee > balance) continue;
        push(
          {
            type: "TRANSFER_OUT",
            status: failed ? "FAILED" : "COMPLETED",
            direction: "DEBIT",
            amount,
            fee,
            totalAmount: amount + fee,
            counterparty: { name: contact.name, identifier: contact.email },
            description: pick(OUT_NOTES),
            paymentMethod: null,
            failureReason: failed ? "Insufficient available balance" : null,
            createdAt: "",
          },
          at,
        );
      } else if (roll < 0.82) {
        const contact = pick(CONTACTS);
        const amount = amountBetween(20, 350);
        push(
          {
            type: "TRANSFER_IN",
            status: "COMPLETED",
            direction: "CREDIT",
            amount,
            fee: 0,
            totalAmount: amount,
            counterparty: { name: contact.name, identifier: contact.email },
            description: pick(IN_NOTES),
            paymentMethod: null,
            failureReason: null,
            createdAt: "",
          },
          at,
        );
      } else {
        const method = pick(["ONLINE_BANKING", "DEBIT_CARD", "BANK_TRANSFER"] as const);
        const amount = amountBetween(100, 1500);
        push(
          {
            type: "DEPOSIT",
            status: failed && method === "DEBIT_CARD" ? "FAILED" : "COMPLETED",
            direction: "CREDIT",
            amount,
            fee: 0,
            totalAmount: amount,
            counterparty: {
              name: method === "ONLINE_BANKING" ? "Online Banking" : method === "DEBIT_CARD" ? "Debit Card" : "Bank Transfer",
              identifier: "Simulated deposit",
            },
            description: "Wallet top-up",
            paymentMethod: method,
            failureReason: failed && method === "DEBIT_CARD" ? "Declined by simulated card issuer" : null,
            createdAt: "",
          },
          at,
        );
      }
    }
  }

  // A deliberate mix of states for the most recent activity.
  push(
    {
      type: "TRANSFER_IN",
      status: "COMPLETED",
      direction: "CREDIT",
      amount: 18_000,
      fee: 0,
      totalAmount: 18_000,
      counterparty: { name: "Aisyah Rahman", identifier: "aisyah.rahman@finwallet.my" },
      description: "Trip refund",
      paymentMethod: null,
      failureReason: null,
      createdAt: "",
    },
    iso(1, 13, 5),
  );
  push(
    {
      type: "TRANSFER_OUT",
      status: "FAILED",
      direction: "DEBIT",
      amount: 1_250_000,
      fee: 100,
      totalAmount: 1_250_100,
      counterparty: { name: "Daniel Lim", identifier: "daniel.lim@finwallet.my" },
      description: "Car deposit",
      paymentMethod: null,
      failureReason: "Insufficient available balance",
      createdAt: "",
    },
    iso(1, 19, 42),
  );
  push(
    {
      type: "DEPOSIT",
      status: "PENDING",
      direction: "CREDIT",
      amount: 80_000,
      fee: 0,
      totalAmount: 80_000,
      counterparty: { name: "Bank Transfer", identifier: "Simulated deposit" },
      description: "Wallet top-up",
      paymentMethod: "BANK_TRANSFER",
      failureReason: null,
      createdAt: "",
    },
    new Date(now.getTime() - 3 * 60 * 60 * 1000),
  );
  push(
    {
      type: "TRANSFER_OUT",
      status: "COMPLETED",
      direction: "DEBIT",
      amount: 6_450,
      fee: 0,
      totalAmount: 6_450,
      counterparty: { name: "Priya Nair", identifier: "priya.nair@finwallet.my" },
      description: "Nasi lemak breakfast",
      paymentMethod: null,
      failureReason: null,
      createdAt: "",
    },
    new Date(now.getTime() - 42 * 60 * 1000),
  );

  return { version: 1, users, wallets, transactions, transfers: [], payments: [], sessions: [] };
}

let cache: MockDb | null = null;

const hasStorage = () => typeof window !== "undefined" && !!window.localStorage;

export function getDb(): MockDb {
  if (cache) return cache;
  if (hasStorage()) {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (raw) {
      try {
        const parsed = JSON.parse(raw) as MockDb;
        if (parsed.version === 1) {
          cache = parsed;
          return cache;
        }
      } catch {
        // fall through to reseed
      }
    }
  }
  cache = buildSeed();
  saveDb(cache);
  return cache;
}

export function saveDb(db: MockDb) {
  cache = db;
  if (hasStorage()) window.localStorage.setItem(STORAGE_KEY, JSON.stringify(db));
}

/** Wipes simulated data back to the seeded demo state. */
export function resetDb() {
  cache = null;
  if (hasStorage()) window.localStorage.removeItem(STORAGE_KEY);
  return getDb();
}

export { DEFAULT_PREFERENCES };
