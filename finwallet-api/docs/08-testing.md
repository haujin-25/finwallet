# Testing guide

Use Java 21 and the [Maven Wrapper](../mvnw). Unit and standalone MVC tests exercise code with mocks. Spring integration tests use real PostgreSQL, Flyway migrations, Spring security filters and transactions. Use a disposable test database. Integration fixtures insert and remove records, and some deliberately create reconciliation discrepancies. Never point the suite at a shared or production database.

After provisioning the database and exporting credentials as in [Getting started](01-getting-started.md):

```sh
export SPRING_DATASOURCE_URL='jdbc:postgresql://127.0.0.1:5432/finwallet_test'
./mvnw test
./mvnw -Dtest=TransferConcurrencyTest test
./mvnw -Dtest=FundingIntegrationTest,DevFundingSecurityTest test
./mvnw '-Dtest=FundingIntegrationTest#registeredUsersCanSettleRm100AndReplayAnRm1050Transfer' test
```

## What the suites check

| Test | What to observe |
| --- | --- |
| [AuthServiceTest](../src/test/java/finwallet_api/auth/AuthServiceTest.java), [JwtServiceTest](../src/test/java/finwallet_api/common/security/JwtServiceTest.java) | Credential verification and token claim/signature checks. |
| [AuthIntegrationTest](../src/test/java/finwallet_api/auth/AuthIntegrationTest.java), [SecurityIntegrationTest](../src/test/java/finwallet_api/common/security/SecurityIntegrationTest.java) | Login, bearer handling, expired/tampered tokens, public registration. |
| [WalletRegistrationRollbackIntegrationTest](../src/test/java/finwallet_api/wallet/WalletRegistrationRollbackIntegrationTest.java), [WalletControllerIntegrationTest](../src/test/java/finwallet_api/wallet/WalletControllerIntegrationTest.java) | Rollback and per-account wallet ownership. |
| [TransferControllerValidationTest](../src/test/java/finwallet_api/transfer/TransferControllerValidationTest.java), [TransferServiceTest](../src/test/java/finwallet_api/transfer/TransferServiceTest.java) | Request validation, amount types, service rules and safe error mapping. |
| [TransferIntegrationTest](../src/test/java/finwallet_api/transfer/TransferIntegrationTest.java) | Posting changes both wallets, journals balance, same-key replay returns the original reference, changed payload conflicts, errors roll back. |
| [TransferPolicyIntegrationTest](../src/test/java/finwallet_api/transfer/risk/TransferPolicyIntegrationTest.java), [TransferPolicyTest](../src/test/java/finwallet_api/transfer/risk/TransferPolicyTest.java) | Quota limits, rollover, concurrent outgoing posts and reserves, replay, reversals, alert rollback and admin authorization. Unit tests cover exact totals and daylight-saving boundaries. |
| [TransferConcurrencyTest](../src/test/java/finwallet_api/transfer/TransferConcurrencyTest.java) | Parallel debits cannot overspend. Same-key calls replay once, changed descriptions conflict, and opposite directions complete with stable lock ordering in the tested scenarios. |
| [FundingControllerTest](../src/test/java/finwallet_api/funding/FundingControllerTest.java), [FundingSettlementServiceTest](../src/test/java/finwallet_api/funding/FundingSettlementServiceTest.java) | Strict DTOs, state transitions, replay, failure and timeout. |
| [FundingIntegrationTest](../src/test/java/finwallet_api/funding/FundingIntegrationTest.java) | Real persistence, balanced legs, competing settlements and withdrawals, rollback, and the registered-user deposit-to-transfer path. |
| [DevFundingSecurityTest](../src/test/java/finwallet_api/funding/dev/DevFundingSecurityTest.java) | Profile/flag gating, validated outcomes, local token and remote-address checks. |
| [TransferHistoryIntegrationTest](../src/test/java/finwallet_api/transfer/TransferHistoryIntegrationTest.java), [TransferReversalIntegrationTest](../src/test/java/finwallet_api/transfer/TransferReversalIntegrationTest.java) | Owner-scoped pagination and trusted compensating postings. |
| [WalletAdministrationSecurityTest](../src/test/java/finwallet_api/admin/WalletAdministrationSecurityTest.java), [WalletAdministrationIntegrationTest](../src/test/java/finwallet_api/admin/WalletAdministrationIntegrationTest.java), [LedgerReconciliationIntegrationTest](../src/test/java/finwallet_api/reconciliation/LedgerReconciliationIntegrationTest.java) | Role checks, audited status updates, and detection of mismatches. |

The RM100-to-RM10.50 acceptance test registers users, settles a 10,000-sen deposit, posts and replays a 1,050-sen transfer, and asserts 8,950/1,050-sen wallet balances, one transfer, and balanced funding and transfer journals. It also checks that reconciliation reports no new discrepancies. Targeted regressions verify that failed transfer rows cannot mask balance mismatches and that entries attached to pending or failed funding still trigger diagnostics without changing posted totals. The manual guide uses a separate RM30 scenario.

Inspect `target/surefire-reports/` for the first context error and per-test results. A database startup error means test bodies did not reach their assertions. Concurrency claims require a passing PostgreSQL concurrency suite. The inspected suites exercise local PostgreSQL behavior and simulator outcomes. They do not establish production load limits, external provider integration, multi-region recovery, or full browser end-to-end coverage.

For manual settlement verification, follow [wallet funding](wallet-funding.md), confirm a PENDING request has no journal, settle it, then check the COMPLETED result and equal journal legs using read-only SQL.
