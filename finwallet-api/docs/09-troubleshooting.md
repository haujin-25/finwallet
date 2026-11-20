# Troubleshooting

## Startup and database

| Symptom | What to check |
| --- | --- |
| Connection refused | Start PostgreSQL. Run `pg_isready -h 127.0.0.1 -p 5432`, then try `psql -h 127.0.0.1 -U finwallet_user -d finwallet -c 'SELECT 1;'`. Confirm the database name and listening port. |
| Password authentication failed | Ensure `SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD` match the configured role and that PostgreSQL permits the connection. Check via `psql` with the same role. |
| `Operation not permitted` from a socket | Check sandbox policy before changing PostgreSQL credentials. A sandbox can deny loopback connections even with PostgreSQL installed. Run integration tests from a normal local terminal. |
| Spring cannot resolve `JWT_SECRET` or reports a weak key | Source trusted shell exports before starting Spring. Generate a fresh key with `openssl rand -hex 64` and assign it without printing live values. |
| Java or Maven startup fails | Run `java -version` and `./mvnw -version`. Use Java 21. The wrapper needs access to download the Maven distribution and dependencies on first use. |
| Port 8080 already in use | Examine `lsof -nP -iTCP:8080 -sTCP:LISTEN`. Stop your old process with Ctrl-C or start with `SERVER_PORT=8081`. Update curl and `FINWALLET_DEV_API_URL`. |
| Missing schema or migration checksum failure | Confirm the datasource points to the intended database and Flyway has permission to apply migrations. Query `SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;`. Missing history on a fresh database should be addressed at startup. Investigate checksum mismatches before making changes. |
| Maven tests fail | Check the first error in `target/surefire-reports/`. Use a separate test DB, correct credentials, and active PostgreSQL. Run one class from [Testing](08-testing.md) to isolate the failure. |

## HTTP responses

- **400** usually means malformed JSON, invalid fields, pagination, self-transfer, or currency mismatch. Use integer sen rather than a decimal or a quoted number. Some frontend and CLI inputs use MYR before conversion.
- **401** on a customer route means a missing, expired, tampered, or unusable token. Log in again. Login itself returns 401 for invalid credentials. Check the configured email, password and clock. A signing-key change invalidates old tokens.
- **403** can mean a rejected role, suspended or closed account at login, or denied development settlement. For a normal customer calling an admin route the JWT can be valid while the role is insufficient.
- **404** can mean an unknown route, missing wallet or recipient, or a funding reference outside the caller’s wallet. Check the exact path and the logged-in account. A disabled dev route may instead hit the normal JWT chain first.
- **409** covers duplicates, inactive wallets, changed payload under an existing key, or conflicting terminal settlement. Compare the original payload and operation status before retrying.
- **422** means inadequate available funds. Check the posted balance minus any reserved withdrawals.
- **500** can mean persistence or transaction failure. Inspect server logs without exposing sensitive data. For an uncertain money POST, recover with the original key and body.

## Development funding preflight returns 401

A `401` in the script preflight commonly indicates that the dev route and its scoped chain were not registered, so the request fell through to the normal JWT chain. An enabled route with a wrong token or a non-loopback peer returns `403`. A successful preflight deliberately submits an invalid outcome and expects `400` with an outcome validation error. This probe does not settle an operation.

1. Confirm the **backend process** was launched with `SPRING_PROFILES_ACTIVE=local` and `FINWALLET_DEV_FUNDING_ENABLED=true`. Check the active profile in startup logs. A profile set containing production or staging disables the route. A default profile is insufficient.
2. Set `FINWALLET_DEV_FUNDING_TOKEN` to the same independently generated value in the server and caller shells. Check presence with `test -n "$FINWALLET_DEV_FUNDING_TOKEN"` without printing it.
3. Set the script’s `FINWALLET_DEV_API_URL=http://127.0.0.1:8080` or use the actual listener port. Omit `/api` and trailing slash. Connect directly.
4. Correct `.env` syntax with `NAME='value'`, without whitespace around `=`. Use `set -a; source ./.env; set +a` in each relevant shell. **Restart Spring after changing its environment.** Restarting the script alone cannot enable the server route.
5. Stop shell tracing before handling credentials. Do not dump `env`, use verbose HTTP logging with live authorization headers, or paste tokens into bug reports.

[Configuration](02-configuration.md) · [Local funding workflow](wallet-funding.md)
