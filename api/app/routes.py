from fastapi import APIRouter, Depends, File, Query, UploadFile

from .file_storage import RequirementStorage
from .models import (
    BackendHealthResponse,
    Identity,
    RequirementResponse,
    TaskCreate,
    TaskResponse,
    TaskSummaryResponse,
)
from .security import get_identity
from .services import BackendHealthService, TaskService


def create_api_router(
    task_service: TaskService,
    requirement_storage: RequirementStorage,
    backend_health_service: BackendHealthService,
) -> APIRouter:
    router = APIRouter(prefix="/api")

    @router.get("/tasks", response_model=list[TaskResponse])
    def list_tasks(
        _owner: str | None = Query(default=None, alias="owner"),
        identity: Identity = Depends(get_identity),
    ) -> list[TaskResponse]:
        return task_service.list_tasks(identity)

    @router.post("/tasks", status_code=201, response_model=TaskResponse)
    def create_task(
        payload: TaskCreate,
        identity: Identity = Depends(get_identity),
    ) -> TaskResponse:
        return task_service.create_task(payload, identity)

    @router.get("/summary", response_model=TaskSummaryResponse)
    def summary(
        _owner: str | None = Query(default=None, alias="owner"),
        identity: Identity = Depends(get_identity),
    ) -> TaskSummaryResponse:
        return task_service.summarize(identity)

    @router.post("/requirements", status_code=201, response_model=RequirementResponse)
    async def upload_requirement(
        file: UploadFile = File(...),
        identity: Identity = Depends(get_identity),
    ) -> RequirementResponse:
        return await requirement_storage.save(file, identity)

    @router.get("/backend-health", response_model=BackendHealthResponse)
    def backend_health(
        identity: Identity = Depends(get_identity),
    ) -> BackendHealthResponse:
        return backend_health_service.check(identity)

    return router
