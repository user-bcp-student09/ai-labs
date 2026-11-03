import unicodedata
from pathlib import Path

from fastapi import HTTPException, UploadFile

from .models import Identity, RequirementResponse

ALLOWED_FILE_EXTENSIONS = frozenset({".txt"})
INVALID_FILENAME_CHARACTERS = frozenset('<>:"|?*')
RESERVED_WINDOWS_FILENAMES = frozenset(
    {
        "CON",
        "PRN",
        "AUX",
        "NUL",
        *(f"COM{number}" for number in range(1, 10)),
        *(f"LPT{number}" for number in range(1, 10)),
    }
)
MAX_UPLOAD_BYTES = 5 * 1024 * 1024
UPLOAD_READ_CHUNK_BYTES = 64 * 1024
UNSUPPORTED_FILE_DETAIL = "Only .txt files are allowed"
INVALID_FILENAME_DETAIL = "Invalid file name"
UPLOAD_TOO_LARGE_DETAIL = "File exceeds the maximum allowed size"


class RequirementStorage:
    def __init__(self, upload_dir: str) -> None:
        self.upload_dir = Path(upload_dir)
        self.upload_dir.mkdir(parents=True, exist_ok=True)

    async def save(
        self,
        upload: UploadFile,
        identity: Identity,
    ) -> RequirementResponse:
        filename = self._normalize_filename(upload.filename)
        target = self.upload_dir / filename
        if not target.resolve().is_relative_to(self.upload_dir.resolve()):
            raise HTTPException(status_code=400, detail=INVALID_FILENAME_DETAIL)

        content = await self._read_limited_content(upload)
        target.write_bytes(content)
        return RequirementResponse(
            owner=identity.username,
            file_name=filename,
            stored_at=str(target),
            size=len(content),
        )

    @staticmethod
    def _normalize_filename(filename: str | None) -> str:
        if not filename:
            raise HTTPException(status_code=400, detail=INVALID_FILENAME_DETAIL)

        normalized_path = unicodedata.normalize("NFKC", filename).replace("\\", "/")
        normalized_name = (
            normalized_path.rsplit("/", maxsplit=1)[-1].strip().rstrip(" .")
        )
        if (
            not normalized_name
            or any(
                ord(character) < 32 or ord(character) == 127
                for character in normalized_name
            )
            or any(
                character in INVALID_FILENAME_CHARACTERS
                for character in normalized_name
            )
            or normalized_name.split(".", maxsplit=1)[0].upper()
            in RESERVED_WINDOWS_FILENAMES
        ):
            raise HTTPException(status_code=400, detail=INVALID_FILENAME_DETAIL)

        extension = Path(normalized_name).suffix.lower()
        if extension not in ALLOWED_FILE_EXTENSIONS:
            raise HTTPException(status_code=400, detail=UNSUPPORTED_FILE_DETAIL)
        return normalized_name

    @staticmethod
    async def _read_limited_content(upload: UploadFile) -> bytes:
        chunks: list[bytes] = []
        total_size = 0
        while chunk := await upload.read(UPLOAD_READ_CHUNK_BYTES):
            total_size += len(chunk)
            if total_size > MAX_UPLOAD_BYTES:
                raise HTTPException(status_code=413, detail=UPLOAD_TOO_LARGE_DETAIL)
            chunks.append(chunk)
        return b"".join(chunks)
