import { env } from "./env";

/** Demo-only hints shown in the UI while the mock API is active. */
export const DEMO_MODE = env.useMockApi;

export const DEMO_ACCOUNT = { email: "demo@finwallet.my", password: "Demo1234!" } as const;

export const DEMO_RECIPIENTS = [
  { name: "Aisyah Rahman", identifier: "aisyah.rahman@finwallet.my" },
  { name: "Daniel Lim", identifier: "daniel.lim@finwallet.my" },
  { name: "Priya Nair", identifier: "FW-USR-1004" },
] as const;
