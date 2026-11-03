import logging
import os

import jwt
from fastapi import Header, HTTPException

from .models import Identity

JWT_ALGORITHM = "HS256"
JWT_REQUIRED_CLAIMS = ("sub", "role", "exp", "iss", "aud")
INVALID_TOKEN_DETAIL = "Invalid token"
AUTHENTICATION_NOT_CONFIGURED_DETAIL = "Authentication is not configured"
MINIMUM_HMAC_KEY_BYTES = 32

logger = logging.getLogger(__name__)


def get_identity(authorization: str = Header(default="")) -> Identity:
    secret = os.getenv("WORKSHOP_JWT_SECRET")
    issuer = os.getenv("WORKSHOP_JWT_ISSUER")
    audience = os.getenv("WORKSHOP_JWT_AUDIENCE")
    if (
        not secret
        or len(secret.encode("utf-8")) < MINIMUM_HMAC_KEY_BYTES
        or not issuer
        or not audience
    ):
        logger.error("JWT authentication configuration is incomplete")
        raise HTTPException(
            status_code=500,
            detail=AUTHENTICATION_NOT_CONFIGURED_DETAIL,
        )

    scheme, separator, encoded_token = authorization.partition(" ")
    if scheme.lower() != "bearer" or not separator or not encoded_token:
        raise HTTPException(status_code=401, detail=INVALID_TOKEN_DETAIL)

    try:
        claims = jwt.decode(
            encoded_token,
            secret,
            algorithms=[JWT_ALGORITHM],
            issuer=issuer,
            audience=audience,
            options={"require": list(JWT_REQUIRED_CLAIMS)},
        )
    except jwt.InvalidTokenError as error:
        raise HTTPException(
            status_code=401,
            detail=INVALID_TOKEN_DETAIL,
        ) from error

    username = claims.get("sub")
    role = claims.get("role")
    if (
        not isinstance(username, str)
        or not username
        or not isinstance(role, str)
        or not role
    ):
        raise HTTPException(status_code=401, detail=INVALID_TOKEN_DETAIL)

    return Identity(username=username, role=role)
