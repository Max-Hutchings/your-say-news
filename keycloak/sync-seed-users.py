#!/usr/bin/env python3
"""Reconcile the realm export's seeded users into an existing development realm."""

import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path


def required_env(name: str) -> str:
    value = os.environ.get(name)
    if not value:
        raise RuntimeError(f"Missing required environment variable: {name}")
    return value


def request_json(
    url: str,
    *,
    data: bytes,
    content_type: str,
    authorization: str | None = None,
) -> dict:
    headers = {"Content-Type": content_type}
    if authorization:
        headers["Authorization"] = authorization

    request = urllib.request.Request(url, data=data, headers=headers, method="POST")
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            body = response.read()
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"Keycloak returned HTTP {error.code}: {detail}") from error

    return json.loads(body) if body else {}


def main() -> None:
    keycloak_url = os.environ.get("KEYCLOAK_URL", "http://keycloak:8080").rstrip("/")
    realm_export_path = Path(
        os.environ.get("KEYCLOAK_REALM_EXPORT", "/config/realm-export.json")
    )

    realm_export = json.loads(realm_export_path.read_text(encoding="utf-8"))
    realm_name = realm_export.get("realm")
    users = realm_export.get("users")
    if not realm_name or not isinstance(users, list) or not users:
        raise RuntimeError("Realm export must contain a realm name and at least one seeded user")

    token_form = urllib.parse.urlencode(
        {
            "client_id": "admin-cli",
            "grant_type": "password",
            "username": required_env("KEYCLOAK_ADMIN"),
            "password": required_env("KEYCLOAK_ADMIN_PASSWORD"),
        }
    ).encode("utf-8")
    token_response = request_json(
        f"{keycloak_url}/realms/master/protocol/openid-connect/token",
        data=token_form,
        content_type="application/x-www-form-urlencoded",
    )
    access_token = token_response.get("access_token")
    if not access_token:
        raise RuntimeError("Keycloak admin token response did not contain an access token")

    import_payload = json.dumps(
        {"ifResourceExists": "OVERWRITE", "users": users}
    ).encode("utf-8")
    result = request_json(
        f"{keycloak_url}/admin/realms/{urllib.parse.quote(realm_name, safe='')}/partialImport",
        data=import_payload,
        content_type="application/json",
        authorization=f"Bearer {access_token}",
    )

    added = result.get("added", 0)
    overwritten = result.get("overwritten", 0)
    print(
        f"Reconciled {len(users)} seeded users into realm '{realm_name}' "
        f"(added={added}, overwritten={overwritten})."
    )


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, RuntimeError) as error:
        print(f"Seeded-user reconciliation failed: {error}", file=sys.stderr)
        raise SystemExit(1) from error
