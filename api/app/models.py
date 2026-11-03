from pydantic import BaseModel, ConfigDict, Field


class Identity(BaseModel):
    username: str
    role: str


class TaskCreate(BaseModel):
    model_config = ConfigDict(extra="ignore")

    title: str = ""
    description: str = ""
    status: str = "TODO"
    priority: str = "MEDIUM"


class TaskResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    id: int
    title: str
    description: str
    status: str
    owner: str
    priority: str
    created_at: str = Field(alias="createdAt")


class TaskSummaryResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    owner: str
    total: int
    by_status: dict[str, int] = Field(alias="byStatus")
    high_priority_open: int = Field(alias="highPriorityOpen")


class RequirementResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    owner: str
    file_name: str = Field(alias="fileName")
    stored_at: str = Field(alias="storedAt")
    size: int


class BackendHealthResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    requested_by: str = Field(alias="requestedBy")
    status: int
    body: str
