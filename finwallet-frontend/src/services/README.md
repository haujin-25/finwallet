# Service layer

All data access goes through `services/api/*.service.ts`. UI code never imports from `services/mock`.

## Authentication

For local development, set `VITE_USE_MOCK_API=false` and `FINWALLET_API_BASE_URL=http://localhost:8080/api` in the ignored `.env.local` file and restart Vite. In production, set `VITE_USE_MOCK_API=false` at build time and `FINWALLET_API_BASE_URL` in the TanStack Start server environment. The server URL defaults to `http://localhost:8080/api`. Rebuild when changing the Vite flag for production. Serve production over HTTPS so the browser sends the Secure session cookie.

| Spring endpoint              | Request                                | Success                                          |
| ---------------------------- | -------------------------------------- | ------------------------------------------------ |
| POST /api/users/register     | JSON `{ fullName, email, password }`   | 201 with `{ id, email, fullName, status }`       |
| POST /api/auth/login         | JSON `{ email, password }`             | 200 with `{ accessToken, tokenType, expiresIn }` |
| GET /api/users/email/{email} | Bearer JWT and URL-encoded JWT subject | 200 with `{ id, email, fullName, status }`       |

Registration creates the account and prompts the user to sign in. TanStack Start handles login on the server and stores the JWT in an HttpOnly, SameSite=Lax cookie that expires with the JWT and uses Secure in production. Session reads validate the token through Spring. The existing Start CSRF middleware protects the server functions. Logout clears the cookie. Spring exposes no refresh, revocation, or password reset endpoint, so an expired session requires another login and logout cannot revoke a previously copied JWT.

Spring auth failures return plain text for controller errors and JSON for security filter errors. Duplicate registration returns 409, invalid credentials return 401, and suspended or closed accounts return 403.

## Wallet overview

Live `/overview` calls the Start wallet server function, which takes no wallet or user parameters. It reads the existing HttpOnly session cookie and forwards its JWT to Spring's `GET /api/wallets/me` as a Bearer credential. Spring selects the wallet from its authenticated principal. Start returns the wallet DTO with a `Cache-Control: no-store` response. The available balance is displayed in MYR from the backend's integer sen value.

The simulated money features are available in demo mode (`VITE_USE_MOCK_API=true`). Demo state is stored locally and demo credentials are tab-scoped.

Money is always integer minor units (sen). Balances, fees and transaction outcomes are decided
by the backend (or the mock backend in `mock/mock-rules.ts`), never in components.

## Live transfers

`/transfer` reviews the recipient email, integer sen amount and optional description before POSTing to Spring's `/api/transfers`. Start uses the existing session cookie and forwards a bearer credential. The idempotency key is part of the JSON body. Spring returns `201` for a completed transfer and for a matching replay.

Before submitting, the browser saves the reviewed fields and UUID in tab-scoped session storage under the signed-in account. An interrupted transfer can be retried with identical details after navigating away, reloading or signing in again in the same tab. The browser keeps the saved request until a receipt arrives or the initial request receives a definitive rejection that does not require signing in again. Session storage is scoped to the original tab. The API exposes account transfer history but has no lookup by idempotency key, so the client uses a matching POST replay to recover a receipt. A confirmed receipt triggers a wallet refetch.

## Transfer history

Live `/transactions` reads Spring's `GET /api/transfers/me` through a Start server
function. The request sends zero-based `page` and `size`; Spring uses the
authenticated principal to select the wallet. The route caches pages per account
and successful transfers invalidate those pages. The UI uses Spring's transfer and
reversal rows, total counts, and `hasNext`. The demo filters and transaction details
are available in demo mode.

## Legacy endpoint assumptions for later phases

| Method | Path                | Service                                        |
| ------ | ------------------- | ---------------------------------------------- |
| GET    | /wallets/me/balance | walletService.getMyBalance                     |
| GET    | /transfers          | transferService.list                           |
| GET    | /transfers/:id      | transferService.get                            |
| POST   | /payments/deposit   | paymentService.deposit (sends Idempotency-Key) |
| GET    | /payments/:id       | paymentService.get                             |
| GET    | /transactions       | transactionService.list                        |
| GET    | /transactions/:id   | transactionService.get                         |

Proposed additions the UI already uses (to add on the backend):

| Method  | Path                               | Why                                                  |
| ------- | ---------------------------------- | ---------------------------------------------------- |
| GET     | /wallets/me/summary?days=30        | Money in/out totals for the dashboard                |
| POST    | /auth/forgot-password              | Password reset email                                 |
| PATCH   | /users/me                          | Profile edits                                        |
| POST    | /users/me/password                 | Password change                                      |
| GET/PUT | /users/me/notification-preferences | Notification settings                                |
