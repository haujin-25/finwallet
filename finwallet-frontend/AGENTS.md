# FinWallet frontend

FinWallet is independently maintained. The frontend uses React, TypeScript, Bun, TanStack Start, TanStack Router, and TanStack Query. The sibling backend uses Java 21, Spring Boot, Spring Security with JWT, PostgreSQL, and Flyway. As the repositories move into the monorepo, keep changes scoped to the owning project and coordinate contract changes across both directories.

## Architecture

- Create page routes in `src/routes/`. Signed-in pages live under `src/routes/_authenticated/` and use the layout’s session guard. The protected layout currently sets `ssr: false`. Regenerate `src/routeTree.gen.ts` through TanStack’s route tooling when routes change.
- Keep HTTP and DTO mapping in `src/services/api/` and shared application types in `src/types/`. Components consume services through route loaders, queries, or mutations. Keep demo implementations in `src/services/mock/` and demo UI settings in `src/config/demo.ts`. UI modules should use the service interfaces.
- Use `src/hooks/queries.ts` for shared TanStack Query options and keys. Scope live financial caches by user where applicable. Invalidate affected wallet and transfer-history queries after a confirmed transfer and clear cached user data on logout or authentication failure.
- Verify contracts in the backend’s controllers, DTOs, security rules, and tests before adding live functionality. Match HTTP methods, paths, status codes, validation, and pagination. Keep demo-only routes and legacy service contracts separate from live paths.

## Environment and authentication

- Use `.env.example` as a template and put local overrides in the ignored `.env.local`. Use `VITE_USE_MOCK_API=false` for Spring Boot and `true` for the demo mode. Restart Vite after changing local settings and rebuild when changing production Vite settings.
- Configure `FINWALLET_API_BASE_URL` in the TanStack Start server environment, for example `http://localhost:8080/api`. `VITE_API_BASE_URL` belongs to the legacy browser client. Vite’s `VITE_*` variables are public. Keep secrets and server credentials out of those variables, committed files, logs, and browser bundles.
- Route live authenticated requests through TanStack Start server functions and server-only service modules. Read the existing HttpOnly session cookie server-side and send the JWT to Spring as an Authorization bearer credential. Keep JWTs out of browser JavaScript, browser storage, and server-function responses. The browser storage helper for demo credentials is scoped to demo mode.
- Preserve the server-function CSRF protection in `src/start.ts` and the session cookie’s security attributes. Serve production over HTTPS. Use Spring’s authenticated principal for ownership, and treat route guards as navigation and session UX. Keep loading, expired-session, validation, and backend-failure states explicit. Live requests should surface errors without falling back to mock data.

## Money and financial state

- Represent MYR values as integer sen at API and state boundaries. Reuse `parseTransferAmount` for live transfer input, `parseAmountToMinor` where used by demo forms, and `formatMoney` for display. Validate the integer range and avoid floating-point money arithmetic.
- Spring is authoritative for balances, available funds, fees, and transaction outcomes. Display confirmed results and refresh server data instead of calculating new balances locally.
- Preserve the same idempotency key and exact payload when retrying an uncertain transfer. Generate a new key only for a new intent, and distinguish an unknown outcome from a confirmed rejection.

## Verification and documentation

- Use Bun. Run `bun run test`, `bun x --no-install tsc --noEmit`, `bun run lint`, and `bun run build` for changes that affect the frontend. Add focused regression coverage in the existing Vitest suite when needed. Report any check blocked by tooling or existing lint failures.
- Use a local Spring Boot and PostgreSQL instance to verify live flows when available. Distinguish that verification from tests with mocked responses.
- For every meaningful change, update the affected documentation and `CHANGELOG.md`. Use the monorepo changelog once it exists, or a frontend-local changelog while the repositories remain separate. Document user-visible behavior, contract or configuration changes, and verification limits.
