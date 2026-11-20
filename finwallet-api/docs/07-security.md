# Security design

## Passwords and bearer authentication

Registration hashes passwords using Spring Security’s BCrypt encoder. BCrypt generates a salt and performs deliberately expensive verification, which slows offline guessing if hashes leak. Login checks the stored hash and current account status. Hashes are absent from responses. Keep passwords out of examples, request logs, source control, and shell history.

[JJWT](../src/main/java/finwallet_api/common/security/JwtService.java) signs an email subject, user ID and timestamps using a HMAC key derived from the configured secret bytes. The parser verifies signatures with the expected algorithm for that key and requires expiry and subject. JWT payloads are readable by their holder. Keep private information out of claims and use HTTPS when traffic leaves loopback. The default lifetime is 900 seconds. Rotating the secret invalidates earlier tokens.

The [JWT filter](../src/main/java/finwallet_api/common/security/JwtAuthenticationFilter.java) loads the current user by signed subject, requires ACTIVE state and checks the signed user ID when present. Authorities come from the database. The default chain is stateless and disables CSRF for bearer-based API calls. Admin requests require ROLE_ADMIN at both controller routing and service method enforcement. Registration ignores supplied role fields. V9 grants ROLE_USER by default. Role provisioning is an authorized operator task without a public role-management route.

## Ownership and development funding

Customer wallet, activity, history and funding reads derive the caller from the security principal. Funding lookup also scopes the reference to that wallet. The authenticated email lookup is broader: any authenticated caller can query another user’s basic profile by known email. Consider narrowing this in a future version.

The dev security chain matches the settlement route before the normal JWT chain. Registration is conditional on the explicitly enabled flag and strictly local/test active profiles. A direct loopback request and a separate `X-Dev-Funding-Token` are mandatory. The secret comparison uses `MessageDigest.isEqual`, and configured tokens shorter than 32 characters are rejected. The token can settle any funding reference in the attached database, so keep it out of browser code, logs, repos, proxies, tunnels, and production. Bind to loopback and disable forwarded headers for local runs. [Development security](../src/main/java/finwallet_api/funding/dev/DevFundingSecurityConfig.java)

## Operational limits

Current code has no refresh or revocation endpoints, administrative MFA, provider webhook verification, or rate-limiting policy. Production would need secret rotation, TLS, controlled admin provisioning, login throttling, restricted database grants, and redacted observability. Audit logs record successful admin changes and no-change requests. Rejected attempts have no durable admin audit record. Database operators with write privileges can change records, so app-level append-only behavior alone is insufficient for production audit integrity.

[Security configuration](../src/main/java/finwallet_api/common/security/SecurityConfig.java) · [Administrative security](administrative-security.md) · [Configuration](02-configuration.md)
