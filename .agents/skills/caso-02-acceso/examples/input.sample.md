# Entrada de ejemplo (ficticia)

Caso `caso-02`, archivo `caso-02-acceso.jsonl`, líneas numeradas desde 1:

```jsonl
1 {"timestamp":"2026-11-03T10:00:00-05:00","level":"INFO","service":"acceso","correlation_id":"LOGIN-DEMO-201","event_id":"evt-201","message":"Inicio de autenticacion","user":"PERSONA-FICTICIA-02"}
2 {"timestamp":"2026-11-03T10:00:01-05:00","level":"ERROR","service":"acceso","correlation_id":"LOGIN-DEMO-201","event_id":"evt-202","code":"AUTH_DEPENDENCY_UNAVAILABLE","http_status":503,"dependency":"identidad-demo","password":"CLAVE-FICTICIA-NO-REAL"}
3 {"timestamp":"2026-11-03T10:00:01-05:00","level":"ERROR","service":"acceso","correlation_id":"LOGIN-DEMO-201","event_id":"evt-202","code":"AUTH_DEPENDENCY_UNAVAILABLE","http_status":503,"dependency":"identidad-demo","password":"CLAVE-FICTICIA-NO-REAL"}
4 {"timestamp":"2026-11-03T10:00:04-05:00","level":"ERROR","service":"acceso","correlation_id":"LOGIN-DEMO-201","event_id":"evt-203","code":"AUTH_DEPENDENCY_UNAVAILABLE","http_status":503,"dependency":"identidad-demo","token":"TOKEN-FICTICIO-XYZ"}
5 {"timestamp":"2026-11-03T10:01:00-05:00","level":"WARN","service":"acceso","correlation_id":"LOGIN-DEMO-202","event_id":"evt-204","code":"INVALID_CREDENTIALS","http_status":401}
```
