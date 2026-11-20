# Transfer limits and risk controls

Customer-originated outgoing transfers can be constrained by hard limits and can generate informational risk alerts. Thresholds are opt-in. With all thresholds unset, transfers use the existing funds, status, currency and overflow checks without quota or risk-history queries.

| Spring property under `finwallet.transfer` | Meaning |
| --- | --- |
| `max-amount-sen` | Maximum amount of one customer transfer in integer sen. |
| `daily-max-amount-sen` | Maximum total completed outgoing customer transfers per wallet and day, in sen. |
| `daily-max-count` | Maximum count of completed outgoing customer transfers per wallet and day. |
| `daily-zone` | Java timezone ID. Defaults to `Asia/Kuala_Lumpur`. |
| `risk-large-amount-sen` | Raise `LARGE_TRANSFER` at or above this transfer amount. |
| `risk-rapid-count` | Raise `RAPID_TRANSFERS` at or above this many outgoing customer transfers in the rolling window, including the candidate. |
| `risk-distinct-recipients` | Raise `DISTINCT_RECIPIENTS` at or above this many distinct recipient wallets in the rolling window, including the candidate once. |
| `risk-window` | Rolling lookback, default `PT5M`. Allowed range is one second to 24 hours. |

Each numeric threshold must be positive when configured and fit a signed Java long. Limits allow equality. An omitted threshold disables that check or rule. Configuration is read at startup. Set the same policy on every app instance. For example, use Spring Boot JSON configuration in a dedicated local shell:

```sh
export SPRING_APPLICATION_JSON='{"finwallet":{"transfer":{"max-amount-sen":10000,"daily-max-amount-sen":25000,"daily-max-count":5,"daily-zone":"Asia/Kuala_Lumpur","risk-large-amount-sen":7500,"risk-rapid-count":3,"risk-distinct-recipients":3,"risk-window":"PT5M"}}}'
./mvnw spring-boot:run
```

The example caps a transfer at RM100 and outgoing daily total at RM250. These are example settings. If a deployment already uses `SPRING_APPLICATION_JSON`, merge the transfer settings into that JSON. Restart all instances when changing policy. A timezone change reinterprets the current day’s historical usage and should be coordinated.

## Posting, time, and retries

The customer service acquires both wallet locks in ascending ID order and checks idempotency before policy evaluation. For a new transfer, it reads PostgreSQL’s `clock_timestamp()` under those locks and uses the same UTC microsecond timestamp for quota evaluation, rolling risk checks, and the persisted transfer. `CURRENT_TIMESTAMP` and `now()` would instead use the transaction start time, potentially before time spent waiting for locks. The calendar day is converted to an inclusive UTC start and exclusive UTC end using the configured zone. Correct clock synchronization on the PostgreSQL host still matters.

The sender lock stays held until commit, even across app instances. The quota statement runs at READ COMMITTED and sees the preceding lock holder’s committed transfers. It uses the existing sender/history index and exact PostgreSQL `sum(bigint)` with Java `BigInteger` for comparison. An outer transaction invoking quota-enabled posting must explicitly run at READ COMMITTED. There is no quota counter to update or reconcile.

Only completed, non-reversal outgoing transfers count. Inbound transfers, failed or rolled-back attempts, and pending funding requests do not count. The customer API does not create pending transfers. Withdrawal holds still reduce available balance. A trusted reversal neither consumes quota nor removes the original charge. Replays return the original receipt, with no new risk evaluation, even after midnight or after thresholds change.

Limit violations return HTTP 422 and a JSON `code` and `message`. Codes are `TRANSFER_AMOUNT_LIMIT`, `DAILY_TRANSFER_AMOUNT_LIMIT`, and `DAILY_TRANSFER_COUNT_LIMIT`. Insufficient-funds errors retain their existing response. Risk alerts do not reject or freeze accounts. The risk window includes completed outgoing customer transfers from the lower bound through the captured posting timestamp plus the candidate.

## Admin report and operational limits

`GET /api/admin/transfer-risk-alerts?page=0&size=20` requires a JWT with the current `ROLE_ADMIN` role. Results are newest first with an ID tie-breaker. Each item contains an alert ID, transfer reference, sender wallet ID, rule, and timestamp. Wallet identifiers are operational data. Protect the report accordingly. Alert rows omit customer names, emails, descriptions, and credentials.

The [V10 migration](../src/main/resources/db/migration/V10__transfer_risk_alerts.sql) adds the alert table and a uniqueness constraint per transfer and rule. Alert writes participate in the posting transaction. An alert insertion failure rolls back the transfer and its financial effects. Existing ledger and reconciliation rules stay the same.

The rules describe simple activity patterns and can generate false positives. Failed attempts are outside the current risk report. There is no automatic investigation, freeze, or historical backfill. Quota and risk queries scan eligible outgoing rows under the lock, so busy wallets and larger windows can increase transaction latency.

[Transfer policy](../src/main/java/finwallet_api/transfer/risk/TransferPolicy.java) · [Usage and alert repository](../src/main/java/finwallet_api/transfer/risk/TransferRiskRepository.java)
