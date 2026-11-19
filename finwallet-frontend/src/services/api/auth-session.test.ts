import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { deleteCookie, getCookie, setCookie } from "@tanstack/react-start/server";
import { endSession, readSession, registerAccount, startSession } from "./auth-session.server";

vi.mock("@tanstack/react-start/server", () => ({
  getCookie: vi.fn(),
  setCookie: vi.fn(),
  deleteCookie: vi.fn(),
  setResponseHeader: vi.fn(),
}));

const fetchSpring = vi.fn<typeof fetch>();
const credentials = { email: "test+wallet@example.com", password: "test password" };

beforeEach(() => {
  vi.stubEnv("FINWALLET_API_BASE_URL", "http://spring.test/api");
  vi.stubGlobal("fetch", fetchSpring);
});

afterEach(() => {
  vi.resetAllMocks();
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

describe("Spring authentication", () => {
  it("registers at the users endpoint and preserves the duplicate email error", async () => {
    fetchSpring.mockResolvedValueOnce(new Response("Email already registered", { status: 409 }));
    const data = { ...credentials, fullName: "Test Wallet" };

    expect(await registerAccount(data)).toEqual({
      ok: false,
      status: 409,
      message: "Email already registered",
    });
    expect(fetchSpring).toHaveBeenCalledWith(
      "http://spring.test/api/users/register",
      expect.objectContaining({ method: "POST", body: JSON.stringify(data) }),
    );
    expect(setCookie).not.toHaveBeenCalled();
  });

  it.each([
    [400, "password: size must be between 8 and 72"],
    [401, "Invalid email or password"],
    [403, "Account is suspended"],
  ])("preserves Spring's %i error without starting a session", async (status, message) => {
    fetchSpring.mockResolvedValueOnce(new Response(message, { status }));
    const result =
      status === 400
        ? await registerAccount({ ...credentials, fullName: "Test Wallet" })
        : await startSession(credentials);
    expect(result).toEqual({ ok: false, status, message });
    expect(setCookie).not.toHaveBeenCalled();
  });

  it("rejects an invalid success response without writing a session", async () => {
    fetchSpring.mockResolvedValueOnce(Response.json({ accessToken: null }));
    expect(await startSession(credentials)).toMatchObject({ ok: false, status: 502 });
    expect(setCookie).not.toHaveBeenCalled();
  });

  it("keeps Spring's login JWT out of the RPC result", async () => {
    vi.stubEnv("NODE_ENV", "production");
    fetchSpring.mockResolvedValueOnce(
      Response.json({ accessToken: "example.jwt.value", tokenType: "Bearer", expiresIn: 900 }),
    );

    expect(await startSession(credentials)).toEqual({ ok: true, data: null });
    expect(fetchSpring).toHaveBeenCalledWith(
      "http://spring.test/api/auth/login",
      expect.objectContaining({ method: "POST", body: JSON.stringify(credentials) }),
    );
    expect(setCookie).toHaveBeenCalledWith(
      "finwallet_session",
      "example.jwt.value",
      expect.objectContaining({
        httpOnly: true,
        sameSite: "lax",
        secure: true,
        path: "/",
        maxAge: 900,
      }),
    );
  });

  it("uses the JWT subject to look up the profile and leaves authorization to Spring", async () => {
    const payload = btoa(JSON.stringify({ sub: credentials.email }));
    const token = `header.${payload}.signature`;
    vi.mocked(getCookie).mockReturnValue(token);
    fetchSpring.mockResolvedValueOnce(
      Response.json({ message: "Authentication required" }, { status: 401 }),
    );

    expect(await readSession()).toEqual({
      ok: false,
      status: 401,
      message: "Authentication required",
    });
    expect(fetchSpring).toHaveBeenCalledWith(
      "http://spring.test/api/users/email/test%2Bwallet%40example.com",
      expect.objectContaining({
        method: "GET",
        headers: expect.objectContaining({ Authorization: `Bearer ${token}` }),
      }),
    );
  });

  it("reports a network failure without creating a session", async () => {
    fetchSpring.mockRejectedValueOnce(new TypeError("network unavailable"));
    expect(await startSession(credentials)).toMatchObject({ ok: false, status: 0 });
    expect(setCookie).not.toHaveBeenCalled();
  });

  it("clears the session on logout and makes no Spring logout request", async () => {
    vi.stubEnv("NODE_ENV", "production");
    endSession();
    expect(deleteCookie).toHaveBeenCalledWith(
      "finwallet_session",
      expect.objectContaining({ path: "/", httpOnly: true, sameSite: "lax", secure: true }),
    );
    expect(await readSession()).toEqual({ ok: true, data: null });
    expect(fetchSpring).not.toHaveBeenCalled();
  });
});
