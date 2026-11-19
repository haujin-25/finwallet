import type { AuthTokens } from "@/types";

const ACCESS_KEY = "finwallet.demo.accessToken";
const REFRESH_KEY = "finwallet.demo.refreshToken";

const hasStorage = () => typeof window !== "undefined" && !!window.sessionStorage;

/** Browser storage for mock credentials used by the demo. */
export const tokenStorage = {
  getAccessToken(): string | null {
    return hasStorage() ? window.sessionStorage.getItem(ACCESS_KEY) : null;
  },
  getRefreshToken(): string | null {
    return hasStorage() ? window.sessionStorage.getItem(REFRESH_KEY) : null;
  },
  setTokens(tokens: AuthTokens) {
    if (!hasStorage()) return;
    window.sessionStorage.setItem(ACCESS_KEY, tokens.accessToken);
    window.sessionStorage.setItem(REFRESH_KEY, tokens.refreshToken);
  },
  clear() {
    if (!hasStorage()) return;
    window.sessionStorage.removeItem(ACCESS_KEY);
    window.sessionStorage.removeItem(REFRESH_KEY);
  },
};
