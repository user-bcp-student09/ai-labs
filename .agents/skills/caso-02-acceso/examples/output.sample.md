```json
{
  "case_id": "caso-02",
  "service": "acceso",
  "error_code": "AUTH_DEPENDENCY_UNAVAILABLE",
  "correlation_id": "LOGIN-DEMO-201",
  "first_seen": "2026-11-03T15:00:01Z",
  "last_seen": "2026-11-03T15:00:04Z",
  "occurrences": 2,
  "priority": "P2",
  "summary": "El servicio acceso recibió 503 de la dependencia identidad-demo en dos eventos de error durante un intento de autenticación.",
  "impact": "Afecta al intento de autenticación LOGIN-DEMO-201. Un 503 no demuestra una contraseña incorrecta; el rechazo INVALID_CREDENTIALS de LOGIN-DEMO-202 es otra operación y no se combina.",
  "evidence": [
    { "file": "caso-02-acceso.jsonl", "line": 1, "text": "{\"timestamp\":\"2026-11-03T10:00:00-05:00\",\"level\":\"INFO\",\"service\":\"acceso\",\"correlation_id\":\"LOGIN-DEMO-201\",\"event_id\":\"evt-201\",\"message\":\"Inicio de autenticacion\",\"user\":\"[REDACTADO]\"}" },
    { "file": "caso-02-acceso.jsonl", "line": 2, "text": "{\"timestamp\":\"2026-11-03T10:00:01-05:00\",\"level\":\"ERROR\",\"service\":\"acceso\",\"correlation_id\":\"LOGIN-DEMO-201\",\"event_id\":\"evt-202\",\"code\":\"AUTH_DEPENDENCY_UNAVAILABLE\",\"http_status\":503,\"dependency\":\"identidad-demo\",\"password\":\"[REDACTADO]\"}" },
    { "file": "caso-02-acceso.jsonl", "line": 4, "text": "{\"timestamp\":\"2026-11-03T10:00:04-05:00\",\"level\":\"ERROR\",\"service\":\"acceso\",\"correlation_id\":\"LOGIN-DEMO-201\",\"event_id\":\"evt-203\",\"code\":\"AUTH_DEPENDENCY_UNAVAILABLE\",\"http_status\":503,\"dependency\":\"identidad-demo\",\"token\":\"[REDACTADO]\"}" }
  ],
  "missing_data": [
    "El log no indica si el intento de LOGIN-DEMO-201 terminó con éxito o con rechazo final.",
    "El log no indica la causa de la indisponibilidad de identidad-demo ni su duración total.",
    "No hay evidencia de que otros clientes o canales hayan sido afectados."
  ],
  "recommended_action": "Pedir a un responsable humano que revise el estado de identidad-demo entre 15:00:01Z y 15:00:04Z del 2026-11-03 y confirme el resultado final de LOGIN-DEMO-201."
}
```
