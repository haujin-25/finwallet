# Role-based access and wallet administration

## Identity and role assignment

V9 gives every existing and new user `ROLE_USER`. Roles are stored in a constrained database column. Registration does not bind role fields, and the application’s JPA mapping has no role writer. No users are promoted by the migration.

Use a separately authorized database operator and a verified existing user ID to grant or revoke the role. Record the approval and operator identity in the deployment change process. For example, an approved operator would run the following with the verified ID bound to the SQL parameter.

```sql
UPDATE users SET role = 'ROLE_ADMIN' WHERE id = ? AND role = 'ROLE_USER';
UPDATE users SET role = 'ROLE_USER' WHERE id = ? AND role = 'ROLE_ADMIN';
```

JWTs authenticate the signed subject and, when present, bind the signed user ID to the same current database account. Verification allows only the signing algorithm for the configured HMAC key and requires an expiration. Each request loads the current account status and role from PostgreSQL. Role claims have no effect. A role change applies on the next authorization lookup, including when the same token is reused. A request that passed that lookup before revocation can already be in flight.

Set `JWT_SECRET` to a strong, deployment-managed random key before starting the application. Spring tests use a test-only key. Install a new secret rather than reusing the former source-controlled development fallback. Changing the secret invalidates tokens signed with the previous key.

## Administrative API

All endpoints under `/api/admin/**` use Spring Security's ADMIN role at the request and service boundaries. Anonymous requests receive 401 and ordinary users receive 403.

| Endpoint | Behavior |
| --- | --- |
| `GET /api/admin/wallets/{walletId}` | Returns the wallet status and posted, reserved, and available balances. |
| `POST /api/admin/wallets/{walletId}/freeze` | Transitions ACTIVE to FROZEN. |
| `POST /api/admin/wallets/{walletId}/unfreeze` | Transitions FROZEN to ACTIVE. |
| `GET /api/admin/audit-logs?page=0&size=20` | Returns newest audit records first, with a maximum page size of 100. |
| `GET /api/admin/reconciliation` | Runs the existing read-only REPEATABLE READ report. |

Freeze and unfreeze require a JSON string `reason`, with a single printable line and a maximum of 500 characters. A repeat against the target state succeeds without another wallet mutation. Each successful call records an audit row with outcome APPLIED or NO_CHANGE. SUSPENDED and CLOSED wallets cannot transition through these endpoints.

## Financial boundaries and auditing

Freeze and unfreeze take the same pessimistic wallet row lock as transfers and funding. They read status for the first time under the lock in a fresh transaction. A financial operation already holding the wallet lock may finish first. A new operation that locks the wallet after the freeze commits sees FROZEN and rejects new spending or funding. Transfers and reversals keep ascending wallet ID ordering. An administrative write locks only its target row.

Frozen wallets cannot send or receive a new transfer, create a new deposit request or withdrawal hold, or participate in a new reversal. Replays of already-recorded operations can return the stored result without moving money. Trusted settlement can still complete or fail funding that was already pending. A failed withdrawal releases its existing hold. This lets the application account for a previously authorized external outcome while the wallet is frozen.

The audit row and wallet status commit together. The actor comes from the authenticated principal, and neither balances nor reserves change during a freeze or unfreeze. The app has no audit edit or delete route, and the audit repository only inserts and reads. Rejected requests return their HTTP status and do not create durable audit rows. The app does not maintain a separate rejection log. Audit reasons should contain operational context without credentials, tokens, or unnecessary customer details.

Customer APIs retain their existing ownership boundaries. Wallet administrators can inspect, freeze, unfreeze, and reconcile. Trusted reversal and provider settlement remain internal and have no administrative or customer HTTP route. The ADMIN role grants no authority to mint money or issue payouts.

The reconciliation endpoint scans the financial tables and returns all discrepancy details. Restrict its operational use to authorized staff and limit repeated calls at the deployment boundary. Before production, use separate migration, application, and privileged operator credentials, restrict database updates to role and audit records, track role changes and denied requests centrally, and establish key rotation and administrative MFA.
