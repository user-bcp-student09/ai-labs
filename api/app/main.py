from __future__ import annotations

import base64
import hashlib
import hmac
import json
import os
import re
import sqlite3
import time
from pathlib import Path
from typing import Any

import urllib3
from fastapi import Body, Depends, FastAPI, File, Header, HTTPException, Query, UploadFile
from fastapi.middleware.cors import CORSMiddleware

TOKEN_ISSUER = "ai-labs-auth"
TOKEN_AUDIENCE = "ai-labs-api"
TOKEN_ALGORITHM = "HS256"
TOKEN_TYPE = "JWT"
TOKEN_SECRET_ENVIRONMENT_VARIABLE = "AUTH_TOKEN_SECRET"
TOKEN_SECRET_MINIMUM_BYTES = 32
TOKEN_MAXIMUM_LENGTH = 8192
BASE64URL_PATTERN = re.compile(r"[A-Za-z0-9_-]+")


def jwt_identity(authorization: str = Header(default="")) -> dict[str, str]:
    scheme, separator, token = authorization.partition(" ")
    if scheme.lower() != "bearer" or not separator or not token:
        raise_invalid_token()
    if len(token) > TOKEN_MAXIMUM_LENGTH:
        raise_invalid_token()

    token_parts = token.split(".")
    if len(token_parts) != 3:
        raise_invalid_token()

    try:
        header = decode_json_object(token_parts[0])
        claims = decode_json_object(token_parts[1])
        supplied_signature = decode_base64url(token_parts[2])
    except ValueError:
        raise_invalid_token()

    if header != {"alg": TOKEN_ALGORITHM, "typ": TOKEN_TYPE}:
        raise_invalid_token()

    signing_input = f"{token_parts[0]}.{token_parts[1]}".encode("ascii")
    expected_signature = hmac.new(
        get_token_secret(),
        signing_input,
        hashlib.sha256,
    ).digest()
    if not hmac.compare_digest(expected_signature, supplied_signature):
        raise_invalid_token()

    if not has_valid_claims(claims):
        raise_invalid_token()

    return {"username": claims["sub"], "role": claims["role"]}


def decode_json_object(encoded_value: str) -> dict[str, Any]:
    parsed_value = json.loads(
        decode_base64url(encoded_value),
        object_pairs_hook=unique_json_object,
    )
    if not isinstance(parsed_value, dict):
        raise ValueError("JWT segment must contain a JSON object")
    return parsed_value


def decode_base64url(encoded_value: str) -> bytes:
    if not BASE64URL_PATTERN.fullmatch(encoded_value):
        raise ValueError("Invalid base64url")
    padded_value = encoded_value + "=" * (-len(encoded_value) % 4)
    return base64.urlsafe_b64decode(padded_value)


def unique_json_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    parsed_object: dict[str, Any] = {}
    for key, value in pairs:
        if key in parsed_object:
            raise ValueError("Duplicate JSON member")
        parsed_object[key] = value
    return parsed_object


def get_token_secret() -> bytes:
    token_secret = os.getenv(TOKEN_SECRET_ENVIRONMENT_VARIABLE)
    if token_secret is None or token_secret.strip() == "":
        raise RuntimeError(f"{TOKEN_SECRET_ENVIRONMENT_VARIABLE} must be configured")

    secret_bytes = token_secret.encode("utf-8")
    if len(secret_bytes) < TOKEN_SECRET_MINIMUM_BYTES:
        raise RuntimeError(
            f"{TOKEN_SECRET_ENVIRONMENT_VARIABLE} must contain at least "
            f"{TOKEN_SECRET_MINIMUM_BYTES} bytes"
        )
    return secret_bytes


def has_valid_claims(claims: dict[str, Any]) -> bool:
    if claims.get("iss") != TOKEN_ISSUER or claims.get("aud") != TOKEN_AUDIENCE:
        return False

    username = claims.get("sub")
    role = claims.get("role")
    issued_at = claims.get("iat")
    expires_at = claims.get("exp")
    if not isinstance(username, str) or not username.strip():
        return False
    if not isinstance(role, str) or not role.strip():
        return False
    if type(issued_at) is not int or type(expires_at) is not int:
        return False

    current_time = int(time.time())
    return issued_at <= current_time < expires_at and expires_at > issued_at


def raise_invalid_token() -> None:
    raise HTTPException(status_code=401, detail="Invalid token")


def create_app(db_path: str | None = None, upload_dir: str | None = None) -> FastAPI:
    selected_db = db_path or os.getenv("WORKSHOP_DB_PATH", "./data/workshop.db")
    selected_uploads = upload_dir or os.getenv("WORKSHOP_UPLOAD_DIR", "./uploads")
    Path(selected_db).parent.mkdir(parents=True, exist_ok=True)
    Path(selected_uploads).mkdir(parents=True, exist_ok=True)

    connection = sqlite3.connect(selected_db, check_same_thread=False)
    connection.row_factory = sqlite3.Row
    connection.execute("CREATE TABLE IF NOT EXISTS tasks (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, description TEXT NOT NULL, status TEXT NOT NULL, owner TEXT NOT NULL, priority TEXT NOT NULL, created_at TEXT NOT NULL)")
    count = connection.execute("SELECT COUNT(*) AS total FROM tasks").fetchone()["total"]
    if count == 0:
        connection.execute("INSERT INTO tasks(title,description,status,owner,priority,created_at) VALUES ('Refactor login','Separar responsabilidades en frontend','TODO','student','HIGH',datetime('now'))")
        connection.execute("INSERT INTO tasks(title,description,status,owner,priority,created_at) VALUES ('Review API','Revisar seguridad de endpoints','IN_PROGRESS','student','MEDIUM',datetime('now'))")
        connection.execute("INSERT INTO tasks(title,description,status,owner,priority,created_at) VALUES ('Trainer notes','Preparar retrospectiva','TODO','trainer','LOW',datetime('now'))")
        connection.commit()

    app = FastAPI(title="Workshop Requirements API", version="0.1.0")
    app.state.connection = connection
    app.state.upload_dir = selected_uploads
    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    def as_task(row: sqlite3.Row) -> dict[str, Any]:
        return {
            "id": row["id"],
            "title": row["title"],
            "description": row["description"],
            "status": row["status"],
            "owner": row["owner"],
            "priority": row["priority"],
            "createdAt": row["created_at"],
        }

    @app.get("/api/tasks")
    def list_tasks(owner: str | None = Query(default=None), identity: dict[str, str] = Depends(jwt_identity)) -> list[dict[str, Any]]:
        requested_owner = owner or identity["username"]
        sql = "SELECT id,title,description,status,owner,priority,created_at FROM tasks WHERE owner='" + requested_owner + "' ORDER BY id"
        rows = connection.execute(sql).fetchall()
        result: list[dict[str, Any]] = []
        for row in rows:
            result.append(as_task(row))
        return result

    @app.post("/api/tasks", status_code=201)
    def create_task(payload: dict[str, Any] = Body(...), identity: dict[str, str] = Depends(jwt_identity)) -> dict[str, Any]:
        title = str(payload.get("title", ""))
        description = str(payload.get("description", ""))
        status = str(payload.get("status", "TODO"))
        priority = str(payload.get("priority", "MEDIUM"))
        owner = str(payload.get("owner", identity["username"]))
        sql = "INSERT INTO tasks(title,description,status,owner,priority,created_at) VALUES ('" + title + "','" + description + "','" + status + "','" + owner + "','" + priority + "',datetime('now'))"
        cursor = connection.execute(sql)
        connection.commit()
        row = connection.execute("SELECT id,title,description,status,owner,priority,created_at FROM tasks WHERE id=" + str(cursor.lastrowid)).fetchone()
        return as_task(row)

    @app.get("/api/summary")
    def summary(owner: str | None = Query(default=None), identity: dict[str, str] = Depends(jwt_identity)) -> dict[str, Any]:
        requested_owner = owner or identity["username"]
        rows = connection.execute("SELECT status,priority FROM tasks WHERE owner='" + requested_owner + "'").fetchall()
        response: dict[str, Any] = {"owner": requested_owner, "total": 0, "byStatus": {}, "highPriorityOpen": 0}
        for row in rows:
            response["total"] = response["total"] + 1
            if row["status"] in response["byStatus"]:
                response["byStatus"][row["status"]] = response["byStatus"][row["status"]] + 1
            else:
                response["byStatus"][row["status"]] = 1
            if row["priority"] == "HIGH":
                if row["status"] != "DONE":
                    if row["status"] != "ARCHIVED":
                        response["highPriorityOpen"] = response["highPriorityOpen"] + 1
        return response

    @app.post("/api/requirements", status_code=201)
    async def upload_requirement(file: UploadFile = File(...), identity: dict[str, str] = Depends(jwt_identity)) -> dict[str, Any]:
        target = Path(selected_uploads) / str(file.filename)
        content = await file.read()
        target.write_bytes(content)
        return {"owner": identity["username"], "fileName": file.filename, "storedAt": str(target), "size": len(content)}

    @app.get("/api/backend-health")
    def backend_health(identity: dict[str, str] = Depends(jwt_identity)) -> dict[str, Any]:
        url = os.getenv("AUTH_HEALTH_URL", "http://localhost:8080/q/health")
        try:
            response = urllib3.PoolManager().request("GET", url, timeout=3.0)
            return {"requestedBy": identity["username"], "status": response.status, "body": response.data.decode("utf-8")}
        except Exception as error:
            return {"requestedBy": identity["username"], "status": 0, "body": str(error)}

    @app.on_event("shutdown")
    def close_connection() -> None:
        connection.close()

    return app


app = create_app()
