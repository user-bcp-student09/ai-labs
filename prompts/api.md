AMBITO
Workspace ai-labs abierto en su raiz. Lee codigo solo en api/.
Todas las rutas parten de la raiz. Lee README.md, RETO.md y
docs/ generales. Este pedido es de solo lectura; no escribas archivos.
Git se ejecuta desde la raiz; las pruebas propuestas usan cwd api/.
No leas ni modifiques codigo de auth/ ni web/. Usa IDs H-API-NN y componente api.

CONTEXTO
ai-labs expone GET /api/tasks (y otras rutas) desde api/app/main.py (FastAPI +
SQLite). La identidad llega por el header Authorization (token Bearer base64).
Lee README.md, RETO.md y docs/ENDPOINTS.md si existe. Localiza la ruta desde
el endpoint HTTP hasta connection.execute(...) contra SQLite, incluyendo
parametros Query, Body y Header. No des por cierta la lista de RETO.md.

OBJETIVO
Determinar si parametros de entrada de la API (por ejemplo owner en
GET /api/tasks, y campos de payload/consulta en otras rutas) llegan
concatenados a una consulta SQL.

RESTRICCIONES
No edites codigo ni ejecutes ataques contra servicios remotos. Usa solo
codigo y datos ficticios locales (base SQLite temporal, WORKSHOP_DB_PATH de
prueba). Si falta un archivo o no puedes ejecutar una comprobacion, dilo.
No cambies contratos (endpoints y formatos usados por el frontend) ni leas
otros componentes.

CRITERIOS DE ACEPTACION
Identifica el recorrido de la entrada hasta la consulta con archivo y linea.
Distingue concatenacion, parametrizacion (placeholders ?) y cualquier caso no
comprobado. Propone una prueba local con pytest y TestClient, con token valido,
token invalido y un valor con comilla (por ejemplo owner=x' OR '1'='1), con
resultado esperado segun el contrato observado (200 con lista, 401 por token
invalido, y que la comilla no devuelva tareas de otros usuarios). No afirmes
que la prueba paso si solo la propusiste.

FORMATO DE RESPUESTA
Tabla: afirmacion | evidencia archivo:linea | comprobado o hipotesis |
prueba propuesta. Termina con preguntas abiertas y comandos no ejecutados.