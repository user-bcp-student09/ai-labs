# Contrato de endpoints — API

Este documento conserva el contrato observado antes de la implementación y
describe el contrato implementado. Los ejemplos de request usan
`<JWT>` únicamente como marcador: reemplázalo por un JWT válido emitido para
este servicio.

## Contrato observado antes del cambio

La API aplicaba un header `Authorization` con token Base64
`username|role|expiración|secreto`. El parámetro `owner` de los GET seleccionaba
el dueño consultado y el `owner` del body de creación decidía el dueño asignado.
La carga guardaba el nombre del archivo recibido sin allowlist ni límite. La
descripción de estas observaciones se conserva en `HALLAZGOS.md`.

| método y ruta | comportamiento y respuesta observados |
|---|---|
| `GET /api/tasks` | Aceptaba `owner` opcional y devolvía una lista de tareas. Cada tarea contenía `id`, `title`, `description`, `status`, `owner`, `priority` y `createdAt`. |
| `POST /api/tasks` | Aceptaba un objeto JSON; devolvía `201` y la tarea creada con los campos de la lista. |
| `GET /api/summary` | Aceptaba `owner` opcional y devolvía `owner`, `total`, `byStatus` y `highPriorityOpen`. |
| `POST /api/requirements` | Aceptaba el campo multipart `file`; devolvía `201` con `owner`, `fileName`, `storedAt` y `size`. |
| `GET /api/backend-health` | Consultaba `AUTH_HEALTH_URL` (por defecto `http://localhost:8080/q/health`) y devolvía `requestedBy`, `status` y `body`. |

## Contrato acordado e implementado

### Autenticación común

Todos los endpoints requieren el header HTTP exacto:

```http
Authorization: Bearer <JWT>
```

Se acepta solo HS256. La firma, expiración (`exp`), emisor (`iss`) y audiencia
(`aud`) se verifican. Son obligatorios `sub`, `role`, `exp`, `iss` y `aud`;
`sub` y `role` deben ser cadenas no vacías. Los tokens Base64 del contrato
observado se rechazan.

La configuración se lee del entorno y no tiene valores predeterminados:

| variable | requisito |
|---|---|
| `WORKSHOP_JWT_SECRET` | Clave HS256 de al menos 32 bytes. |
| `WORKSHOP_JWT_ISSUER` | Valor esperado del claim `iss`. |
| `WORKSHOP_JWT_AUDIENCE` | Valor esperado del claim `aud`. |

Si falta alguna variable requerida o la clave es menor de 32 bytes, la API
responde `500` con `{"detail":"Authentication is not configured"}`. Token
ausente o inválido: `401` con `{"detail":"Invalid token"}`. No se verificó la
emisión real ni la coordinación con un servicio externo; deben configurarse
emisor, audiencia y clave compatibles antes de integrar el despliegue. No
incluyas una clave real en ejemplos o en el repositorio.

### `GET /api/tasks`

1. **Qué hace:** devuelve las tareas que pertenecen a la identidad autenticada.
2. **Autenticación:** requerida; `Authorization: Bearer <JWT>`.
3. **Request:**

   | campo/parámetro | tipo | obligatorio | descripción |
   |---|---|---|---|
   | `owner` (query) | cadena | no | Se acepta por compatibilidad, pero se ignora y no selecciona al dueño. |

   No lleva body. Ejemplo completo:

   ```http
   GET /api/tasks?owner=trainer HTTP/1.1
   Host: localhost:8000
   Authorization: Bearer <JWT>
   ```

4. **Response exitosa:** `200 OK`, array JSON (puede estar vacío).

   | campo | tipo | descripción |
   |---|---|---|
   | `id` | entero | Identificador de la tarea. |
   | `title` | cadena | Título. |
   | `description` | cadena | Descripción. |
   | `status` | cadena | Estado. |
   | `owner` | cadena | Usuario del claim `sub` autenticado. |
   | `priority` | cadena | Prioridad. |
   | `createdAt` | cadena | Fecha de creación almacenada por SQLite. |

   Ejemplo completo:

   ```json
   [
     {
       "id": 1,
       "title": "Refactor login",
       "description": "Separar responsabilidades en frontend",
       "status": "TODO",
       "owner": "student",
       "priority": "HIGH",
       "createdAt": "2026-11-02 12:00:00"
     }
   ]
   ```

5. **Errores:**

   | estado | cuándo ocurre | ejemplo de cuerpo |
   |---|---|---|
   | `401 Unauthorized` | Header ausente o incorrecto, o token inválido/expirado. | `{"detail":"Invalid token"}` |
   | `500 Internal Server Error` | Falta configuración JWT o la clave tiene menos de 32 bytes. | `{"detail":"Authentication is not configured"}` |

6. **Seguridad:** el filtro usa siempre el `sub` del JWT verificado. El query
   `owner` no puede consultar datos de otro usuario.

### `POST /api/tasks`

1. **Qué hace:** crea una tarea para el usuario autenticado.
2. **Autenticación:** requerida; `Authorization: Bearer <JWT>`.
3. **Request:** `Content-Type: application/json`.

   | campo/parámetro | tipo | obligatorio | descripción |
   |---|---|---|---|
   | `title` | cadena | no | Título; valor predeterminado `""`. |
   | `description` | cadena | no | Descripción; valor predeterminado `""`. |
   | `status` | cadena | no | Estado; valor predeterminado `"TODO"`. |
   | `priority` | cadena | no | Prioridad; valor predeterminado `"MEDIUM"`. |
   | otros campos, incluido `owner` | campo extra | no | Se ignoran; no forman parte del modelo de entrada ni determinan al dueño. |

   Ejemplo completo:

   ```http
   POST /api/tasks HTTP/1.1
   Host: localhost:8000
   Authorization: Bearer <JWT>
   Content-Type: application/json

   {
     "title": "Add tests",
     "description": "Improve coverage",
     "status": "TODO",
     "priority": "HIGH",
     "owner": "trainer"
   }
   ```

4. **Response exitosa:** `201 Created`.

   | campo | tipo | descripción |
   |---|---|---|
   | `id` | entero | Identificador asignado. |
   | `title` | cadena | Título guardado. |
   | `description` | cadena | Descripción guardada. |
   | `status` | cadena | Estado guardado. |
   | `owner` | cadena | Usuario del claim `sub`, no el campo extra del request. |
   | `priority` | cadena | Prioridad guardada. |
   | `createdAt` | cadena | Fecha de creación almacenada por SQLite. |

   Ejemplo completo:

   ```json
   {
     "id": 4,
     "title": "Add tests",
     "description": "Improve coverage",
     "status": "TODO",
     "owner": "student",
     "priority": "HIGH",
     "createdAt": "2026-11-02 12:00:00"
   }
   ```

5. **Errores:**

   | estado | cuándo ocurre | ejemplo de cuerpo |
   |---|---|---|
   | `401 Unauthorized` | Header ausente o incorrecto, o token inválido/expirado. | `{"detail":"Invalid token"}` |
   | `422 Unprocessable Entity` | Body ausente, JSON no válido o campo conocido no es cadena; por ejemplo, `title` numérico. | `{"detail":[{"type":"string_type","loc":["body","title"],"msg":"Input should be a valid string","input":123}]}` |
   | `500 Internal Server Error` | Falta configuración JWT o la clave tiene menos de 32 bytes. | `{"detail":"Authentication is not configured"}` |

6. **Seguridad:** `owner` y otros campos extra se ignoran. El dueño se deriva
   de la identidad verificada y los valores se envían a SQLite mediante
   parámetros.

### `GET /api/summary`

1. **Qué hace:** resume las tareas del usuario autenticado por estado y cuenta
   las tareas de prioridad alta que siguen abiertas.
2. **Autenticación:** requerida; `Authorization: Bearer <JWT>`.
3. **Request:**

   | campo/parámetro | tipo | obligatorio | descripción |
   |---|---|---|---|
   | `owner` (query) | cadena | no | Se acepta por compatibilidad, pero se ignora. |

   No lleva body. Ejemplo completo:

   ```http
   GET /api/summary?owner=trainer HTTP/1.1
   Host: localhost:8000
   Authorization: Bearer <JWT>
   ```

4. **Response exitosa:** `200 OK`.

   | campo | tipo | descripción |
   |---|---|---|
   | `owner` | cadena | Usuario del claim `sub` autenticado. |
   | `total` | entero | Total de tareas de ese usuario. |
   | `byStatus` | objeto de cadena a entero | Conteo agrupado por `status`. |
   | `highPriorityOpen` | entero | Tareas con `priority` `"HIGH"`, excluidos los estados `"DONE"` y `"ARCHIVED"`. |

   Ejemplo completo:

   ```json
   {
     "owner": "student",
     "total": 2,
     "byStatus": {
       "TODO": 1,
       "IN_PROGRESS": 1
     },
     "highPriorityOpen": 1
   }
   ```

5. **Errores:**

   | estado | cuándo ocurre | ejemplo de cuerpo |
   |---|---|---|
   | `401 Unauthorized` | Header ausente o incorrecto, o token inválido/expirado. | `{"detail":"Invalid token"}` |
   | `500 Internal Server Error` | Falta configuración JWT o la clave tiene menos de 32 bytes. | `{"detail":"Authentication is not configured"}` |

6. **Seguridad:** consultas y conteos se limitan al `sub` del JWT. El parámetro
   `owner` no permite acceso a datos de otro usuario.

### `POST /api/requirements`

1. **Qué hace:** guarda un archivo de requisitos de texto asociado al usuario
   autenticado.
2. **Autenticación:** requerida; `Authorization: Bearer <JWT>`.
3. **Request:** `Content-Type: multipart/form-data`.

   | campo/parámetro | tipo | obligatorio | descripción |
   |---|---|---|---|
   | `file` | archivo multipart | sí | Se normaliza el nombre a basename. Solo se permite `.txt`; máximo 5 MiB (5,242,880 bytes). |

   Ejemplo completo:

   ```bash
   curl -X POST "http://localhost:8000/api/requirements" \
     -H "Authorization: Bearer <JWT>" \
     -F "file=@requirements.txt;type=text/plain"
   ```

4. **Response exitosa:** `201 Created`.

   | campo | tipo | descripción |
   |---|---|---|
   | `owner` | cadena | Usuario del claim `sub` autenticado. |
   | `fileName` | cadena | Basename normalizado y guardado. |
   | `storedAt` | cadena | Ruta resultante bajo el directorio configurado de uploads. |
   | `size` | entero | Tamaño del contenido guardado, en bytes. |

   Ejemplo completo con el directorio predeterminado:

   ```json
   {
     "owner": "student",
     "fileName": "requirements.txt",
     "storedAt": "uploads/requirements.txt",
     "size": 24
   }
   ```

5. **Errores:**

   | estado | cuándo ocurre | ejemplo de cuerpo |
   |---|---|---|
   | `400 Bad Request` | Nombre vacío o inválido. | `{"detail":"Invalid file name"}` |
   | `400 Bad Request` | Extensión distinta de `.txt`. | `{"detail":"Only .txt files are allowed"}` |
   | `401 Unauthorized` | Header ausente o incorrecto, o token inválido/expirado. | `{"detail":"Invalid token"}` |
   | `413 Request Entity Too Large` | Contenido mayor de 5 MiB. | `{"detail":"File exceeds the maximum allowed size"}` |
   | `422 Unprocessable Entity` | Falta el campo multipart obligatorio `file`. | `{"detail":[{"type":"missing","loc":["body","file"],"msg":"Field required","input":null}]}` |
   | `500 Internal Server Error` | Falta configuración JWT o la clave tiene menos de 32 bytes. | `{"detail":"Authentication is not configured"}` |

6. **Seguridad:** cualquier directorio en el nombre se elimina y se comprueba
   que el destino resuelto quede dentro del directorio de uploads. Solo se
   valida nombre, extensión y tamaño; el contenido semántico no se inspecciona.
   Los errores inesperados del filesystem no tienen un formato de respuesta
   normalizado.

### `GET /api/backend-health`

1. **Qué hace:** consulta el endpoint de salud de autenticación configurado y
   devuelve su status y cuerpo como texto.
2. **Autenticación:** requerida; `Authorization: Bearer <JWT>`.
3. **Request:** no acepta parámetros ni body. El destino se selecciona con
   `AUTH_HEALTH_URL`; si no se define, se usa
   `http://localhost:8080/q/health`. Timeout de conexión: 3 segundos.

   | campo/parámetro | tipo | obligatorio | descripción |
   |---|---|---|---|
   | — | — | no | No acepta query parameters ni body. |

   Ejemplo completo:

   ```http
   GET /api/backend-health HTTP/1.1
   Host: localhost:8000
   Authorization: Bearer <JWT>
   ```

4. **Response exitosa:** `200 OK` cuando el destino responde, incluso si el
   status HTTP informado por ese backend no es 2xx.

   | campo | tipo | descripción |
   |---|---|---|
   | `requestedBy` | cadena | Usuario del claim `sub` autenticado. |
   | `status` | entero | Status HTTP del backend consultado; `0` si urllib3 produce un error HTTP/de red. |
   | `body` | cadena | Cuerpo del backend decodificado como UTF-8; texto genérico ante error HTTP/de red. |

   Ejemplo completo con respuesta normal del destino:

   ```json
   {
     "requestedBy": "student",
     "status": 200,
     "body": "UP"
   }
   ```

   Ante error HTTP/de red de urllib3, la API sigue respondiendo `200 OK`:

   ```json
   {
     "requestedBy": "student",
     "status": 0,
     "body": "Backend unavailable"
   }
   ```

5. **Errores HTTP de esta API:**

   | estado | cuándo ocurre | ejemplo de cuerpo |
   |---|---|---|
   | `401 Unauthorized` | Header ausente o incorrecto, o token inválido/expirado. | `{"detail":"Invalid token"}` |
   | `500 Internal Server Error` | Falta configuración JWT o la clave tiene menos de 32 bytes. | `{"detail":"Authentication is not configured"}` |

   El error HTTP/de red del backend se representa en la respuesta `200`
   anterior. Errores inesperados, como un cuerpo externo que no se pueda
   decodificar como UTF-8, no tienen una respuesta normalizada.
6. **Seguridad:** `requestedBy` procede del JWT verificado. El cuerpo del
   backend se devuelve como cadena sin transformación; el consumidor no debe
   tratarlo como contenido confiable.

### Preguntas abiertas de integración

- ¿Qué emisor desplegado generará los JWT y qué valores de `iss` y `aud`
  configurará para coincidir con la API? Esta integración no se verificó en
  este alcance.
- ¿Debe definirse un cuerpo estable para errores inesperados de almacenamiento,
  base de datos o decodificación del backend? El código actual no lo define.
- ¿Qué status y cuerpo debe prometer el contrato ante un multipart malformado?
  La ruta solo define explícitamente la validación del campo `file`; la
  respuesta de parseo la controla FastAPI/Starlette.
