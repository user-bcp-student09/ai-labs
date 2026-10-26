import base64
import hashlib
import hmac
import json
import time
from pathlib import Path
from typing import Mapping

import pytest
from fastapi.testclient import TestClient

from app.main import create_app

JWT_TEST_SECRET = "test-only-not-a-real-secret-key-32bytes"
TOKEN_LIFETIME_SECONDS = 3600


@pytest.fixture(autouse=True)
def set_test_token_secret(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("AUTH_TOKEN_SECRET", JWT_TEST_SECRET)


def token(
    username: str = "student",
    role: str = "STUDENT",
    *,
    issuer: str = "ai-labs-auth",
    audience: str = "ai-labs-api",
    algorithm: str = "HS256",
    issued_at: int | None = None,
    expires_at: int | None = None,
) -> str:
    token_issued_at = issued_at if issued_at is not None else int(time.time())
    header = {"alg": algorithm, "typ": "JWT"}
    claims = {
        "iss": issuer,
        "sub": username,
        "role": role,
        "aud": audience,
        "iat": token_issued_at,
        "exp": expires_at if expires_at is not None else token_issued_at + TOKEN_LIFETIME_SECONDS,
    }
    return signed_token(header, claims)


def signed_token(
    header: dict[str, str] | str,
    claims: dict[str, str | int] | str,
) -> str:
    encoded_header = encode_segment(header)
    encoded_claims = encode_segment(claims)
    signing_input = f"{encoded_header}.{encoded_claims}"
    signature = hmac.new(
        JWT_TEST_SECRET.encode(),
        signing_input.encode(),
        hashlib.sha256,
    ).digest()
    encoded_signature = base64.urlsafe_b64encode(signature).rstrip(b"=").decode()
    return f"{signing_input}.{encoded_signature}"


def encode_segment(value: Mapping[str, str | int] | str) -> str:
    json_value = value if isinstance(value, str) else json.dumps(value, separators=(",", ":"))
    encoded = json_value.encode()
    return base64.urlsafe_b64encode(encoded).rstrip(b"=").decode()


def headers(username: str = "student", role: str = "STUDENT") -> dict[str, str]:
    return {"Authorization": f"Bearer {token(username, role)}"}


def test_lists_seeded_tasks(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.get("/api/tasks", headers=headers())
    assert response.status_code == 200
    assert len(response.json()) == 2


def test_creates_task_and_summary(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        created = client.post(
            "/api/tasks",
            headers=headers(),
            json={"title": "Add tests", "description": "Improve coverage", "status": "TODO", "priority": "HIGH"},
        )
        summary = client.get("/api/summary", headers=headers())
    assert created.status_code == 201
    assert created.json()["owner"] == "student"
    assert summary.json()["total"] == 3


def test_uploads_requirement(tmp_path: Path) -> None:
    upload_dir = tmp_path / "uploads"
    app = create_app(str(tmp_path / "workshop.db"), str(upload_dir))
    with TestClient(app) as client:
        response = client.post(
            "/api/requirements",
            headers=headers(),
            files={"file": ("requirements.txt", b"Login must be accessible", "text/plain")},
        )
    assert response.status_code == 201
    assert (upload_dir / "requirements.txt").exists()


def test_rejects_invalid_token(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.get("/api/tasks", headers={"Authorization": "Bearer invalid"})
    assert response.status_code == 401
    assert response.json()["detail"] == "Invalid token"


@pytest.mark.parametrize(
    "invalid_token",
    [
        token(issuer="untrusted-issuer"),
        token(audience="another-service"),
        token(algorithm="none"),
        token(issued_at=int(time.time()) + 60),
        token(expires_at=int(time.time()) - 1),
    ],
)
def test_rejects_jwt_with_invalid_claims(
    invalid_token: str,
    tmp_path: Path,
) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.get(
            "/api/tasks",
            headers={"Authorization": f"Bearer {invalid_token}"},
        )
    assert response.status_code == 401
    assert response.json()["detail"] == "Invalid token"


def test_rejects_jwt_with_altered_signature(tmp_path: Path) -> None:
    signed_token = token()
    signing_input, signature = signed_token.rsplit(".", 1)
    altered_signature = ("A" if signature[0] != "A" else "B") + signature[1:]
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.get(
            "/api/tasks",
            headers={"Authorization": f"Bearer {signing_input}.{altered_signature}"},
        )
    assert response.status_code == 401
    assert response.json()["detail"] == "Invalid token"


def test_rejects_jwt_with_duplicate_claims(tmp_path: Path) -> None:
    current_time = int(time.time())
    duplicate_claims = (
        '{"iss":"ai-labs-auth","iss":"ai-labs-auth","aud":"ai-labs-api",'
        f'"sub":"student","role":"STUDENT","iat":{current_time},'
        f'"exp":{current_time + TOKEN_LIFETIME_SECONDS}' + "}"
    )
    duplicate_token = signed_token(
        {"alg": "HS256", "typ": "JWT"},
        duplicate_claims,
    )
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.get(
            "/api/tasks",
            headers={"Authorization": f"Bearer {duplicate_token}"},
        )
    assert response.status_code == 401
    assert response.json()["detail"] == "Invalid token"


def test_rejects_oversized_jwt(tmp_path: Path) -> None:
    oversized_token = "A" * 9000
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.get(
            "/api/tasks",
            headers={"Authorization": f"Bearer {oversized_token}"},
        )
    assert response.status_code == 401
    assert response.json()["detail"] == "Invalid token"


def test_missing_token_secret_is_reported_as_server_error(
    monkeypatch: pytest.MonkeyPatch,
    tmp_path: Path,
) -> None:
    monkeypatch.delenv("AUTH_TOKEN_SECRET")
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app, raise_server_exceptions=False) as client:
        response = client.get("/api/tasks", headers=headers())
    assert response.status_code == 500
