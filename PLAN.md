# Plan de corrección

## Estado de aprobación

El usuario confirmó H-API-01 a H-API-07, el plan completo y los puntos de
decisión que estaban pendientes el 2026-11-02. La implementación quedó
autorizada y se ejecutó. La aprobación no equivale a una prueba de integración
con el emisor externo ni a una revisión independiente de cierre; ambas quedan
visibles como limitaciones.

Decisiones aplicadas: JWT HS256 con claims requeridos `sub`, `role`, `exp`,
`iss` y `aud`; configuración mediante `WORKSHOP_JWT_SECRET`,
`WORKSHOP_JWT_ISSUER` y `WORKSHOP_JWT_AUDIENCE`, sin secreto por defecto y con
clave de al menos 32 bytes; `owner` siempre se deriva del `sub`; cargas solo
`.txt` de hasta 5 MiB.

## API

Al inicio `docs/ENDPOINTS.md` no existía. Se tomaron como referencia los
contratos observados en `HALLAZGOS.md`; el contrato observado y el acordado
después de la corrección se documentan separadamente en `docs/ENDPOINTS.md`.
Autenticación y autorización cambian intencionalmente qué credenciales y datos
se aceptan, según la aprobación del usuario. La integración con el emisor real
no se ejecutó dentro del alcance API.

### H-API-01 — Token Base64 con secreto embebido y sin firma

- **Problema:** `legacy_identity` confía en campos Base64, usa un secreto
  incluido en el código y no valida firma, emisor ni audiencia
  (`api/app/main.py:14-26`).
- **Cambio aplicado:** verificar JWT firmado, con algoritmo permitido
  explícitamente y validación de firma, emisor, audiencia y expiración. Cargar
  clave, emisor y audiencia desde configuración de entorno sin valores secretos
  por defecto; responder con error 401 genérico ante token inválido. PyJWT no
  estaba en `api/requirements.txt`; se agregó `PyJWT==2.15.1` después de revisar
  los avisos y la versión publicada vigente.
- **Archivos tocados:** `api/app/security.py`, `api/app/routes.py`,
  `api/requirements.txt` y `api/tests/test_api.py`.
- **Caracterización antes del cambio:** el 2026-11-02 la suite original aceptó
  la credencial Base64 y rechazó un token inválido (4 pruebas, 91% de cobertura).
  Se comprobó después que JWT válido, claims inválidos y el formato legacy se
  comportaban según la transición definida.
- **Compatibilidad:** el formato JWT no es compatible con el token Base64
  observado; la API rechaza el formato legacy. La integración con el emisor
  externo y la configuración efectiva del despliegue no se probaron porque
  auth/ y los servicios externos están fuera del alcance.

### H-API-02 — Filtros e inserts SQL concatenados

- **Problema:** filtros, campos del insert e identificador de lectura se
  construyen concatenando valores (`api/app/main.py:70-71,84-87,93`).
- **Cambio aplicado:** trasladar SQL al repositorio y usar placeholders de
  SQLite y parámetros para todo valor; conservar columnas, orden, valores por
  defecto y forma de respuesta observados.
- **Archivos tocados:** `api/app/main.py`; módulo nuevo
  `api/app/repositories.py`; `api/tests/test_api.py`.
- **Caracterización antes del cambio:** cubrir listado y resumen filtrados,
  creación y lectura posterior de tareas, campos y valores por defecto.
  Añadir casos con comillas en título y descripción para verificar que se
  conservan como datos y no rompen la operación.

### H-API-03 — Acceso de cliente a otro `owner`

- **Problema:** `owner` de query y body decide el ámbito consultado o el dueño
  asignado, sin ligarlo a la identidad del token
  (`api/app/main.py:68-70,78-85,91-93`).
- **Cambio aplicado:** derivar el dueño exclusivamente de la identidad
  verificada para listar, resumir y crear; no usar `owner` del cliente para
  consultas ni inserts. Mantener rutas, nombres de campos y formas de respuesta.
- **Archivos tocados:** `api/app/routes.py`; módulos nuevos
  `api/app/services.py` y `api/app/repositories.py`; `api/tests/test_api.py`.
- **Caracterización antes del cambio:** registrar respuestas y comportamiento
  actuales para llamadas sin `owner`, con `owner` coincidente y con `owner`
  distinto, tanto en GET como en creación; cubrir que los valores por defecto
  siguen ligados al usuario del token.
- **Decisión confirmada:** se prioriza la identidad autenticada. El parámetro
  `owner` y el campo `owner` del body se aceptan por compatibilidad de forma,
  pero no amplían el ámbito ni determinan el dueño creado.

### H-API-04 — Nombre y contenido de archivo sin controles

- **Problema:** el nombre recibido se concatena con el directorio, se lee todo
  el archivo en memoria y se escribe sin límites (`api/app/main.py:107-112`).
- **Cambio aplicado:** validar y normalizar el nombre, aplicar una allowlist
  de extensiones y un máximo de bytes antes de persistir, y escribir solo
  dentro del directorio configurado. Mantener `POST /api/requirements`, el
  campo multipart `file`, el status 201 y las claves de respuesta existentes.
- **Archivos tocados:** `api/app/routes.py`; módulo nuevo
  `api/app/file_storage.py`; `api/tests/test_api.py`.
- **Caracterización antes del cambio:** conservar una carga válida `.txt` y
  comprobar status, claves de respuesta y archivo persistido. Añadir casos de
  nombre con ruta, extensión no permitida y tamaño excedido para verificar que
  no se escriben fuera del directorio ni se guarda contenido rechazado.
- **Política aplicada:** allowlist `.txt`, máximo de 5 MiB y normalización de
  separadores, nombres reservados y caracteres inválidos; los límites están
  definidos como constantes con nombre.

### H-API-05 — Versiones de dependencias por revisar

- **Problema:** `urllib3==2.5.0` y `python-multipart==0.0.21` están fijadas en
  `api/requirements.txt:4-5`; el estado de advisories no se ha verificado.
- **Cambio aplicado:** consultar advisories confiables y el resultado del
  scanner del proyecto, actualizar solo a versiones corregidas compatibles y
  registrar evidencia; no silenciar alertas ni relajar controles. No agregar
  dependencias sin justificación.
- **Archivos tocados:** `api/requirements.txt` tras la evidencia que confirma
  una actualización necesaria; `api/tests/test_api.py` solo si se requiere
  adaptar una prueba por cambio de comportamiento.
- **Caracterización antes del cambio:** una carga válida multipart y el
  backend-health simulado conservaron status y campos; la suite inicial pasó.
- **Evidencia SCA:** `pip-audit --no-deps` reportó 22 advisories para los pins
  originales `urllib3==2.5.0` y `python-multipart==0.0.21`; con
  `urllib3==2.8.0` y `python-multipart==0.0.33` reportó cero para ambos. Las
  fuentes upstream confirman que 2.8.0 corrige avisos de urllib3 y 0.0.33
  supera las versiones corregidas de python-multipart.
- **Riesgo SCA fuera del plan:** el escaneo de todo el entorno encontró 12
  entradas de advisories en `pytest==9.0.2` y `starlette==0.50.0`. No se
  actualizaron: no están incluidos en H-API-05 y Starlette está sujeto al
  rango de FastAPI fijado. El informe no fue ignorado ni suprimido; queda como
  trabajo pendiente fuera del alcance aprobado.

### H-API-06 — Rutas, persistencia, reglas y serialización en un módulo

- **Problema:** `create_app` inicializa SQLite y las rutas mezclan HTTP,
  consultas, reglas y armado de respuesta (`api/app/main.py:29-56,67-114`).
- **Cambio aplicado:** separar rutas, servicios y repositorio; mantener
  inicialización y ciclo de vida de la conexión centralizados en la fábrica de
  aplicación. Reutilizar mapeos/modelos comunes sin duplicar reglas.
- **Archivos tocados:** `api/app/main.py`; módulos nuevos
  `api/app/routes.py`, `api/app/services.py`, `api/app/repositories.py` y
  `api/app/models.py`; `api/app/file_storage.py`, `api/app/security.py` y
  `api/tests/test_api.py`.
- **Caracterización antes del cambio:** cubrir arranque y tareas semilla,
  métodos/rutas/statuses, respuestas y claves JSON de tareas y resumen, carga
  de archivo y cierre/aislamiento de la base temporal. Ejecutar las pruebas
  antes de mover responsabilidades y repetirlas después.

### H-API-07 — Tipos crudos, errores amplios y lógica anidada

- **Problema:** el body de creación usa `dict[str, Any]`, errores amplios
  filtran detalles y `summary` contiene condicionales anidados
  (`api/app/main.py:25-26,78,94-105,120`).
- **Cambio aplicado:** definir modelos Pydantic de entrada y salida con los
  campos y alias JSON observados; extraer reglas de resumen a una función
  pequeña con condiciones combinadas o retornos anticipados. Manejar errores
  específicos y devolver mensajes genéricos sin exponer texto de excepciones.
  Conservar defaults, códigos HTTP y forma de respuestas para solicitudes
  válidas.
- **Archivos tocados:** `api/app/routes.py`; módulo nuevo
  `api/app/models.py` y, al extraer reglas, `api/app/services.py`;
  `api/tests/test_api.py`.
- **Caracterización antes del cambio:** probar creación con campos requeridos,
  opcionales y defaults, forma/alias de respuesta, resumen por estado y
  prioridad, token inválido con 401 y backend no disponible con la forma de
  respuesta observada. Agregar después casos inválidos para confirmar
  validación Pydantic y errores genéricos sin alterar los casos válidos.

## Secuencia y validación

1. La aprobación del plan quedó confirmada por el usuario. La configuración
   JWT, la política `owner`, los límites de archivo y los pins SCA quedaron
   decididos como se indica arriba; el emisor externo no se ejecutó ni se
   verificó dentro del ámbito API.
2. Se ejecutó la caracterización antes de producción y las pruebas de
   corrección fallaron con el código anterior; los resultados están abajo.
3. Se implementaron los siete hallazgos con cambios en `api/` y se repitieron
   las pruebas. No se modificaron otros componentes.
4. Se crearon `docs/ENDPOINTS.md` y `docs/TESTING.md` después de la validación.
   La suite final obtuvo 97% de cobertura total de `app`, con cada módulo
   modificado entre 93% y 100%; se conservó el umbral de 85%.

## Registro de ejecuciones — componente api

- **H-API-01..07 — línea base inicial:** `pytest --cov=app
  --cov-report=term-missing` desde la raíz no se ejecutó porque `pytest` no
  estaba en PATH. Se preparó el entorno aislado `api/.venv` usando los
  manifiestos existentes.
- **H-API-01..07 — suite previa:** desde `api/`,
  `.\.venv\Scripts\python.exe -m pytest --cov=app --cov-report=term-missing`:
  **4 passed**, cobertura total **91%**.
- **H-API-01..07 — caracterización antes de producción:** mismo comando:
  **8 passed**, cobertura total **95%**. Fijó semilla, formas de respuesta,
  defaults, elección legacy de `owner`, carga `.txt` y backend-health simulado.
- **H-API-01..04, H-API-07 — correcciones antes del cambio:** desde `api/`,
  `.\.venv\Scripts\python.exe -m pytest -q -k 'accepts_signed_jwt or
  rejects_jwt or parameterizes or ignores_client_owner or normalizes_upload
  or rejects_upload_larger or generic_backend or validates_task'`: **9 failed**
  contra la implementación anterior, como se esperaba.
- **H-API-05 — caracterización tras actualizar dependencias, antes de cambiar
  producción:** 8 pruebas seleccionadas pasaron con
  `urllib3==2.8.0` y `python-multipart==0.0.33`.
- **H-API-01..07 — primera suite tras el cambio:** **17 passed, 1 failed**; el
  caso adicional de `role` vacío reveló una validación incompleta. Se corrigió
  para rechazarlo; no se ocultó el fallo.
- **H-API-01..07 — suite final:** desde `api/`,
  `.\.venv\Scripts\python.exe -m pytest --cov=app --cov-report=term-missing`:
  **20 passed**, cobertura total **97%** (227 statements, 4 sin cubrir; 26
  branches, 4 parciales). Los módulos con líneas descubiertas quedaron en
  `file_storage.py` **93%**, `repositories.py` **94%** y `security.py` **94%**;
  los otros cinco módulos alcanzaron 100%. Sin fallos; una advertencia de
  deprecación de `anyio` provino de `starlette.testclient`.
- **H-API-05 — SCA antes:** `.\.venv\Scripts\pip-audit.exe --no-deps -r
  .sca-baseline.txt` evaluó los pins originales y reportó **22 entradas de advisories en
  urllib3 2.5.0 y python-multipart 0.0.21** (salida 1).
- **H-API-05 — SCA después, paquetes del hallazgo:**
  `.\.venv\Scripts\pip-audit.exe --no-deps -r .sca-current.txt` reportó
  **No known vulnerabilities found** (salida 0) para
  `urllib3 2.8.0` y `python-multipart 0.0.33`. Los dos archivos de requisitos
  temporales usados en la comparación se eliminaron.
- **H-API-05 — auditoría completa del entorno:**
  `.\.venv\Scripts\pip-audit.exe --format columns` reportó **12
  entradas de advisories en dos paquetes**, `pytest==9.0.2` y
  `starlette==0.50.0` (salida 1). No se excluyeron ni silenciaron. Corregirlos
  exige cambios fuera del plan aprobado; permanecen pendientes.
- **H-API-01..07 — consistencia:** `.\.venv\Scripts\python.exe -m pip check`:
  **No broken requirements found**. Desde la raíz, `git diff --check`: sin
  errores.
- **H-API-01 — integración:** no se ejecutó contra auth ni contra servicios
  remotos. Las pruebas JWT firman tokens de prueba aislados; el token Base64
  legacy se rechaza. El despliegue necesita coordinar la configuración JWT
  documentada con el emisor antes de integrar.
