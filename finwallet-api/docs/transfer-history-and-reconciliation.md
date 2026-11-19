# Transfer history and ledger reconciliation

## Transfer history

`GET /api/transfers/me` requires the same JWT Bearer authentication as other wallet operations. The wallet comes from the authenticated principal. The request has no wallet or user ID selector.

| Parameter | Values |
| --- | --- |
| `page` | Zero-based integer, default `0`. The resulting offset must fit in a signed 32-bit integer. |
| `size` | Integer from `1` to `100`, default `20`. |
| `direction` | Optional `INCOMING` or `OUTGOING`, relative to the caller's wallet. |
| `from` | Optional inclusive timestamp. |
| `to` | Optional exclusive timestamp. Must be later than `from` when both are present. |

Timestamps use ISO-8601 with an explicit offset, such as `2026-11-18T10:00:00Z` or `2026-11-18T18:00:00.123456+08:00`. Use years 0001–9999 and up to microsecond precision. Encode a literal plus sign in a URL query string as `%2B`. Invalid filters return HTTP 400.

```json
{
  "items": [
    {
      "transferReference": "TRF-20261118-ABCD1234",
      "direction": "OUTGOING",
      "operationType": "TRANSFER",
      "amount": 2500,
      "currency": "MYR",
      "status": "COMPLETED",
      "description": "Dinner",
      "counterpartyName": "Bob Example",
      "createdAt": "2026-11-18T10:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "hasNext": false
}
```

Reversal entries use `operationType: REVERSAL` and include the original transfer’s public reference in `reversalOfReference`.

The database applies the filters and orders by creation time descending, then transfer ID descending. The projection joins the counterparty wallet and user in the same query and selects the current display name. Spring Data limits results to one page and uses a count query when necessary. The page and count share a database snapshot within a request. Transfers committed between separate page requests can shift offset-based pages, and deep offsets and exact counts grow more expensive with history size.

Flyway V6 replaces the sender- and recipient-wallet indexes with indexes on wallet ID, creation time descending, and transfer ID descending. The leading wallet column still supports wallet-only lookups. The extra columns support time ranges and ordering for a single direction. PostgreSQL may combine indexes and sort when querying both directions. Wider indexes increase storage and write cost. Standard index creation in this migration can block writes on a large transfer table, so schedule it accordingly.

## Ledger reconciliation

Call `LedgerReconciliationService.reconcile()` from trusted application code. The report includes the snapshot timestamp, wallet and transfer counts, discrepancy count and details, and PASS or FAIL. The transfer count covers all transfer statuses. The expected ledger pair applies to completed transfers, and ledger entries linked to other statuses are flagged.

Registration creates wallets at zero. Expected posted balance is zero plus credits minus debits from transfers, reversals, and completed funding in the wallet’s currency. PostgreSQL aggregates in exact integer-valued `numeric` without narrowing to `long`. Pending withdrawals affect available funds through a separate hold. An unrecorded opening balance or out-of-band credit can produce a discrepancy. The report cannot distinguish unrecorded funding from corruption or establish the origin of records loaded outside the application. See [wallet funding and withdrawals](wallet-funding.md) for settlement and reservation checks.

The service runs in its own read-only PostgreSQL REPEATABLE READ transaction. Counting and discrepancy queries see the same committed snapshot while transfers continue. It checks debit and credit cardinality and validates each entry's transfer, wallet, currency, and amount. Database constraints protect positive amounts, recognized types, references, and uniqueness by type. The transfer write path creates the complete pair and balance changes atomically. Reconciliation detects discrepancies within the snapshot. It cannot prove that equal-and-opposite valid-looking history was never deleted or altered, recover an unknown opening balance, or establish the provenance of a transfer. It makes no repairs.

Operators can invoke reconciliation at `GET /api/admin/reconciliation` with a database-backed `ROLE_ADMIN`. A full run scans financial tables and materializes discrepancy details. See the [administrative security policy](administrative-security.md) for limits and access controls.

Run the full suite against a dedicated PostgreSQL test database with the application's datasource settings.

```bash
./mvnw clean test
```
