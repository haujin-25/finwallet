#!/bin/sh
# Disable inherited tracing before any credential handling.
set +x
exec python3 - "$@" <<'PY'
import argparse
import getpass
import hashlib
import http.client
import json
import os
import re
import sys
import uuid
import warnings
from urllib.error import HTTPError, URLError
from urllib.request import HTTPRedirectHandler, ProxyHandler, Request, build_opener


def fail(message):
    sys.exit(message)


class NoRedirects(HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None


opener = build_opener(ProxyHandler({}), NoRedirects())


def request(path, *, body=None, headers=None):
    data = None if body is None else json.dumps(body).encode()
    request_headers = {"Accept": "application/json, text/plain", **(headers or {})}
    if data is not None:
        request_headers["Content-Type"] = "application/json"
    try:
        with opener.open(Request(base + "/api" + path, data, request_headers), timeout=10) as response:
            return response.status, response.read()
    except HTTPError as error:
        with error:
            try:
                return error.code, error.read()
            except (OSError, http.client.HTTPException):
                return error.code, b""
    except (URLError, OSError, http.client.HTTPException):
        return None, b""


def response_json(label, expected, path, *, body=None, headers=None):
    status, raw = request(path, body=body, headers=headers)
    if status != expected:
        if status is None or status >= 500:
            fail(f"{label}: no confirmed result. The backend may have processed the request.")
        hints = {
            400: "check the request fields",
            401: "check the account credentials or sign in again",
            403: "check your local development token or account access",
            404: "check the local route and funding reference",
            409: "the request conflicts with an existing deposit or terminal outcome",
        }
        fail(f"{label}: HTTP {status}. {hints.get(status, 'Check the local backend configuration')}.")
    try:
        result = json.loads(raw)
    except (ValueError, UnicodeError):
        fail(f"{label}: invalid backend response.")
    if not isinstance(result, dict):
        fail(f"{label}: invalid backend response.")
    return result


parser = argparse.ArgumentParser(prog="dev-fund-wallet.sh", description="Request and settle a test deposit on local FinWallet.")
parser.add_argument("email")
parser.add_argument("amount", help="amount in MYR, for example 100 or 10.50")
parser.add_argument("--retry", metavar="KEY", help="resume the exact deposit printed by a previous run")
args = parser.parse_args()

base = os.environ.get("FINWALLET_DEV_API_URL", "")
url = re.fullmatch(r"http://127\.0\.0\.1:([1-9][0-9]{0,4})", base)
if not url or int(url[1]) > 65535:
    fail("Set FINWALLET_DEV_API_URL to a local URL such as http://127.0.0.1:8080 (no /api suffix).")
token = os.environ.get("FINWALLET_DEV_FUNDING_TOKEN", "")
if len(token) < 32 or not re.fullmatch(r"[\x21-\x7e]+", token):
    fail("Set FINWALLET_DEV_FUNDING_TOKEN to your local backend token. Use at least 32 printable characters without spaces.")
email = args.email.strip().lower()
if len(email) > 255 or not re.fullmatch(r"[^\s@]+@[^\s@]+", email):
    fail("Enter the registered user's email address.")
amount = re.fullmatch(r"([0-9]{1,17})(?:\.([0-9]{1,2}))?", args.amount)
if not amount:
    fail("Enter a positive MYR amount with up to two decimal places, for example 100 or 10.50.")
sen = int(amount[1]) * 100 + int((amount[2] or "").ljust(2, "0"))
if not 0 < sen <= 2**63 - 1:
    fail("Amount must fit in a positive signed 64-bit integer in sen.")

description = "Local developer wallet funding"
intent = json.dumps([base, email, sen, description]).encode()
fingerprint = hashlib.sha256(intent).hexdigest()[:24]
if args.retry and not re.fullmatch(r"dev-fund-[0-9a-f]{32}-" + fingerprint, args.retry):
    fail("Retry key does not match this backend, account, amount, or deposit description.")
key = args.retry or f"dev-fund-{uuid.uuid4().hex}-{fingerprint}"

dev_headers = {"X-Dev-Funding-Token": token}
# An invalid outcome must be rejected by the enabled controller before settlement is called.
status, body = request("/dev/funding/preflight/settle", body={"outcome": "INVALID"}, headers=dev_headers)
if status != 400 or not body.startswith(b"outcome:"):
    fail(f"Development settlement preflight failed ({'connection error' if status is None else f'HTTP {status}'}). "
         "Check the loopback URL, local/test profile, enabled flag and development token.")

try:
    with warnings.catch_warnings():
        warnings.simplefilter("error", getpass.GetPassWarning)
        password = getpass.getpass("FinWallet test account password: ")
except (OSError, EOFError, KeyboardInterrupt, getpass.GetPassWarning):
    fail("A terminal with hidden password input is required.")
if not password:
    fail("A password is required.")
login = response_json("Login", 200, "/auth/login", body={"email": email, "password": password})
del password
jwt = login.get("accessToken")
if login.get("tokenType") != "Bearer" or not isinstance(jwt, str) or not re.fullmatch(r"[\w.-]+", jwt, re.ASCII):
    fail("Login: invalid bearer token response.")
customer_headers = {"Authorization": f"Bearer {jwt}"}

print(f"Deposit key: {key}", flush=True)
print(f"If interrupted, rerun the same command with --retry {key}.", flush=True)
deposit = response_json("Deposit", 201, "/deposits", headers=customer_headers,
                        body={"amount": sen, "idempotencyKey": key, "description": description})
reference = deposit.get("reference")
if not isinstance(reference, str) or not re.fullmatch(r"DEP-[A-Za-z0-9-]+", reference):
    fail("Deposit: invalid reference in backend response. Resume with the same retry key.")
print(f"Deposit reference: {reference}", flush=True)
settled = response_json("Settlement", 200, f"/dev/funding/{reference}/settle",
                         headers=dev_headers, body={"outcome": "SUCCESS"})
if settled.get("reference") != reference or settled.get("status") != "COMPLETED":
    fail("Settlement: the backend did not confirm completion. Resume with the same retry key.")
print("Settlement status: COMPLETED", flush=True)

wallet = response_json("Wallet balance", 200, "/wallets/me", headers=customer_headers)
if wallet.get("currency") != "MYR" or any(
    type(wallet.get(field)) is not int or wallet[field] < 0 for field in ("balance", "availableBalance")
):
    fail("Wallet balance: invalid MYR balance response.")
for label, field in (("Wallet balance", "balance"), ("Available balance", "availableBalance")):
    ringgit, cents = divmod(wallet[field], 100)
    print(f"{label}: RM{ringgit:,}.{cents:02} ({wallet[field]} sen)")
PY
