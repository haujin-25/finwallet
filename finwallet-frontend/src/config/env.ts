// Browser configuration. Live authentication uses FINWALLET_API_BASE_URL on the Start server.
export const env = {
  apiBaseUrl:
    (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? "http://localhost:3000/api/v1",
  useMockApi: (import.meta.env.VITE_USE_MOCK_API as string | undefined) === "true",
} as const;
