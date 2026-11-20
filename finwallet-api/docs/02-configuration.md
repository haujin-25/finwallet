# Configuration

Spring Boot reads environment variables from the process that starts it. The backend does not automatically load `.env`. The funding script reads its own environment as well. Use [.env.example](../.env.example) as a shell-compatible template and keep real values in the ignored `.env`.

| Variable | Required and purpose | Example | Reader and when to reload |
| --- | --- | --- | --- |
| `JWT_SECRET` | Required for normal backend startup. HMAC signing key. The source defines no production fallback. | Generate with `openssl rand -hex 64`. | Spring. Restart. A change invalidates prior tokens. |
| `JWT_EXPIRATION_SECONDS` | Optional. Token lifetime in seconds. Default 900. | `900` | Spring. Restart. Existing tokens keep their signed expiry. |
| `SPRING_DATASOURCE_URL` | Set for a different database. Default targets local `finwallet` on 5432. | `jdbc:postgresql://127.0.0.1:5432/finwallet_test` | Spring and Spring integration tests. Restart or rerun tests. |
| `SPRING_DATASOURCE_USERNAME` | Override the configured local role as needed. | `finwallet_user` | Spring and integration tests. Restart or rerun. |
| `SPRING_DATASOURCE_PASSWORD` | Set to the actual password for your local database role. | `REPLACE_WITH_LOCAL_DATABASE_PASSWORD` | Spring and integration tests. Restart or rerun. |
| `SPRING_PROFILES_ACTIVE` | Optional for normal API. Explicit `local` or `test` is required for dev settlement. | `local` | Spring. Restart. |
| `FINWALLET_DEV_FUNDING_ENABLED` | Optional. Explicit `true` enables the dev route subject to the profile gate. Default is off. | `false` | Spring. Restart. |
| `FINWALLET_DEV_FUNDING_TOKEN` | Required to use the dev endpoint and script. Independent token of at least 32 characters. The script expects printable, non-space ASCII. | Generate with `openssl rand -hex 32`. | Spring and script. Restart Spring and update the script shell. |
| `FINWALLET_DEV_API_URL` | Required by the script. Explicit literal IPv4 loopback URL and port, without `/api` or a trailing slash. | `http://127.0.0.1:8080` | Script. Set before the next run. |
| `SERVER_ADDRESS` | Optional Spring Boot listener bind address. Use loopback for local funding. | `127.0.0.1` | Spring. Restart. |
| `SERVER_PORT` | Optional HTTP port override. | `8080` | Spring. Restart. Match the script URL. |
| `SERVER_FORWARD_HEADERS_STRATEGY` | Spring Boot forwarding policy. Use `none` for the direct local listener. | `none` | Spring. Restart. |
| `JAVA_HOME` | Set when the shell selects the wrong JDK. | `/path/to/jdk-21/Contents/Home` | Maven Wrapper/JVM launch. Restart or rerun. |

Datasource, server, and profile settings use Spring property binding. The script reads only `FINWALLET_DEV_API_URL` and `FINWALLET_DEV_FUNDING_TOKEN`. The tests load a separate test JWT signing property. `spring.jpa.hibernate.ddl-auto=validate`, SQL logging, and `spring.jpa.open-in-view=false` are in [application.properties](../src/main/resources/application.properties). Flyway owns schema changes.

## Export settings to child processes

```sh
set -a
source ./.env
set +a
```

`source` evaluates trusted shell assignments. `set -a` exports subsequent assignments to child processes, and `set +a` stops automatic exports. Use `NAME='value'` without spaces around `=`. Quote whitespace and shell metacharacters. Start Spring from the same configured shell. A running JVM retains its startup environment. Re-sourcing a file in another terminal affects only future processes in that terminal.

Generate signing and development tokens separately. Keep them away from source control, logs, shell traces, and browser settings. A placeholder is not a configured key. Avoid printing the active environment to debug credentials.

## Transfer policy

Optional limits and risk thresholds are configured under `finwallet.transfer`. See [Transfer limits and risk controls](transfer-limits-and-risk.md) for every property, a `SPRING_APPLICATION_JSON` example, and restart guidance.

## Profiles and settlement

The dev controller and its security chain require the flag and a nonempty active-profile set containing only `local` and/or `test`. An unset active profile, a default-only local profile, or a mixed set such as `local,production` disables the controller. The `test` profile alone does not enable it. The route also requires loopback and the separate token.

The token can settle any known funding reference in the connected database. Use an isolated test database and direct loopback access. Keep the flag disabled on shared or production deployments. See [the local funding guide](wallet-funding.md) for the setup and [security](07-security.md) for boundaries.
