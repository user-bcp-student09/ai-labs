# Evidencia de pruebas — API

Los comandos de pruebas se ejecutaron con cwd `api/`, Python 3.13.17 y el
entorno aislado `api/.venv`. Las pruebas usan SQLite y directorios temporales;
backend-health usa una respuesta simulada. No se hicieron llamadas de prueba a
servicios remotos.

## Matriz de pruebas por endpoint — H-API-01..H-API-07

Los casos se implementan en `api/tests/test_api.py`. Los errores de
autenticación se comprueban en cada ruta: respuestas `401` tanto para header
ausente como para token inválido, y `500` cuando falta la configuración JWT.

| endpoint | caso | tipo | resultado esperado |
|---|---|---|---|
| `GET /api/tasks` | Listar tareas semilla | Camino feliz | `200`, tareas del `sub` autenticado con las claves documentadas. |
| `GET /api/tasks` | Usuario sin tareas intenta `owner=student` | Borde/autorización | `200`, lista vacía; no lee tareas de otro `owner`. |
| `GET /api/tasks` | Header ausente o token inválido | Error | `401`, `{"detail":"Invalid token"}`. |
| `GET /api/tasks` | Configuración JWT ausente | Error | `500`, `{"detail":"Authentication is not configured"}`. |
| `POST /api/tasks` | Crear tarea válida | Camino feliz | `201`, respuesta tipada; dueño procede de `sub`. |
| `POST /api/tasks` | JSON vacío | Borde | `201`, defaults `""`, `""`, `TODO`, `MEDIUM`. |
| `POST /api/tasks` | JSON ausente, malformado o tipo conocido inválido | Error | `422`, detalle de validación FastAPI/Pydantic. |
| `POST /api/tasks` | Header ausente o token inválido | Error | `401`, `{"detail":"Invalid token"}`. |
| `POST /api/tasks` | Configuración JWT ausente | Error | `500`, `{"detail":"Authentication is not configured"}`. |
| `GET /api/summary` | Resumen del usuario con tareas | Camino feliz | `200`, conteos por estado y prioridades abiertas correctos. |
| `GET /api/summary` | Usuario autenticado sin tareas; query `owner` ajeno | Borde/autorización | `200`, total `0`, mapa vacío y sin acceso a las tareas solicitadas. |
| `GET /api/summary` | Header ausente o token inválido | Error | `401`, `{"detail":"Invalid token"}`. |
| `GET /api/summary` | Configuración JWT ausente | Error | `500`, `{"detail":"Authentication is not configured"}`. |
| `POST /api/requirements` | Archivo `.txt` válido | Camino feliz | `201`, archivo escrito y metadatos de respuesta. |
| `POST /api/requirements` | Archivo vacío y archivo exactamente de 5 MiB | Borde | `201`; tamaños `0` y `5,242,880` bytes. |
| `POST /api/requirements` | Sin extensión, extensión no permitida o nombre inválido | Error | `400`, detalle genérico documentado; no guarda archivo rechazado. |
| `POST /api/requirements` | Archivo de más de 5 MiB | Error | `413`, `{"detail":"File exceeds the maximum allowed size"}`. |
| `POST /api/requirements` | Campo multipart `file` ausente | Error | `422`, error de campo requerido. |
| `POST /api/requirements` | Header ausente o token inválido | Error | `401`, `{"detail":"Invalid token"}`. |
| `POST /api/requirements` | Configuración JWT ausente | Error | `500`, `{"detail":"Authentication is not configured"}`. |
| `GET /api/backend-health` | Backend simulado responde `204` | Camino feliz | `200` de la API con `status` y cuerpo del backend. |
| `GET /api/backend-health` | Backend simulado responde `503` | Borde | `200` de la API; preserva `status: 503` y cuerpo. |
| `GET /api/backend-health` | Fallo HTTP/de red del cliente urllib3 | Error de dependencia (contrato: fallback) | `200`, `status: 0`, cuerpo `"Backend unavailable"`. |
| `GET /api/backend-health` | Header ausente o token inválido | Error | `401`, `{"detail":"Invalid token"}`. |
| `GET /api/backend-health` | Configuración JWT ausente | Error | `500`, `{"detail":"Authentication is not configured"}`. |

### Código fuente de las pruebas

El código ejecutable completo, incluidos los tests anteriores de regresión,
está en [`api/tests/test_api.py`](../api/tests/test_api.py). Los nuevos casos
transversales son `test_each_endpoint_rejects_missing_authentication`,
`test_each_endpoint_rejects_invalid_authentication` y
`test_each_endpoint_reports_missing_authentication_configuration`. Los demás
tests de borde y por ruta incluyen
`test_owner_cannot_read_another_users_tasks`,
`test_creates_task_from_empty_payload`,
`test_rejects_task_without_json_body`, `test_rejects_malformed_task_json`,
`test_accepts_empty_requirement_file`,
`test_accepts_requirement_file_at_exact_size_limit`,
`test_rejects_requirement_file_without_extension`,
`test_rejects_requirement_file_without_upload_field` y
`test_backend_health_preserves_non_success_backend_status`.

## H-API-01..H-API-07 — caracterización y regresión

| etapa | comando desde `api/` | resultado |
|---|---|---|
| Línea base antes de ampliar pruebas | `.\.venv\Scripts\python.exe -m pytest --cov=app --cov-report=term-missing` | 4 passed; cobertura `app`: 91%. |
| Caracterización antes de producción | `.\.venv\Scripts\python.exe -m pytest --cov=app --cov-report=term-missing` | 8 passed; cobertura `app`: 95%. Confirmó token Base64 vigente, rechazo de token inválido, filtros `owner`, defaults y alias, carga `.txt` y forma exitosa de backend-health. |
| Correcciones contra producción anterior | `.\.venv\Scripts\python.exe -m pytest -q -k 'accepts_signed_jwt or rejects_jwt or parameterizes or ignores_client_owner or normalizes_upload or rejects_upload_larger or generic_backend or validates_task'` | 9 failed como se esperaba: JWT válido era rechazado, SQL con comillas fallaba, `owner` del cliente alteraba el ámbito, traversal/extensión/tamaño no se controlaban, se exponían detalles del backend y body tipado inválido era aceptado. |
| Suite completa anterior a esta ampliación | `.\.venv\Scripts\python.exe -m pytest --cov=app --cov-report=term-missing` | 20 passed; cobertura total `app`: 97% (227 statements, 4 sin cubrir; 26 branches, 4 parciales). Resultado histórico previo, preservado. |
| Consistencia de dependencias | `.\.venv\Scripts\python.exe -m pip check` | `No broken requirements found.` |

## Ampliación de cobertura de endpoints — H-API-01..H-API-07

| etapa | comando desde `api/` | código de salida | resultado |
|---|---|---:|---|
| Suite ampliada final | `.\.venv\Scripts\python.exe -m pytest --cov=app --cov-report=term-missing` | `0` | **44 passed**, 1 advertencia de deprecación de `anyio` originada en `starlette.testclient`. |

### Cobertura

Método: `pytest-cov`/Coverage.py con `branch = true`, midiendo `source = app`
según `api/pyproject.toml`; reporte de consola `term-missing`. Cobertura
statement global: **224/227 statements (98.7%)**. El reporte branch-aware de
Coverage.py da **98%**: 3 statements sin cubrir y 3 branch points parciales de
26. Por módulo, `file_storage.py` da **93%** (44/46 statements ejecutados,
2 branch points parciales) y `repositories.py` **94%** (26/27 statements,
1 branch point parcial); los otros seis módulos `app` quedaron al 100%.

No se modificó código de producción en esta ampliación: el archivo de
pruebas modificado es `api/tests/test_api.py`, que no es un módulo de
`source=app` y no se incluye en ese denominador. La cobertura global de la
aplicación y la cobertura por módulo anterior son la medición ejecutada para
verificar el umbral; no se presenta como cobertura diferencial del diff.

### Fallos, pendientes y pruebas propuestas

- **Fallos finales:** ninguno. En una primera ejecución de la ampliación hubo
  un fallo de expectativa del test de aislamiento: usó `trainer`, que tiene
  una tarea semilla propia, cuando esperaba lista vacía. La evidencia de la
  respuesta condujo a cambiar el actor del test a `outsider`; la prueba ahora
  confirma que no obtiene tareas de `student`, y la suite final pasó. No se
  modificó producción para ocultar el caso.
- **Invocación fallida:** se intentó inicialmente ejecutar
  `.\.venv\Scripts\python.exe` desde la raíz; esa ruta solo existe bajo `api/`
  y no se inició pytest. La ejecución registrada arriba se repitió desde el
  directorio correcto, `api/`, con salida 0.
- **Pendientes:** no se probó el emisor JWT real ni se hicieron solicitudes a
  servicios remotos. Se conserva el riesgo SCA de `pytest` y `starlette`
  descrito en la sección H-API-05 anterior.
- **Propuestas, no ejecutadas:** prueba de integración con el emisor real una
  vez disponible su configuración, y definición/pruebas del contrato para
  fallos inesperados de filesystem o decodificación del cuerpo del backend.
  Esos casos se mantienen como preguntas abiertas en `docs/ENDPOINTS.md`.

La primera suite después de implementar obtuvo 17 passed y 1 failed: un JWT
firmado con `role` vacío se aceptaba. Se ajustó la validación para rechazarlo y
la suite completa de esa etapa pasó. La única advertencia de pytest final es
una deprecación de `anyio` que proviene de `starlette.testclient`; no se
suprimió.

### Casos por hallazgo

| hallazgo | prueba de corrección: antes → después |
|---|---|
| H-API-01 | El token Base64 caracterizado antes del cambio dejó de autenticarse. JWT HS256 firmado pasa; firma, expiración, emisor, audiencia, algoritmo y rol inválidos reciben `401` genérico. Configuración ausente recibe `500` explícito. |
| H-API-02 | Crear tarea con comillas fallaba antes; ahora devuelve `201` y conserva título y descripción. El filtro parametrizado no devuelve tareas de otros usuarios. |
| H-API-03 | Antes, `owner=trainer` consultaba tareas de trainer y un body podía asignar owner trainer. Ahora ambos datos del cliente se ignoran: lista, resumen e insert usan `sub`. |
| H-API-04 | Antes no había normalización ni límites. Ahora nombres POSIX/Windows se reducen a basename, nombres reservados o inválidos y extensiones no permitidas reciben `400`, y más de 5 MiB recibe `413` sin escribir el destino. |
| H-API-05 | La auditoría de los pins anteriores encontró 22 entradas de advisories en los dos paquetes; la auditoría de los pins nuevos no encontró vulnerabilidades conocidas en ellos. El escaneo completo del entorno se detalla en la sección SCA. |
| H-API-06 | Las pruebas de seed, reapertura de SQLite, rutas, status, respuestas y almacenamiento pasaron antes y después de separar capas. |
| H-API-07 | Antes un título numérico se convertía y se aceptaba; ahora el modelo lo rechaza con `422`. La indisponibilidad HTTP mantiene el formato de respuesta y usa texto genérico. |

## H-API-05 — evidencia SCA

Se consultaron advisories upstream y se ejecutó `pip-audit 2.10.1`. Para la
comparación se crearon temporalmente dos archivos de requisitos con solo los
pins de H-API-05; se eliminaron después de la auditoría.

Comandos exactos desde `api/`:

- Antes: `.\.venv\Scripts\pip-audit.exe --no-deps -r .sca-baseline.txt` —
  **22 entradas**, salida 1.
- Después: `.\.venv\Scripts\pip-audit.exe --no-deps -r .sca-current.txt` —
  **No known vulnerabilities found**, salida 0.
- Auditoría completa: `.\.venv\Scripts\pip-audit.exe --format columns` —
  **12 entradas** en `pytest` y `starlette`, salida 1.

Los archivos temporales contenían, respectivamente, los pins originales
`urllib3==2.5.0`/`python-multipart==0.0.21` y los pins actualizados
`urllib3==2.8.0`/`python-multipart==0.0.33`; se limpiaron al terminar.
Las comparaciones `--no-deps` también emitieron la recomendación de usar hashes
para requisitos fijados; no se añadieron hashes ni se ignoraron avisos.

| paquete | pin anterior | pin aplicado | evidencia de corrección |
|---|---:|---:|---|
| `urllib3` | `2.5.0` | `2.8.0` | `pip-audit --no-deps` reportó advisories para el pin anterior y ninguno para 2.8.0. El changelog de 2.8.0 lista correcciones GHSA-8988-9cw3-xx77, GHSA-vxq7-64xx-v4gw y GHSA-gh4c-6fx4-qh6g; incluye también la corrección previa GHSA-38jv-5279-wg99. [Changelog upstream](https://urllib3.readthedocs.io/en/stable/changelog.html). |
| `python-multipart` | `0.0.21` | `0.0.33` | `pip-audit --no-deps` reportó advisories para el pin anterior y ninguno para 0.0.33. Las versiones corregidas upstream incluyen 0.0.22, 0.0.26, 0.0.27 y 0.0.31 para los avisos consultados. [GHSA-wp53-j4wj-2cfg](https://github.com/advisories/GHSA-wp53-j4wj-2cfg), [GHSA-mj87-hwqh-73pj](https://github.com/advisories/GHSA-mj87-hwqh-73pj), [GHSA-pp6c-gr5w-3c5g](https://github.com/advisories/GHSA-pp6c-gr5w-3c5g), [GHSA-v9pg-7xvm-68hf](https://github.com/advisories/GHSA-v9pg-7xvm-68hf). |

La auditoría específica antes reportó **22 entradas** en ambos pins (salida 1);
la auditoría específica después reportó `No known vulnerabilities found`
(salida 0). Las entradas incluyen aliases duplicados del proveedor.

La auditoría de todo el entorno (`.\.venv\Scripts\pip-audit.exe --format
columns`) reportó **12 entradas** en `pytest==9.0.2` y `starlette==0.50.0`
(salida 1; seis IDs únicos: `PYSEC-2026-1845`, `PYSEC-2026-161`,
`PYSEC-2026-2281`, `PYSEC-2026-2280`, `PYSEC-2026-249`, `PYSEC-2026-248`).
No se silenciaron. `pytest` tiene fix indicado 9.0.3; los fixes indicados para
Starlette son 1.x, fuera del rango declarado por FastAPI 0.128.2. No se
actualizaron porque requieren ampliar el alcance más allá de H-API-05 y del
plan aprobado. Este resultado queda pendiente, no se presenta como auditoría
completa limpia.

## Limitaciones

- No se verificó el emisor JWT real ni la configuración de despliegue; auth/ y
  servicios externos quedaron fuera del ámbito.
- No se ejecutó un scanner configurado por el proyecto; se usó `pip-audit`
  como herramienta local y su instalación no se añadió a los manifiestos.
- La suite previa a esta ampliación alcanzó 97%; la suite actualizada alcanza
  98% de cobertura branch-aware total de `app`. Cada módulo de producción
  reportado está por encima de 85%; no se calculó cobertura diferencial del
  diff. En esta ampliación no se cambió código de producción.
