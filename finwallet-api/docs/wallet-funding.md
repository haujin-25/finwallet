# Wallet funding and withdrawals

## Customer API

All amounts are positive JSON integers in MYR sen. Requests accept `amount`, `idempotencyKey`, and an optional `description`. MYR is fixed by the server. The JWT principal supplies the wallet owner. Customer input cannot supply a wallet, currency, provider reference, or payment outcome.

| Endpoint | Behavior |
| --- | --- |
| `POST /api/deposits` | Returns a pending request with HTTP 201. The wallet receives no funds until trusted settlement. |
| `POST /api/withdrawals` | Reserves the amount and returns a pending request with HTTP 201. Insufficient available funds return 422. |
| `GET /api/funding/{reference}` | Returns the current status for the caller's operation. Unknown and other users' references return 404. |
| `GET /api/wallets/me/activity?page=0&size=20` | Returns paginated transfers, reversals, deposits, and withdrawals. Each item includes its status and intended direction. A pending or failed item does not indicate a posted balance movement. |

The activity endpoint is separate from `GET /api/transfers/me`. It orders newest first and uses deterministic tie-breakers. It exposes no settlement account or provider references. The existing wallet response keeps `balance` as the posted total and adds `reservedBalance` and `availableBalance`.

## Lifecycle, locking, and retries

A request starts at PENDING. A trusted success advances it to COMPLETED. A trusted failure advances it to FAILED. Both terminal states are final. TIMEOUT leaves the current state and any hold intact.

Only trusted in-process code calls `FundingSettlementService.checkStatus(reference, scenario)`. The customer controller never calls it. The opt-in local development trigger below is the only HTTP caller. There is no provider callback or background completion job. The deterministic adapter returns a status for the persisted provider transaction reference. It has no external effects and does not issue a payout on a retry. Trusted code can simulate a later status check after a timeout with the same reference.

Customer requests use one key per wallet and operation type. Each user has one wallet. Identical retries return the existing operation with its current status, including after a lost HTTP response. A changed amount or description returns 409. The provider transaction reference is stable and unique. Terminal event references are also unique. Replaying the same terminal event returns the existing operation. Contradictory terminal results are rejected.

Funding requests and settlements lock the customer wallet before reading current operation state. This serializes reservations and competing confirmations alongside transfer and reversal spending. Settlement uses a fresh READ COMMITTED transaction. Holds are separate from journal entries. Available balance equals posted total minus reserved balance. Pending withdrawals add a hold, successful payouts remove that hold and debit the posted total once, and failed payouts release the hold without journal entries. Terminal settlement can complete for a wallet that became inactive after the request, since it resolves an existing funding event. The wallet currency must still match.

## Local development settlement

Use an isolated local PostgreSQL database with test accounts. The trigger exists only when
`finwallet.dev.funding.enabled=true` and every explicitly active Spring profile is
`local` or `test`. A profile such as `production` disables the trigger even if
`local` is also active and the flag is set. The development token must be at least
32 characters. A missing or invalid token, or a non-loopback connection, receives
HTTP 403 when the trigger is enabled.

From the backend directory, with your usual local `JWT_SECRET` and database configuration:

```sh
export FINWALLET_DEV_FUNDING_TOKEN="$(openssl rand -hex 32)"
SPRING_PROFILES_ACTIVE=local FINWALLET_DEV_FUNDING_ENABLED=true \
  SERVER_ADDRESS=127.0.0.1 SERVER_FORWARD_HEADERS_STRATEGY=none ./mvnw spring-boot:run
```

### Fund a wallet from the terminal

With Python 3 installed and `FINWALLET_DEV_FUNDING_TOKEN` set to the same value as
the running backend, run this from the backend directory:

```sh
export FINWALLET_DEV_API_URL=http://127.0.0.1:8080
./scripts/dev-fund-wallet.sh testuser2@gmail.com 100
```

The script verifies the local settlement route before requesting a deposit and
prompts for the registered user's password with input hidden. It prints the
deposit key before submitting, followed by the reference, settlement status,
and posted and available balances. If an attempt was interrupted or the backend
could not confirm the result, reuse the printed key with the same backend, account,
and amount:

```sh
./scripts/dev-fund-wallet.sh testuser2@gmail.com 100 --retry '<printed-key>'
```

Keep the original test database in place when resuming. A command without
`--retry` starts a separate deposit. The script connects directly to
`http://127.0.0.1:<port>` and refuses redirects.

### Manual API calls

Use the same token in the terminal that calls the trigger. First log in via
`POST /api/auth/login` and put the returned `accessToken` in `CUSTOMER_JWT`.
Request a deposit and save the returned reference:

```sh
curl --fail-with-body http://127.0.0.1:8080/api/deposits \
  -H "Authorization: Bearer ${CUSTOMER_JWT}" \
  -H 'Content-Type: application/json' \
  -d '{"amount":10000,"idempotencyKey":"local-rm100-001","description":"Local test deposit"}'

FUNDING_REFERENCE='replace-with-the-returned-reference'
curl --fail-with-body "http://127.0.0.1:8080/api/dev/funding/${FUNDING_REFERENCE}/settle" \
  -H "X-Dev-Funding-Token: ${FINWALLET_DEV_FUNDING_TOKEN}" \
  -H 'Content-Type: application/json' \
  -d '{"outcome":"SUCCESS"}'
```

The trigger returns HTTP 200 and the resulting funding DTO. Use `FAILURE` on a
separate pending operation to test a failed settlement. Repeating a terminal outcome
returns the existing result. Attempting to change a terminal outcome returns 409.
Use a fresh deposit key for each new deposit and reuse that key for retries of the
same request. Confirm the resulting balance with `GET /api/wallets/me` using the customer JWT.

The development token can settle any known funding reference in the local database.
Keep it separate from the JWT signing secret, out of source control and frontend
configuration, and away from logs. Bind to loopback and call Spring directly. Keep
the trigger off shared networks, reverse proxies, tunnels, and production databases.
A customer or admin JWT grants no access to the trigger by itself. Stop the local
server and unset the token when finished.

## Accounting and reconciliation

V8 adds the wallet reservation column and three tables. `funding_operations` tracks requests and provider events. `funding_accounts` identifies the separate MYR settlement account. `funding_ledger_entries` records one customer leg and one settlement leg per completion. Transfers and reversals retain their existing journal and constraints.

FinWallet's signed balance convention treats CREDIT as an increase and DEBIT as a decrease. A deposit credits the customer and debits simulated settlement. A payout debits the customer and credits simulated settlement. Every posted operation writes an equal pair with the same currency. Wallet changes, the terminal outcome, and journal rows commit in one transaction. The settlement account has no customer owner, no spendable balance, and no transfer address. Its signed net position starts at zero and is derived from the journal. It measures simulated flows and provides no evidence of cash reserves or external liquidity.

Customer wallets still start at zero. Their posted balance now includes both transfer and completed funding journal entries. Reconciliation compares reserved balances with pending withdrawal amounts, checks the funding journal shape and ownership, and compares posted settlement movements with the independent totals from completed operations. These queries use the same REPEATABLE READ snapshot as transfer and reversal checks. They report discrepancies without changing records. The application journal is append-only. Database credentials with direct write permissions can still alter history, so operational controls and independent reconciliation remain necessary.

A real integration would replace scenario selection with authenticated provider notifications or authenticated status queries, verify transaction reference, amount and currency, and persist dispatch and provider idempotency state before initiating payouts. It would also need a real external settlement statement, authenticated payout destinations, and operational controls for unresolved events and liquidity. This simulator performs no external transfers.
