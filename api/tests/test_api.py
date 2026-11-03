import base64
import time
from pathlib import Path

import jwt
import pytest
import urllib3
from fastapi.testclient import TestClient

from app.main import create_app

TEST_JWT_SECRET = "isolated-test-signing-key-with-over-sixty-four-bytes-for-local-tests"
TEST_JWT_ISSUER = "test-auth"
TEST_JWT_AUDIENCE = "test-api"
MAX_UPLOAD_BYTES = 5 * 1024 * 1024


@pytest.fixture(autouse=True)
def configure_test_jwt(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("WORKSHOP_JWT_SECRET", TEST_JWT_SECRET)
    monkeypatch.setenv("WORKSHOP_JWT_ISSUER", TEST_JWT_ISSUER)
    monkeypatch.setenv("WORKSHOP_JWT_AUDIENCE", TEST_JWT_AUDIENCE)


def token(username: str = "student", role: str = "STUDENT") -> str:
    raw = f"{username}|{role}|{int(time.time()) + 3600}|WORKSHOP_SECRET_2026"
    return base64.b64encode(raw.encode()).decode()


def signed_token(
    username: str = "student",
    role: str = "STUDENT",
    *,
    issuer: str = TEST_JWT_ISSUER,
    audience: str = TEST_JWT_AUDIENCE,
    expiration: int | None = None,
    secret: str = TEST_JWT_SECRET,
    algorithm: str = "HS256",
) -> str:
    payload = {
        "sub": username,
        "role": role,
        "iss": issuer,
        "aud": audience,
        "exp": expiration if expiration is not None else int(time.time()) + 3600,
    }
    return jwt.encode(payload, secret, algorithm=algorithm)


def signed_headers(**claims: object) -> dict[str, str]:
    return {"Authorization": f"Bearer {signed_token(**claims)}"}


def legacy_headers(username: str = "student", role: str = "STUDENT") -> dict[str, str]:
    return {"Authorization": f"Bearer {token(username, role)}"}


def headers(username: str = "student", role: str = "STUDENT") -> dict[str, str]:
    return signed_headers(username=username, role=role)


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


def test_summary_counts_statuses_and_excludes_closed_high_priority_tasks(
    tmp_path: Path,
) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    additional_tasks = [
        {"title": "Done high", "status": "DONE", "priority": "HIGH"},
        {"title": "Archived high", "status": "ARCHIVED", "priority": "HIGH"},
        {"title": "Open high", "status": "TODO", "priority": "HIGH"},
    ]

    with TestClient(app) as client:
        for task in additional_tasks:
            response = client.post(
                "/api/tasks",
                headers=headers(),
                json=task,
            )
            assert response.status_code == 201
        summary = client.get("/api/summary", headers=headers())

    assert summary.json()["byStatus"] == {
        "TODO": 2,
        "IN_PROGRESS": 1,
        "DONE": 1,
        "ARCHIVED": 1,
    }
    assert summary.json()["highPriorityOpen"] == 2


def test_database_reuse_does_not_duplicate_seed_tasks(tmp_path: Path) -> None:
    database_path = str(tmp_path / "workshop.db")
    upload_path = str(tmp_path / "uploads")
    app = create_app(database_path, upload_path)
    with TestClient(app) as client:
        client.post(
            "/api/tasks",
            headers=headers(),
            json={"title": "Persisted task", "description": "Reopen database"},
        )

    reopened_app = create_app(database_path, upload_path)
    with TestClient(reopened_app) as client:
        response = client.get("/api/tasks", headers=headers())

    assert [task["title"] for task in response.json()] == [
        "Refactor login",
        "Review API",
        "Persisted task",
    ]


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


def test_accepts_signed_jwt_with_configured_claims(
    tmp_path: Path,
    monkeypatch,
) -> None:
    monkeypatch.setenv("WORKSHOP_JWT_SECRET", TEST_JWT_SECRET)
    monkeypatch.setenv("WORKSHOP_JWT_ISSUER", TEST_JWT_ISSUER)
    monkeypatch.setenv("WORKSHOP_JWT_AUDIENCE", TEST_JWT_AUDIENCE)
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))

    with TestClient(app) as client:
        response = client.get("/api/tasks", headers=signed_headers())

    assert response.status_code == 200
    assert {task["owner"] for task in response.json()} == {"student"}


def test_rejects_jwt_with_invalid_signature_or_claims(
    tmp_path: Path,
    monkeypatch,
) -> None:
    monkeypatch.setenv("WORKSHOP_JWT_SECRET", TEST_JWT_SECRET)
    monkeypatch.setenv("WORKSHOP_JWT_ISSUER", TEST_JWT_ISSUER)
    monkeypatch.setenv("WORKSHOP_JWT_AUDIENCE", TEST_JWT_AUDIENCE)
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    invalid_tokens = [
        signed_token(secret=f"{TEST_JWT_SECRET}-different"),
        signed_token(expiration=int(time.time()) - 1),
        signed_token(issuer="unexpected-issuer"),
        signed_token(audience="unexpected-audience"),
        signed_token(role=""),
        signed_token(algorithm="HS384"),
    ]

    with TestClient(app) as client:
        responses = [
            client.get(
                "/api/tasks",
                headers={"Authorization": f"Bearer {invalid_token}"},
            )
            for invalid_token in invalid_tokens
        ]

    assert [response.status_code for response in responses] == [401] * len(invalid_tokens)
    assert {response.json()["detail"] for response in responses} == {"Invalid token"}


def test_parameterizes_task_creation_with_quotes(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app, raise_server_exceptions=False) as client:
        response = client.post(
            "/api/tasks",
            headers=headers(),
            json={
                "title": "Owner's task",
                "description": "A quote: ' remains text",
            },
        )

    assert response.status_code == 201
    assert response.json()["title"] == "Owner's task"
    assert response.json()["description"] == "A quote: ' remains text"


def test_parameterizes_owner_filter_and_ignores_client_owner(
    tmp_path: Path,
) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        injected = client.get(
            "/api/tasks",
            params={"owner": "nobody' OR '1'='1"},
            headers=headers(),
        )
        selected = client.get(
            "/api/tasks",
            params={"owner": "trainer"},
            headers=headers(),
        )
        summary = client.get(
            "/api/summary",
            params={"owner": "trainer"},
            headers=headers(),
        )

    assert [task["owner"] for task in injected.json()] == ["student", "student"]
    assert {task["owner"] for task in selected.json()} == {"student"}
    assert summary.json()["owner"] == "student"
    assert summary.json()["total"] == 2


def test_normalizes_upload_filename_and_rejects_unsupported_extension(
    tmp_path: Path,
) -> None:
    upload_dir = tmp_path / "uploads"
    app = create_app(str(tmp_path / "workshop.db"), str(upload_dir))
    with TestClient(app) as client:
        normalized = client.post(
            "/api/requirements",
            headers=headers(),
            files={"file": ("../outside.txt", b"requirement", "text/plain")},
        )
        windows_normalized = client.post(
            "/api/requirements",
            headers=headers(),
            files={"file": (r"..\windows-outside.txt", b"requirement", "text/plain")},
        )
        rejected = client.post(
            "/api/requirements",
            headers=headers(),
            files={"file": ("payload.exe", b"not allowed", "application/octet-stream")},
        )
        invalid_windows_name = client.post(
            "/api/requirements",
            headers=headers(),
            files={"file": ("CON.txt", b"not allowed", "text/plain")},
        )
        invalid_character_name = client.post(
            "/api/requirements",
            headers=headers(),
            files={"file": ("bad:name.txt", b"not allowed", "text/plain")},
        )

    assert normalized.status_code == 201
    assert normalized.json()["fileName"] == "outside.txt"
    assert Path(normalized.json()["storedAt"]).resolve().is_relative_to(upload_dir.resolve())
    assert (upload_dir / "outside.txt").read_bytes() == b"requirement"
    assert windows_normalized.status_code == 201
    assert windows_normalized.json()["fileName"] == "windows-outside.txt"
    assert (upload_dir / "windows-outside.txt").read_bytes() == b"requirement"
    assert not (tmp_path / "outside.txt").exists()
    assert not (tmp_path / "windows-outside.txt").exists()
    assert rejected.status_code == 400
    assert rejected.json() == {"detail": "Only .txt files are allowed"}
    assert invalid_windows_name.status_code == 400
    assert invalid_windows_name.json() == {"detail": "Invalid file name"}
    assert invalid_character_name.status_code == 400
    assert invalid_character_name.json() == {"detail": "Invalid file name"}
    assert not (upload_dir / "payload.exe").exists()


def test_rejects_upload_larger_than_configured_limit(tmp_path: Path) -> None:
    upload_dir = tmp_path / "uploads"
    app = create_app(str(tmp_path / "workshop.db"), str(upload_dir))
    oversized_content = b"x" * (MAX_UPLOAD_BYTES + 1)

    with TestClient(app) as client:
        response = client.post(
            "/api/requirements",
            headers=headers(),
            files={"file": ("large.txt", oversized_content, "text/plain")},
        )

    assert response.status_code == 413
    assert not (upload_dir / "large.txt").exists()


def test_returns_generic_backend_health_error(
    tmp_path: Path,
    monkeypatch,
) -> None:
    class FailingPoolManager:
        def request(self, method: str, url: str, timeout: float) -> None:
            raise urllib3.exceptions.HTTPError("private connection detail")

    monkeypatch.setattr("urllib3.PoolManager", FailingPoolManager)
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.get("/api/backend-health", headers=headers())

    assert response.status_code == 200
    assert response.json() == {
        "requestedBy": "student",
        "status": 0,
        "body": "Backend unavailable",
    }


def test_validates_task_payload_types(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.post(
            "/api/tasks",
            headers=headers(),
            json={"title": 123, "description": "Invalid title type"},
        )

    assert response.status_code == 422


def test_rejects_legacy_token(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.get("/api/tasks", headers=legacy_headers())

    assert response.status_code == 401
    assert response.json()["detail"] == "Invalid token"


def test_reports_missing_jwt_configuration(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.delenv("WORKSHOP_JWT_SECRET")
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.get("/api/tasks", headers=headers())

    assert response.status_code == 500
    assert response.json()["detail"] == "Authentication is not configured"


def test_owner_filters_are_scoped_to_identity(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        tasks = client.get("/api/tasks", params={"owner": "trainer"}, headers=headers())
        summary = client.get("/api/summary", params={"owner": "trainer"}, headers=headers())

    assert tasks.status_code == 200
    assert [task["owner"] for task in tasks.json()] == ["student", "student"]
    assert summary.json() == {
        "owner": "student",
        "total": 2,
        "byStatus": {"TODO": 1, "IN_PROGRESS": 1},
        "highPriorityOpen": 1,
    }


def test_characterizes_task_defaults_and_response_fields(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.post(
            "/api/tasks",
            headers=headers(),
            json={"title": "Defaults", "description": "Use observed defaults"},
        )

    assert response.status_code == 201
    assert set(response.json()) == {
        "id",
        "title",
        "description",
        "status",
        "owner",
        "priority",
        "createdAt",
    }
    assert response.json()["status"] == "TODO"
    assert response.json()["priority"] == "MEDIUM"
    assert response.json()["owner"] == "student"
    assert response.json()["createdAt"]


def test_task_owner_is_taken_from_identity(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.post(
            "/api/tasks",
            headers=headers(),
            json={
                "title": "Assigned task",
                "description": "Observed owner behavior",
                "owner": "trainer",
            },
        )

    assert response.status_code == 201
    assert response.json()["owner"] == "student"


def test_characterizes_backend_health_success_shape(
    tmp_path: Path,
    monkeypatch,
) -> None:
    class FakeResponse:
        status = 204
        data = b"healthy"

    class FakePoolManager:
        def request(self, method: str, url: str, timeout: float) -> FakeResponse:
            assert method == "GET"
            assert url == "http://auth.test/health"
            assert timeout == 3.0
            return FakeResponse()

    monkeypatch.setenv("AUTH_HEALTH_URL", "http://auth.test/health")
    monkeypatch.setattr("urllib3.PoolManager", FakePoolManager)
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.get("/api/backend-health", headers=headers())

    assert response.status_code == 200
    assert response.json() == {
        "requestedBy": "student",
        "status": 204,
        "body": "healthy",
    }


PROTECTED_ENDPOINTS = [
    pytest.param("GET", "/api/tasks", {}, id="list-tasks"),
    pytest.param("POST", "/api/tasks", {"json": {}}, id="create-task"),
    pytest.param("GET", "/api/summary", {}, id="summary"),
    pytest.param(
        "POST",
        "/api/requirements",
        {"files": {"file": ("requirements.txt", b"text", "text/plain")}},
        id="upload-requirement",
    ),
    pytest.param("GET", "/api/backend-health", {}, id="backend-health"),
]


@pytest.mark.parametrize(("request_method", "path", "request_kwargs"), PROTECTED_ENDPOINTS)
def test_each_endpoint_rejects_missing_authentication(
    tmp_path: Path,
    request_method: str,
    path: str,
    request_kwargs: dict[str, object],
) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.request(request_method, path, **request_kwargs)

    assert response.status_code == 401
    assert response.json() == {"detail": "Invalid token"}


@pytest.mark.parametrize(("request_method", "path", "request_kwargs"), PROTECTED_ENDPOINTS)
def test_each_endpoint_rejects_invalid_authentication(
    tmp_path: Path,
    request_method: str,
    path: str,
    request_kwargs: dict[str, object],
) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.request(
            request_method,
            path,
            headers={"Authorization": "Bearer invalid"},
            **request_kwargs,
        )

    assert response.status_code == 401
    assert response.json() == {"detail": "Invalid token"}


@pytest.mark.parametrize(("request_method", "path", "request_kwargs"), PROTECTED_ENDPOINTS)
def test_each_endpoint_reports_missing_authentication_configuration(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
    request_method: str,
    path: str,
    request_kwargs: dict[str, object],
) -> None:
    monkeypatch.delenv("WORKSHOP_JWT_SECRET")
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.request(
            request_method,
            path,
            headers=headers(),
            **request_kwargs,
        )

    assert response.status_code == 500
    assert response.json() == {"detail": "Authentication is not configured"}


def test_owner_cannot_read_another_users_tasks(
    tmp_path: Path,
) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        tasks = client.get(
            "/api/tasks",
            params={"owner": "student"},
            headers=headers(username="outsider"),
        )
        summary = client.get(
            "/api/summary",
            params={"owner": "student"},
            headers=headers(username="outsider"),
        )

    assert tasks.status_code == 200
    assert tasks.json() == []
    assert summary.status_code == 200
    assert summary.json() == {
        "owner": "outsider",
        "total": 0,
        "byStatus": {},
        "highPriorityOpen": 0,
    }


def test_creates_task_from_empty_payload_defaults(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.post("/api/tasks", headers=headers(), json={})

    assert response.status_code == 201
    assert response.json()["title"] == ""
    assert response.json()["description"] == ""
    assert response.json()["status"] == "TODO"
    assert response.json()["priority"] == "MEDIUM"
    assert response.json()["owner"] == "student"


def test_rejects_task_without_json_body(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.post("/api/tasks", headers=headers())

    assert response.status_code == 422
    assert response.json()["detail"][0]["loc"] == ["body"]


def test_rejects_malformed_task_json(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.post(
            "/api/tasks",
            headers={**headers(), "Content-Type": "application/json"},
            content="{not valid JSON",
        )

    assert response.status_code == 422
    assert response.json()["detail"][0]["loc"] == ["body", 1]


def test_accepts_empty_requirement_file(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.post(
            "/api/requirements",
            headers=headers(),
            files={"file": ("empty.txt", b"", "text/plain")},
        )

    assert response.status_code == 201
    assert response.json()["size"] == 0


def test_accepts_requirement_file_at_exact_size_limit(tmp_path: Path) -> None:
    upload_dir = tmp_path / "uploads"
    app = create_app(str(tmp_path / "workshop.db"), str(upload_dir))
    content = b"x" * MAX_UPLOAD_BYTES

    with TestClient(app) as client:
        response = client.post(
            "/api/requirements",
            headers=headers(),
            files={"file": ("at-limit.txt", content, "text/plain")},
        )

    assert response.status_code == 201
    assert response.json()["size"] == MAX_UPLOAD_BYTES
    assert (upload_dir / "at-limit.txt").stat().st_size == MAX_UPLOAD_BYTES


def test_rejects_requirement_file_without_extension(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.post(
            "/api/requirements",
            headers=headers(),
            files={"file": ("requirements", b"text", "text/plain")},
        )

    assert response.status_code == 400
    assert response.json() == {"detail": "Only .txt files are allowed"}


def test_rejects_requirement_file_without_upload_field(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.post("/api/requirements", headers=headers())

    assert response.status_code == 422
    assert response.json()["detail"][0]["loc"] == ["body", "file"]


def test_backend_health_preserves_non_success_backend_status(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    class UnavailableResponse:
        status = 503
        data = b"maintenance"

    class FailingServicePoolManager:
        def request(
            self,
            method: str,
            url: str,
            timeout: float,
        ) -> UnavailableResponse:
            return UnavailableResponse()

    monkeypatch.setattr("urllib3.PoolManager", FailingServicePoolManager)
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.get("/api/backend-health", headers=headers())

    assert response.status_code == 200
    assert response.json() == {
        "requestedBy": "student",
        "status": 503,
        "body": "maintenance",
    }
