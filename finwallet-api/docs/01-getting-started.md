# Getting started on macOS

Run the commands from the backend repository unless a step says otherwise. Use a fresh local PostgreSQL database and disposable accounts so the expected balances start at zero.

## Install tools and prepare the database

With Homebrew installed:

```sh
brew install openjdk@21 postgresql@16 jq python
export JAVA_HOME="$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$(brew --prefix postgresql@16)/bin:$PATH"
java -version
python3 --version
./mvnw -version
brew services start postgresql@16
pg_isready -h 127.0.0.1 -p 5432
psql -d postgres
```

If you already use Postgres.app or another local PostgreSQL installation, start that instance instead. The commands below assume a local PostgreSQL administrator and a database/role that do not exist yet. Choose a local password when `\password` prompts.

```sql
CREATE ROLE finwallet_user LOGIN;
\password finwallet_user
CREATE DATABASE finwallet OWNER finwallet_user;
\q
```

If the role already exists, reuse its credentials. Verify the connection with `psql -h 127.0.0.1 -U finwallet_user -d finwallet -c 'SELECT current_database();'`.

## Configure and start Spring

Use `cp -n .env.example .env` to create a template only when `.env` is absent. Edit your local `.env` to set the database password and a signing key generated with `openssl rand -hex 64`. For this walkthrough set `FINWALLET_DEV_FUNDING_ENABLED=true` and add a separate `FINWALLET_DEV_FUNDING_TOKEN` generated with `openssl rand -hex 32`. Keep `SPRING_PROFILES_ACTIVE=local` and the loopback settings from the template.

In terminal 1:

```sh
set -a
source ./.env
set +a
./mvnw spring-boot:run
```

Flyway applies the migrations on startup. Hibernate validates that the mapped schema matches. Wait for Spring's startup message. Press Ctrl-C in terminal 1 to stop it. Restart after configuration changes. Stop a Homebrew-managed PostgreSQL service with `brew services stop postgresql@16` when no other application needs it.

## Alice funds RM100 and sends Bob RM30

In terminal 2, from the backend repository:

```sh
set -a
source ./.env
set +a
API='http://127.0.0.1:8080/api'
curl -i "$API/wallets/me"
```

The protected wallet request should return `401` while signed out. The following commands use fictitious local accounts. Use a new database or a different alias if rerunning the walkthrough. Passwords are read without echo in macOS zsh and remain in your shell rather than command history.

```zsh
set -o pipefail
ALICE_EMAIL=alice@example.test
BOB_EMAIL=bob@example.test
read -rs 'ALICE_PASSWORD?Choose Alice test password (8–72 characters): '
printf '\n'
read -rs 'BOB_PASSWORD?Choose Bob test password (8–72 characters): '
printf '\n'

register() {
  local email=$1 name=$2 password=$3
  jq -nc --arg email "$email" --arg fullName "$name" --rawfile password /dev/fd/3 \
    '{email:$email,fullName:$fullName,password:($password|rtrimstr("\n"))}' 3<<<"$password" |
    curl --fail-with-body -sS "$API/users/register" \
      -H 'Content-Type: application/json' --data-binary @-
}
login() {
  local email=$1 password=$2
  jq -nc --arg email "$email" --rawfile password /dev/fd/3 \
    '{email:$email,password:($password|rtrimstr("\n"))}' 3<<<"$password" |
    curl --fail-with-body -sS "$API/auth/login" \
      -H 'Content-Type: application/json' --data-binary @- |
    jq -er '.accessToken'
}
register "$ALICE_EMAIL" Alice "$ALICE_PASSWORD" | jq .
register "$BOB_EMAIL" Bob "$BOB_PASSWORD" | jq .
ALICE_JWT=$(login "$ALICE_EMAIL" "$ALICE_PASSWORD")
BOB_JWT=$(login "$BOB_EMAIL" "$BOB_PASSWORD")
unset ALICE_PASSWORD BOB_PASSWORD

curl --fail-with-body -sS "$API/wallets/me" -H "Authorization: Bearer $ALICE_JWT" | jq .
./scripts/dev-fund-wallet.sh "$ALICE_EMAIL" 100

TRANSFER_KEY=$(uuidgen)
curl --fail-with-body -sS "$API/transfers" \
  -H "Authorization: Bearer $ALICE_JWT" -H 'Content-Type: application/json' \
  --data "$(jq -nc --arg to "$BOB_EMAIL" --arg key "$TRANSFER_KEY" \
    '{recipientEmail:$to,amount:3000,idempotencyKey:$key,description:"Local test transfer"}')" | jq .

for JWT in "$ALICE_JWT" "$BOB_JWT"; do
  curl --fail-with-body -sS "$API/wallets/me" -H "Authorization: Bearer $JWT" |
    jq '{balance,availableBalance,currency}'
  curl --fail-with-body -sS "$API/transfers/me?page=0&size=20" -H "Authorization: Bearer $JWT" | jq .
  curl --fail-with-body -sS "$API/wallets/me/activity?page=0&size=20" -H "Authorization: Bearer $JWT" | jq .
done
unset ALICE_JWT BOB_JWT JWT
```

The funding script prompts again for Alice’s password. Its input `100` means **RM100.00**. The underlying deposit request sends `10000` **sen**. The transfer API accepts `3000` **sen** for RM30.00. The expected balances are Alice `7000` and Bob `3000` sen. The same `transferReference` appears as `OUTGOING` for Alice and `INCOMING` for Bob. Alice’s wallet activity additionally shows the completed deposit. Tokens expire after the configured lifetime, so log in again if needed.

If funding is interrupted, repeat the script with the same email, amount, database, and `--retry '<printed-key>'`. For a transfer replay, reuse the original `TRANSFER_KEY` and exact body. A new key represents a new operation.

## Inspect the journal in PostgreSQL

Use the reference returned by the transfer. Connect with
`psql -h 127.0.0.1 -U finwallet_user -d finwallet` and run this read-only query:

```sql
\set transfer_ref 'replace-with-the-returned-reference'
SELECT t.reference, e.entry_type, u.email, e.amount, e.currency
FROM ledger_entries e
JOIN transfers t ON t.id = e.transfer_id
JOIN wallets w ON w.id = e.wallet_id
JOIN users u ON u.id = w.user_id
WHERE t.reference = :'transfer_ref'
ORDER BY e.entry_type;
```

Expect one 3,000-sen debit for Alice and one 3,000-sen credit for Bob. The deposit
uses a separate journal described in [Database design](05-database-design.md).
The [PostgreSQL acceptance test](08-testing.md) verifies both journals.

## Run tests

Use a separate PostgreSQL database for integration tests. From `psql` as a local database administrator, run `CREATE DATABASE finwallet_test OWNER finwallet_user;` once. Then:

```sh
SPRING_DATASOURCE_URL='jdbc:postgresql://127.0.0.1:5432/finwallet_test' ./mvnw test
```

See the [testing guide](08-testing.md) for targeted commands and expected coverage.
