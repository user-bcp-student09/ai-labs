# Hallazgos

Los hallazgos H-API-01 a H-API-07 y los acuerdos pendientes del plan fueron
confirmados por el usuario. La implementación está realizada y sus resultados,
incluidas las limitaciones de integración y el análisis SCA, están registrados
en `PLAN.md` y `docs/TESTING.md`.

## API — contrato observado antes de la implementación

`docs/ENDPOINTS.md` no existía al iniciar el trabajo; lo siguiente conserva la
evidencia de discovery y no describe el contrato actual acordado. Antes del
cambio, todos los endpoints listados aplicaban `legacy_identity` al header de
autorización. La función decodificaba el token Base64 como
`username|role|expiración|secreto` y comprobaba el secreto embebido y la
expiración (líneas de `api/app/main.py` antes de la implementación). El contrato
actual está en `docs/ENDPOINTS.md`.

| método y ruta | comportamiento observado |
|---|---|
| `GET /api/tasks` | Acepta `owner` opcional (por defecto, el usuario del token). Devuelve una lista con `id`, `title`, `description`, `status`, `owner`, `priority` y `createdAt`. |
| `POST /api/tasks` | Acepta un objeto JSON; devuelve `201` y la tarea creada con los campos de la lista. |
| `GET /api/summary` | Acepta `owner` opcional (por defecto, el usuario del token). Devuelve `owner`, `total`, `byStatus` y `highPriorityOpen`. |
| `POST /api/requirements` | Acepta un archivo multipart `file`; devuelve `201` con `owner`, `fileName`, `storedAt` y `size`. |
| `GET /api/backend-health` | Consulta `AUTH_HEALTH_URL` (por defecto `http://localhost:8080/q/health`) y devuelve `requestedBy`, `status` y `body`. |

## Matriz de seguimiento

| id | componente | hallazgo | tipo | explicacion | evidencia (archivo:linea) | severidad | estado |
|---|---|---|---|---|---|---|---|
| H-API-01 | api | Token Base64 con secreto embebido y sin firma | Vulnerabilidad | El token solo codificaba sus campos en Base64 y el secreto estaba en el código; quien pudiera construir el valor podía suplantar identidades, sin verificar firma, emisor ni audiencia. | api/app/security.py:18,39,63 | alta | Resuelto: JWT HS256 valida firma, emisor, audiencia y expiración; configuración externa sin secreto por defecto. Emisor real no probado. |
| H-API-02 | api | Filtros e inserts SQL concatenados | Vulnerabilidad | Valores controlados por la solicitud se interpolaban en SQL; podían alterar la consulta o impedir que los datos se almacenaran correctamente. | api/app/repositories.py:59,66,73 | alta | Resuelto: lecturas, inserts y recuperación por ID usan placeholders SQLite. |
| H-API-03 | api | Acceso de cliente a otro `owner` | Vulnerabilidad | El cliente podía elegir `owner` en consultas y al crear tareas, en vez de limitar esas acciones a la identidad del token. | api/app/routes.py:23,30,37 | alta | Resuelto: listado, resumen e inserts derivan `owner` de la identidad verificada; el valor del cliente se conserva solo como campo compatible e ignorado (`api/app/services.py:23,27`). |
| H-API-04 | api | Carga de archivos usa el nombre recibido y no limita tamaño | Vulnerabilidad | Se usaba `file.filename` directamente y se leía el archivo completo, sin límite. | api/app/file_storage.py:27,52,81 | media | Resuelto: normaliza nombres y rutas, permite `.txt`, limita a 5 MiB y verifica que el destino permanezca en el directorio. |
| H-API-05 | api | Dependencias fijadas a `urllib3 2.5.0` y `python-multipart 0.0.21` | Sugerencia | Las versiones del reto estaban fijadas en el manifiesto y resultaron afectadas por advisories consultados. | api/requirements.txt:4-6 | baja | Resuelto para las dependencias del hallazgo: `urllib3==2.8.0`, `python-multipart==0.0.33`; `pip-audit` pasó de 22 hallazgos en esos pins a ninguno. La auditoría completa aún informa vulnerabilidades en `pytest` y `starlette`, fuera del plan aprobado; véase `PLAN.md`. |
| H-API-06 | api | Rutas, persistencia, reglas y serialización concentradas en un archivo | Refactor | `create_app` inicializaba SQLite y definía rutas que ejecutaban SQL y construían respuestas en el mismo módulo. | api/app/main.py:18, api/app/routes.py:16, api/app/repositories.py:12 | baja | Resuelto: se separaron fábrica/lifecycle, rutas, servicios, repositorio, modelos y almacenamiento. |
| H-API-07 | api | Tipado crudo, excepciones amplias y lógica anidada | Refactor | Las rutas recibían `dict[str, Any]`, capturaban `Exception` y `summary` anidaba condiciones. | api/app/models.py:9,18,30, api/app/services.py:31, api/app/security.py:39 | baja | Resuelto: request/response Pydantic, resumen con condiciones planas y errores de backend genéricos. |

## AUTH — candidatos para revisión

### H-AUTH-01 — Consulta de credenciales construida por concatenación
- Estado: Resuelto para el alcance aprobado.
- Evidencia: `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:34-39` usa parámetros SQL para `username` y `password`. `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java:35,55` comprueba una cuenta de prueba con comilla literal y el rechazo de credenciales desconocidas.
- Impacto: una entrada especialmente formada podría alterar la condición de autenticación.
- Prioridad propuesta: Alta.
- Contrato potencialmente afectado: `POST /auth/login` debe autenticar únicamente las credenciales suministradas y conservar sus respuestas previstas de éxito y rechazo.
- Validación ejecutada: `python tools/lab.py test auth` — 3 pruebas aprobadas usando `auth/target/auth-test-workshop.db`. JaCoCo para `LegacyDatabase`: 20/23 líneas (87.0%) y 96/102 instrucciones cubiertas.

### H-AUTH-02 — Credenciales predeterminadas y contraseña almacenada en claro
- Estado: CANDIDATO
- Evidencia: `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:22-24` define la columna `password` e inserta usuarios con contraseñas fijas; `LegacyDatabase.java:32` compara la contraseña recibida directamente con la almacenada.
- Impacto: fuera del entorno local de práctica, las credenciales iniciales podrían permitir acceso; una copia de la base revelaría las contraseñas.
- Prioridad propuesta: Alta si se despliega fuera del laboratorio.
- Contrato potencialmente afectado: autenticación y asociación entre cuenta autenticada, identidad y rol.
- Cómo validarlo: usar una base temporal recién creada para comprobar las cuentas iniciales y verificar que los entornos no locales no las habiliten ni almacenen contraseñas recuperables.

### H-AUTH-03 — Token Base64 con secreto fijo y sin firma
- Estado: CANDIDATO
- Evidencia: `auth/src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java:8,12-15` define un secreto en código, concatena usuario, rol y vencimiento, y solo codifica el valor con Base64.
- Impacto: Base64 no garantiza integridad; el riesgo depende de cómo los consumidores validen los campos del token.
- Prioridad propuesta: Alta, pendiente de verificar consumidores.
- Contrato potencialmente afectado: campos `token` y `expiresIn` de `POST /auth/login`, e identidad/rol derivados del token.
- Cómo validarlo: en una prueba de integración aislada, verificar formato y expiración y comprobar que el consumidor rechace tokens modificados o no firmados.

### H-AUTH-04 — La respuesta de login revela detalles internos
- Estado: CANDIDATO
- Evidencia: `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:54-58` captura cualquier excepción y devuelve su mensaje y clase; la respuesta exitosa agrega `debug` en la línea 48.
- Impacto: respuestas de error podrían revelar detalles internos y la respuesta exitosa incluye información de depuración.
- Prioridad propuesta: Media.
- Contrato potencialmente afectado: formato y contenido de las respuestas de éxito y error de `POST /auth/login`.
- Cómo validarlo: provocar fallos controlados y comprobar que las respuestas externas no incluyan mensajes internos, clases de excepción ni campos de depuración.

### H-AUTH-05 — CORS configurado para cualquier origen
- Estado: CANDIDATO
- Evidencia: `auth/src/main/resources/application.properties:5` configura `/.*/` como origen CORS; la línea 3 configura el host de producción como `0.0.0.0`.
- Impacto: si el servicio se publica en una red, el navegador podría permitir solicitudes cross-origin desde sitios no previstos; el riesgo depende de los endpoints y mecanismos de autenticación expuestos.
- Prioridad propuesta: Media, sujeta al entorno de despliegue.
- Contrato potencialmente afectado: política CORS para los clientes web autorizados.
- Cómo validarlo: comprobar preflight y respuestas CORS con orígenes permitidos y no permitidos usando configuración de producción.
