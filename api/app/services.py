import os

import urllib3

from .models import (
    BackendHealthResponse,
    Identity,
    TaskCreate,
    TaskResponse,
    TaskSummaryResponse,
)
from .repositories import TaskRepository

DEFAULT_AUTH_HEALTH_URL = "http://localhost:8080/q/health"
BACKEND_HEALTH_TIMEOUT_SECONDS = 3.0
CLOSED_TASK_STATUSES = frozenset({"DONE", "ARCHIVED"})


class TaskService:
    def __init__(self, repository: TaskRepository) -> None:
        self.repository = repository

    def list_tasks(self, identity: Identity) -> list[TaskResponse]:
        rows = self.repository.list_for_owner(identity.username)
        return [TaskResponse.model_validate(dict(row)) for row in rows]

    def create_task(self, task: TaskCreate, identity: Identity) -> TaskResponse:
        row = self.repository.create(task, identity.username)
        return TaskResponse.model_validate(dict(row))

    def summarize(self, identity: Identity) -> TaskSummaryResponse:
        rows = self.repository.summary_rows_for_owner(identity.username)
        counts_by_status: dict[str, int] = {}
        high_priority_open = 0

        for row in rows:
            status = row["status"]
            counts_by_status[status] = counts_by_status.get(status, 0) + 1
            if row["priority"] == "HIGH" and status not in CLOSED_TASK_STATUSES:
                high_priority_open += 1

        return TaskSummaryResponse(
            owner=identity.username,
            total=len(rows),
            by_status=counts_by_status,
            high_priority_open=high_priority_open,
        )


class BackendHealthService:
    def check(self, identity: Identity) -> BackendHealthResponse:
        url = os.getenv("AUTH_HEALTH_URL", DEFAULT_AUTH_HEALTH_URL)
        try:
            response = urllib3.PoolManager().request(
                "GET",
                url,
                timeout=BACKEND_HEALTH_TIMEOUT_SECONDS,
            )
        except urllib3.exceptions.HTTPError:
            return BackendHealthResponse(
                requested_by=identity.username,
                status=0,
                body="Backend unavailable",
            )

        return BackendHealthResponse(
            requested_by=identity.username,
            status=response.status,
            body=response.data.decode("utf-8"),
        )
