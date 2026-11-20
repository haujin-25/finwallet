# Backend API reference

Examples use `API=http://127.0.0.1:8080/api` and the shell variables from [Getting started](01-getting-started.md). `JWT` is a customer access token and `ADMIN_JWT` is a token for a user with the current database role `ROLE_ADMIN`. Use `Content-Type: application/json` on JSON POST requests. GET requests carry no body. Monetary JSON fields are integer sen and timestamps carry a timezone offset.

Controllers return DTOs. The [exception handler](../src/main/java/finwallet_api/common/exception/GlobalExceptionHandler.java) often returns plain text for application errors. The customer security chain returns JSON for its `401` and `403` responses. Framework validation and routing errors can have a different envelope. Every customer or admin route can return `401` for missing or rejected authentication and `500` for unexpected server or persistence failures. Admin routes can also return `403` for an authenticated non-admin.

## Accounts and authentication

| Method and path | Access and validation | Success and specific errors |
| --- | --- | --- |
| `POST /api/users/register` | Public. Body `email` (valid, max 255), `password` (8–72 characters, nonblank), `fullName` (nonblank, max 100). Role fields are ignored. | `201` User. `400` invalid input, `409` duplicate email or wallet conflict. |
| `POST /api/auth/login` | Public. Body `email` (valid, max 255), `password` (nonblank). | `200` `{"accessToken":"<jwt>","tokenType":"Bearer","expiresIn":900}` with the configured lifetime in seconds. `400` validation, `401` invalid credentials, `403` suspended or closed user with valid credentials. |
| `GET /api/users/email/{email}` | Bearer. URL-encode the path segment and use the stored canonical email. A caller can look up another account’s profile. | `200` User, `404` missing email. |

User shape is `{"id":1,"email":"alice@example.test","fullName":"Alice","status":"ACTIVE"}`. Registration creates the wallet and returns no JWT. Source is in the [user](../src/main/java/finwallet_api/user/controller/UserController.java) and [auth](../src/main/java/finwallet_api/auth/controller/AuthController.java) controllers.

```sh
# Set JSON through the hidden-input helpers in Getting started.
printf '%s' "$REGISTER_JSON" | curl -sS "$API/users/register" -H 'Content-Type: application/json' --data-binary @-
printf '%s' "$LOGIN_JSON" | curl -sS "$API/auth/login" -H 'Content-Type: application/json' --data-binary @- | jq 'del(.accessToken)'
curl -sS "$API/users/email/alice%40example.test" -H "Authorization: Bearer $JWT"
```

`REGISTER_JSON` and `LOGIN_JSON` denote the request bodies from the table. The walkthrough supplies complete shell helpers that construct them without recording plaintext passwords.

## Wallets

### GET /api/wallets/me

Bearer. No filters. Returns `200` and `{"walletId":10,"currency":"MYR","balance":7000,"status":"ACTIVE","reservedBalance":0,"availableBalance":7000}`. Returns `404` if no wallet exists. The [wallet controller](../src/main/java/finwallet_api/wallet/controller/WalletController.java) uses the authenticated principal.

### GET /api/wallets/me/activity

Bearer. Optional zero-based `page` (default 0) and `size` (default 20, 1–100). Invalid pagination returns `400`. Returns `200` with `{items,page,size,hasNext}`. Each item has `reference`, `operationType`, `direction`, `amount`, `currency`, `status`, nullable `description`, and `createdAt`. Types are `TRANSFER`, `REVERSAL`, `DEPOSIT`, and `WITHDRAWAL`. Direction is `INCOMING` or `OUTGOING`. For funding, direction indicates the intended movement. A pending deposit has no wallet credit. A missing wallet returns an empty list. See the [activity controller](../src/main/java/finwallet_api/wallet/controller/WalletActivityController.java).

```sh
curl -sS "$API/wallets/me" -H "Authorization: Bearer $JWT"
curl -sS "$API/wallets/me/activity?page=0&size=20" -H "Authorization: Bearer $JWT"
```

## Transfers

### POST /api/transfers

Bearer and JSON. The sender is derived from the principal. Body fields:

- `recipientEmail` is a nonblank email of at most 255 characters. Supply the recipient’s stored email.
- `amount` is a positive JSON integer that fits a signed Java `Long`. Decimal, numeric string, and boolean inputs are rejected.
- `description` is optional or null, up to 500 Unicode code points, with no NUL.
- `idempotencyKey` is required in the body, 1–255 characters from letters, digits, `._:-`.

`201` on posting or exact replay. Response shape:

```json
{"transferReference":"TRF-20261120-00000001","senderWalletId":10,"recipientWalletId":11,"amount":3000,"currency":"MYR","status":"COMPLETED","description":"Lunch","createdAt":"2026-11-20T01:00:00Z"}
```

`400` invalid input, self-transfer or currency mismatch. `404` missing sender wallet, recipient or recipient wallet. `409` inactive wallet or a key already tied to different recipient wallet, amount, or description. `422` insufficient available funds or a configured transfer limit. Limit violations return JSON `{code,message}`. See [transfer policy codes](transfer-limits-and-risk.md#posting-time-and-retries). See [TransferRequest](../src/main/java/finwallet_api/transfer/dto/TransferRequest.java) and [TransferController](../src/main/java/finwallet_api/transfer/controller/TransferController.java).

### GET /api/transfers/me

Bearer. Optional `page` defaults to 0, `size` to 20 and is bounded 1–100. `page × size` cannot exceed 2,147,483,647. `direction` is `INCOMING` or `OUTGOING`. `from` is inclusive and `to` exclusive. Both timestamps need an offset, year 1–9999, and at most microsecond precision. When both are given, `from` must precede `to`. Invalid parameters return `400`.

`200` returns `{items,page,size,totalElements,totalPages,hasNext}` ordered by descending creation time then ID. Items contain `transferReference`, `direction`, `amount`, `currency`, `status`, nullable `description`, `counterpartyName`, `createdAt`, `operationType` (`TRANSFER` or `REVERSAL`), and `reversalOfReference` when applicable. A missing wallet yields an empty page. [History controller](../src/main/java/finwallet_api/transfer/controller/TransferHistoryController.java)

```sh
curl -sS "$API/transfers" -H "Authorization: Bearer $JWT" -H 'Content-Type: application/json' \
  --data '{"recipientEmail":"bob@example.test","amount":3000,"idempotencyKey":"guide-transfer-1","description":"Lunch"}'
curl -sS -G "$API/transfers/me" -H "Authorization: Bearer $JWT" \
  --data-urlencode 'direction=OUTGOING' --data-urlencode 'from=2026-11-20T00:00:00+08:00'
```

## Simulated funding

| Method and path | Access and request | Success and specific errors |
| --- | --- | --- |
| `POST /api/deposits` | Bearer and JSON FundingRequest. | `201` FundingResponse. A new request is `PENDING`. `400` invalid input or non-MYR wallet, `404` missing wallet, `409` inactive wallet or changed same-key body. |
| `POST /api/withdrawals` | Bearer and JSON FundingRequest. | Same as deposit plus `422` for insufficient available funds. A new pending withdrawal reserves funds. |
| `GET /api/funding/{reference}` | Bearer. Reference from a funding response. | `200` FundingResponse, `404` if the operation is missing or belongs to another account, or caller has no wallet. |
| `POST /api/dev/funding/{reference}/settle` | Separate `X-Dev-Funding-Token` header, loopback, enabled development configuration. JSON `{"outcome":"SUCCESS"}` or `{"outcome":"FAILURE"}`. | `200` FundingResponse, `400` invalid outcome or wallet currency mismatch, `403` unauthorized on the enabled route, `404` unknown reference, `409` conflicting terminal outcome. Disabled configuration leaves the route unregistered. |

[FundingRequest](../src/main/java/finwallet_api/funding/dto/FundingRequest.java) accepts only `amount` (positive JSON integer within signed `Long`), `idempotencyKey` (body, 1–255 characters from letters, digits, `._:-`), and optional or null `description` (max 500 Unicode code points, no NUL). Retries match amount and description within wallet and operation type.

[FundingResponse](../src/main/java/finwallet_api/funding/dto/FundingResponse.java) contains `reference`, `operationType` (`DEPOSIT` or `WITHDRAWAL`), `status` (`PENDING`, `COMPLETED` or `FAILED`), `amount`, `currency`, nullable `description`, `createdAt`, and nullable `resolvedAt`. Use the appropriate returned reference in each follow-up call. [Funding controller](../src/main/java/finwallet_api/funding/controller/FundingController.java) · [Development controller](../src/main/java/finwallet_api/funding/dev/DevFundingController.java)

```sh
curl -sS "$API/deposits" -H "Authorization: Bearer $JWT" -H 'Content-Type: application/json' \
  --data '{"amount":10000,"idempotencyKey":"guide-deposit-1"}'
curl -sS "$API/withdrawals" -H "Authorization: Bearer $JWT" -H 'Content-Type: application/json' \
  --data '{"amount":500,"idempotencyKey":"guide-withdrawal-1"}'
curl -sS "$API/funding/$FUNDING_REF" -H "Authorization: Bearer $JWT"
curl -sS "$API/dev/funding/$FUNDING_REF/settle" \
  -H "X-Dev-Funding-Token: $FINWALLET_DEV_FUNDING_TOKEN" -H 'Content-Type: application/json' \
  --data '{"outcome":"SUCCESS"}'
```

## Administration

All routes below require admin bearer authorization. Wallet IDs must be positive. For mutations the body is only `{"reason":"Local exercise"}`. The value must be nonblank, at most 500 Unicode code points, and one printable line without control/format or line/paragraph separator characters.

| Method and path | Success and specific errors |
| --- | --- |
| `GET /api/admin/wallets/{walletId}` | `200` wallet DTO with the same fields as `/wallets/me`. `400` invalid ID, `404` missing wallet. |
| `POST /api/admin/wallets/{walletId}/freeze` | `200` wallet DTO. ACTIVE becomes FROZEN, or an already frozen wallet remains frozen and an audit row records `NO_CHANGE`. `400` invalid input, `404` missing wallet, `409` suspended or closed wallet. |
| `POST /api/admin/wallets/{walletId}/unfreeze` | `200` wallet DTO. FROZEN becomes ACTIVE, or an already active wallet receives a `NO_CHANGE` audit. Same errors as freeze. |
| `GET /api/admin/audit-logs` | `200` `{items,page,size,hasNext}`; optional zero-based `page` default 0 and `size` default 20 (1–100). `400` invalid pagination. |
| `GET /api/admin/reconciliation` | `200` `{reconciledAt,walletsChecked,transfersChecked,discrepancyCount,discrepancies,status}` with `status` `PASS` or `FAIL`. `FAIL` is a financial integrity finding in a successful HTTP response. |

Audit items contain `id`, `administratorUserId`, `action` (`FREEZE` or `UNFREEZE`), `walletId`, `occurredAt`, `reason`, `previousStatus`, `newStatus`, and `outcome` (`APPLIED` or `NO_CHANGE`). Reconciliation discrepancies contain `type`, nullable `walletId`, `transferId` and `ledgerEntryId`, and `detail`. Only completed parent operations contribute to expected posted balances and the simulated settlement position. Unexpected entries attached to failed transfers or pending or failed funding operations still produce discrepancies. The exact types are listed in [ReconciliationDiscrepancy](../src/main/java/finwallet_api/reconciliation/dto/ReconciliationDiscrepancy.java). [Administration controller](../src/main/java/finwallet_api/admin/controller/WalletAdministrationController.java)

```sh
curl -sS "$API/admin/wallets/$WALLET_ID" -H "Authorization: Bearer $ADMIN_JWT"
curl -sS "$API/admin/wallets/$WALLET_ID/freeze" -H "Authorization: Bearer $ADMIN_JWT" \
  -H 'Content-Type: application/json' --data '{"reason":"Local freeze exercise"}'
curl -sS "$API/admin/wallets/$WALLET_ID/unfreeze" -H "Authorization: Bearer $ADMIN_JWT" \
  -H 'Content-Type: application/json' --data '{"reason":"Local exercise complete"}'
curl -sS "$API/admin/audit-logs?page=0&size=20" -H "Authorization: Bearer $ADMIN_JWT"
curl -sS "$API/admin/reconciliation" -H "Authorization: Bearer $ADMIN_JWT"
```


### GET /api/admin/transfer-risk-alerts

Admin bearer. No request body. Optional zero-based §page§ defaults to 0 and §size§ defaults to 20 (1–100). Returns §200§ with §{items,page,size,hasNext}§. Each item has §id§, §transferReference§, §senderWalletId§, §rule§, and §occurredAt§. Invalid pagination returns §400§. The rules and error codes are documented in [Transfer limits and risk controls](transfer-limits-and-risk.md).

§§§sh
curl -sS "$API/admin/transfer-risk-alerts?page=0&size=20" -H "Authorization: Bearer $ADMIN_JWT"
§§§
