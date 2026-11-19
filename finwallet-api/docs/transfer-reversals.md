# Transfer lifecycle and reversals

## Synchronous lifecycle

Posting a transfer records a new COMPLETED operation. The transfer row, both wallet balance changes, and its debit and credit become visible together at commit. Validation failures and transaction rollbacks leave no operation row. The existing schema still recognizes the legacy FAILED status. Current posting code creates neither FAILED nor PENDING rows and exposes no status transitions.

A client might lose the HTTP response after a successful commit. Retry the same `POST /api/transfers` request with the same sender-scoped idempotency key. Under the sender wallet lock, the service returns the recorded result before checking funds again. Reuse of a key with a different recipient, amount, or description returns 409. If the original attempt rolled back, a retry can post normally. The contract provides idempotent financial effects. HTTP delivery can fail or repeat.

## Internal reversal

Trusted application code can call `TransferService.reverse(originalReference)` outside a wallet-writing transaction. Spring opens a fresh READ COMMITTED transaction and persistence context. The service checks that the reference identifies a completed original transfer, then acquires both pessimistic wallet locks in ascending wallet-ID order, just as normal transfers do. A waiting request checks for an existing reversal after acquiring the locks and can see the preceding commit.

The original reference is the entire retry contract. The amount, currency, and parties come from the original row, so there is no conflicting reversal payload. A different eligible original reference identifies a different operation. Repeating the original reference after success returns its existing reversal, including after the reversal debit wallet spends its funds. Missing references and attempts to reverse a reversal or a legacy FAILED row are rejected. Wallet status, original currency, available balance, and credit overflow are checked before posting. An insufficient-funds failure leaves the original untouched and can be retried if funds return.

V7 adds a nullable, unique self-referencing foreign key, `transfers.reversal_of_transfer_id`. It allows at most one reversal row per original and rejects self-links. Ordinary rows retain a required sender-scoped idempotency key. Reversal rows use the original link as their key and have a null client key, so an internal reversal cannot occupy a client's key. Existing rows require no backfill.

A reversal inserts a new COMPLETED row, debits the original recipient, credits the original sender, and writes its own debit and credit. The original row and its entries are preserved. Posting failures, including ledger constraint failures and lock errors, propagate out of the transaction and roll it back. HTTP callers receive a generic response for persistence errors.

## History, reconciliation, and authorization

History adds `operationType` (`TRANSFER` or `REVERSAL`). Reversals also include `reversalOfReference`, the original public reference. Direction remains relative to the caller's wallet.

Reconciliation includes reversal entries in the signed wallet totals and checks their ledger pairs as usual. It also checks the reversal link, original eligibility, swapped wallets, original amount and currency, and duplicate links. The unique constraint blocks duplicates during normal database writes. Cross-row financial mismatches are checked by the application and reconciliation.

The reversal operation has no HTTP route. The wallet administration role grants no reversal authority. A future administrative route should require an independently managed server-side reversal permission and audit actor, original reference, reason, and outcome. It should never treat transfer ownership as reversal permission.

Run the complete suite with a configured PostgreSQL test database.

```bash
./mvnw test
```
