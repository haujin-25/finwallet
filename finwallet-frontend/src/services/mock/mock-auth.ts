import type {
  AuthResponse,
  AuthTokens,
  ChangePasswordRequest,
  ForgotPasswordRequest,
  LoginRequest,
  LogoutRequest,
  NotificationPreferences,
  RefreshTokenRequest,
  RegisterRequest,
  UpdateProfileRequest,
  User,
} from "@/types";
import { ApiError } from "../api/api-error";
import {
  DEFAULT_PREFERENCES,
  getDb,
  saveDb,
  walletNumberFor,
  type MockDb,
} from "./mock-db";
import { requireUser, toPublicUser } from "./mock-ledger";
import {
  delay,
  generateId,
  mockError,
  mockPasswordDigest,
  randomCode,
} from "./mock-utils";

function issueTokens(db: MockDb, userId: string): AuthTokens {
  const tokens: AuthTokens = {
    accessToken: `mock_at_${randomCode(32)}`,
    refreshToken: `mock_rt_${randomCode(32)}`,
    expiresIn: 900,
  };

  db.sessions.push({
    accessToken: tokens.accessToken,
    refreshToken: tokens.refreshToken,
    userId,
  });

  return tokens;
}

export const mockAuth = {
  async register(req: RegisterRequest): Promise<AuthResponse> {
    await delay();
    const db = getDb();

    const email = req.email.trim().toLowerCase();
    if (db.users.some((u) => u.email.toLowerCase() === email)) {
      throw mockError.conflict("An account with this email already exists");
    }

    const sequence = 1000 + db.users.length + 1;

    const now = new Date().toISOString();

    const user = {
      id: generateId("usr"),
      userCode: `FW-USR-${sequence}`,
      fullName: req.fullName.trim(),
      email,
      phone: null,
      createdAt: now,
      passwordDigest: mockPasswordDigest(req.password),
      preferences: { ...DEFAULT_PREFERENCES },
    };

    db.users.push(user);

    db.wallets.push({
      id: generateId("wal"),
      walletNumber: walletNumberFor(sequence),
      userId: user.id,
      currency: "MYR",
      status: "ACTIVE",
      createdAt: now,
    });

    const tokens = issueTokens(db, user.id);

    saveDb(db);

    return { user: toPublicUser(user), tokens };
  },

  async login(req: LoginRequest): Promise<AuthResponse> {
    await delay();
    const db = getDb();
    const email = req.email.trim().toLowerCase();
    const user = db.users.find((u) => u.email.toLowerCase() === email);
    if (
      !user ||
      !user.passwordDigest ||
      user.passwordDigest !== mockPasswordDigest(req.password)
    ) {
      throw new ApiError(
        401,
        "Incorrect email or password",
        "INVALID_CREDENTIALS",
      );
    }
    const tokens = issueTokens(db, user.id);
    saveDb(db);
    return { user: toPublicUser(user), tokens };
  },

  async refresh(req: RefreshTokenRequest): Promise<AuthTokens> {
    await delay(100, 200);
    const db = getDb();
    const session = db.sessions.find(
      (s) => s.refreshToken === req.refreshToken,
    );
    if (!session) throw mockError.unauthorized();
    db.sessions = db.sessions.filter((s) => s !== session);
    const tokens = issueTokens(db, session.userId);
    saveDb(db);
    return tokens;
  },

  async logout(req: LogoutRequest): Promise<void> {
    await delay(100, 250);
    const db = getDb();
    db.sessions = db.sessions.filter(
      (s) => s.refreshToken !== req.refreshToken,
    );
    saveDb(db);
  },

  async me(): Promise<User> {
    await delay(120, 260);
    return toPublicUser(requireUser(getDb()));
  },

  async forgotPassword(
    _req: ForgotPasswordRequest,
  ): Promise<{ message: string }> {
    await delay();
    // Same response whether or not the account exists, as a real backend should.
    return {
      message:
        "If an account exists for this email, a reset link is on its way.",
    };
  },

  async updateProfile(req: UpdateProfileRequest): Promise<User> {
    await delay();

    const db = getDb();

    const user = requireUser(db);

    user.fullName = req.fullName.trim();

    user.phone = req.phone?.trim() || null;

    saveDb(db);

    return toPublicUser(user);
  },

  async changePassword(req: ChangePasswordRequest): Promise<void> {
    await delay();
    const db = getDb();
    const user = requireUser(db);
    if (user.passwordDigest !== mockPasswordDigest(req.currentPassword)) {
      throw mockError.unprocessable(
        "Your current password is incorrect",
        "INVALID_PASSWORD",
      );
    }
    user.passwordDigest = mockPasswordDigest(req.newPassword);
    saveDb(db);
  },

  async getPreferences(): Promise<NotificationPreferences> {
    await delay(150, 300);
    return { ...requireUser(getDb()).preferences };
  },

  async updatePreferences(
    req: NotificationPreferences,
  ): Promise<NotificationPreferences> {
    await delay(200, 400);
    const db = getDb();
    const user = requireUser(db);
    user.preferences = { ...req };
    saveDb(db);
    return { ...user.preferences };
  },
};
