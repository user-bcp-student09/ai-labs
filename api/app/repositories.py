import sqlite3

from .models import TaskCreate

TASK_COLUMNS = "id,title,description,status,owner,priority,created_at"


class TaskRepository:
    def __init__(self, connection: sqlite3.Connection) -> None:
        self.connection = connection

    def initialize(self) -> None:
        self.connection.execute(
            "CREATE TABLE IF NOT EXISTS tasks ("
            "id INTEGER PRIMARY KEY AUTOINCREMENT, "
            "title TEXT NOT NULL, "
            "description TEXT NOT NULL, "
            "status TEXT NOT NULL, "
            "owner TEXT NOT NULL, "
            "priority TEXT NOT NULL, "
            "created_at TEXT NOT NULL)"
        )
        task_count = self.connection.execute(
            "SELECT COUNT(*) AS total FROM tasks"
        ).fetchone()["total"]
        if task_count:
            return

        seed_tasks = [
            (
                "Refactor login",
                "Separar responsabilidades en frontend",
                "TODO",
                "student",
                "HIGH",
            ),
            (
                "Review API",
                "Revisar seguridad de endpoints",
                "IN_PROGRESS",
                "student",
                "MEDIUM",
            ),
            (
                "Trainer notes",
                "Preparar retrospectiva",
                "TODO",
                "trainer",
                "LOW",
            ),
        ]
        self.connection.executemany(
            "INSERT INTO tasks(title,description,status,owner,priority,created_at) "
            "VALUES (?, ?, ?, ?, ?, datetime('now'))",
            seed_tasks,
        )
        self.connection.commit()

    def list_for_owner(self, owner: str) -> list[sqlite3.Row]:
        rows = self.connection.execute(
            f"SELECT {TASK_COLUMNS} FROM tasks WHERE owner = ? ORDER BY id",
            (owner,),
        )
        return rows.fetchall()

    def create(self, task: TaskCreate, owner: str) -> sqlite3.Row:
        cursor = self.connection.execute(
            "INSERT INTO tasks(title,description,status,owner,priority,created_at) "
            "VALUES (?, ?, ?, ?, ?, datetime('now'))",
            (task.title, task.description, task.status, owner, task.priority),
        )
        self.connection.commit()
        row = self.connection.execute(
            f"SELECT {TASK_COLUMNS} FROM tasks WHERE id = ?",
            (cursor.lastrowid,),
        ).fetchone()
        if row is None:
            raise RuntimeError("Created task could not be retrieved")
        return row

    def summary_rows_for_owner(self, owner: str) -> list[sqlite3.Row]:
        rows = self.connection.execute(
            "SELECT status,priority FROM tasks WHERE owner = ?",
            (owner,),
        )
        return rows.fetchall()
