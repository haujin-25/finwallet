# FinWallet API

FinWallet is a fintech backend engineering portfolio project. It models a MYR wallet system where registered users can move test funds and engineers can trace the resulting balance changes through journals, history, and reconciliation.

## What works

- Registration creates one zero-balance wallet in the same database transaction. Login issues a signed JWT.
- Authenticated users can read their wallet, transfer integer sen, and view transfer history or broader wallet activity.
- Deposits and withdrawals create pending simulated funding operations. Trusted settlement posts the accounting entries. An opt-in local route and script support developer testing.
- Administrators can inspect wallets, freeze or unfreeze them, read audit history, and run read-only reconciliation.
- Trusted Java callers can reverse a completed transfer through a compensating posting. There is no public reversal endpoint.

## Stack and design

Java 21, Spring Boot 4.1.2, Spring MVC, Bean Validation, Spring Security, BCrypt, JJWT 0.12.6, Spring Data JPA/Hibernate, PostgreSQL, Flyway, and the Maven Wrapper. Controllers map HTTP requests to DTOs, services enforce business rules and transaction boundaries, and repositories persist or query data. PostgreSQL constraints and row locks complement application-level validation and idempotency.

## Quick start

From `finwallet-api/`, install Java 21 and PostgreSQL, provision the local database, and configure `JWT_SECRET` and the datasource values using the [setup guide](docs/01-getting-started.md) and [.env.example](.env.example). Then run:

```sh
./mvnw spring-boot:run
curl -i http://localhost:8080/api/wallets/me
```

An unauthenticated `401` from that protected route confirms that HTTP and Spring Security are responding. The setup guide walks through an authenticated check and a complete Alice-to-Bob transfer.

## Handbook

| Guide | Topic |
| --- | --- |
| [01 Getting started](docs/01-getting-started.md) | macOS setup and the RM100-to-RM30 walkthrough |
| [02 Configuration](docs/02-configuration.md) | Environment, profiles, and secrets |
| [03 API reference](docs/03-api-reference.md) | All backend routes and wire contracts |
| [04 Architecture](docs/04-architecture.md) | Layers, Spring beans, request flow |
| [05 Database design](docs/05-database-design.md) | Tables, constraints, relationships, migrations |
| [06 Financial workflows](docs/06-financial-workflows.md) | Accounting, retry, locking, and settlement |
| [07 Security](docs/07-security.md) | Authentication, roles, and environment isolation |
| [08 Testing](docs/08-testing.md) | Unit and PostgreSQL integration suites |
| [09 Troubleshooting](docs/09-troubleshooting.md) | Startup, database, and API failures |
| [10 Engineering concepts](docs/10-engineering-concepts.md) | Study notes, exercises, and interview prompts |

## Project status

The backend is a local learning system. Funding uses a deterministic simulator. Real payment-provider integration, external reconciliation, token refresh, and public reversal workflows are future work. The frontend lives in a separate repository. The API reference covers backend capabilities, including operations with no corresponding frontend page.
