# Plan de corrección

## auth

### Alcance y criterio

HALLAZGOS.md contiene siete hallazgos `auth` (H-AUTH-01–H-AUTH-07);
inicialmente se planificaron todos como pendientes. H-AUTH-03 se marcó
Resuelto tras la migración inicial de las filas heredadas. No existe
`docs/ENDPOINTS.md`. Hasta acordar un contrato documentado, las
caracterizaciones conservan el comportamiento observado:
`POST /auth/login`, validaciones 400, login válido 200 con `token`,
`expiresIn`, `user` y `debug`, y credenciales no encontradas 401. No se
modificará código antes de escribir y ejecutar las pruebas de caracterización.

### H-AUTH-01 — Concatenación de credenciales en la consulta SQL

- **Hallazgo de HALLAZGOS.md:** credenciales concatenadas en la consulta del
  login.
- **Problema:** los campos de entrada se interpolan en SQL y se ejecutan con
  `Statement`, permitiendo alterar la consulta.
- **Cambio propuesto:** reemplazar la interpolación por marcadores `?`, usar
  `PreparedStatement` y enlazar username/password con `setString`. Mantener el
  resultado observado de usuario existente/no existente y el contrato HTTP.
- **Archivos previstos:** `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java`;
  `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java`; posible
  test de repositorio nuevo dentro de `auth/src/test/java/com/bcp/workshop/auth/`.
- **Caracterización previa:** probar login válido (200 y campos actuales),
  credenciales inválidas (401) y campos ausentes/vacíos (400) con SQLite local
  aislada. Incluir test directo de persistencia que constate SQL con marcadores
  y enlace de ambos valores; ejecutar y registrar antes del cambio.

### H-AUTH-02 — Token Base64 con secreto embebido y sin firma

- **Hallazgo de HALLAZGOS.md:** token codificado en Base64, con secreto en
  código y sin firma verificable.
- **Problema:** Base64 no autentica ni protege la integridad del contenido.
- **Cambio implementado:** `auth` emite JWT HS256 con `iss`, `aud`, `sub`,
  `role`, `iat` y `exp`, firma con `AUTH_TOKEN_SECRET` y mantiene los campos
  observados `token` y `expiresIn`. Con autorización expresa del solicitante,
  se revisó el consumidor API: se reemplazó la validación del token legado por
  validación JWT HS256 con firma constante, emisor, audiencia, tipos de claims
  y expiración. No se agregó dependencia. La variable `AUTH_TOKEN_SECRET`
  debe configurarse con el mismo valor de al menos 32 bytes en ambos procesos.
- **Archivos previstos:** `auth/src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java`;
  `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java`;
  `auth/src/main/resources/application.properties`;
  `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java`.
- **Caracterización y corrección:** se verificó que el consumidor API rechazaba
  el JWT antes del cambio (401). Después se probaron login con el formato HTTP
  observado, token válido en `/api/tasks`, firma alterada, algoritmo/claims/
  emisor/audiencia/expiración incorrectos y secreto de configuración ausente.
  Los endpoints existentes se mantuvieron; `web/` envía el token como Bearer y
  no requiere cambio porque lo trata como opaco. La revisión de consumidores se
  limitó al recorrido del JWT y no modificó `web/`.
- **Estado:** implementado; evidencia final en el registro de ejecución.

### H-AUTH-03 — Contraseñas almacenadas en texto plano

- **Hallazgo de HALLAZGOS.md:** persistencia y comparación de contraseñas en
  claro.
- **Problema:** la exposición de la base de datos revelaría las credenciales.
- **Cambio implementado:** almacenar y verificar PBKDF2-HMAC-SHA256 con salt
  aleatorio. Durante `LegacyDatabase.initialize()`, recorrer las filas y
  migrar las contraseñas que no coincidan con el formato y estructura de hash
  válidos antes de insertar semillas;
  la actualización compara también el valor previo para no pisar un cambio
  concurrente. La operación es repetible: hashes existentes no se vuelven a
  migrar. No se agregó dependencia criptográfica.
- **Archivos previstos:** `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java`;
  posible clase de hashing bajo `auth/src/main/java/com/bcp/workshop/auth/`;
  pruebas nuevas o existentes bajo `auth/src/test/java/com/bcp/workshop/auth/`.
- **Caracterización previa y corrección:** se agregó
  `migratesLegacyPasswordsDuringDatabaseInitialization` con usuario y clave
  ficticios; el primer intento falló porque el esquema de prueba no estaba
  inicializado. Tras inicializarlo, el test falló en la aserción esperada
  (contraseña persistida seguía sin hash). Tras la migración, el mismo test
  pasa y confirma que la clave heredada verifica contra el hash almacenado.
  `mvn verify` vuelve a cubrir login y almacenamiento de semillas.
- **Estado:** implementado; evidencia final en el registro de ejecución.

### H-AUTH-04 — CORS permisivo y secreto hardcodeado

- **Hallazgo de HALLAZGOS.md:** CORS permite cualquier origen y el secreto del
  token está en código.
- **Problema:** origen no restringido y secreto no rotatable mediante
  configuración.
- **Cambio implementado:** CORS de `auth` se limita al origen expresamente
  confirmado `http://localhost:4200`; el secreto se lee de
  `AUTH_TOKEN_SECRET`, compartido con API, y no se almacena en código.
- **Archivos previstos:** `auth/src/main/resources/application.properties`;
  la clase de token seleccionada para H-AUTH-02; pruebas bajo
  `auth/src/test/java/com/bcp/workshop/auth/`.
- **Caracterización y corrección:** el preflight con `https://unapproved.example`
  recibía 200 y reflejaba el origen con la configuración comodín. Tras el
  cambio, `http://localhost:4200` recibe 200 con encabezado CORS y el origen
  no permitido recibe 403.
- **Estado:** implementado; evidencia final en el registro de ejecución.

### H-AUTH-05 — Versiones de dependencias por evaluar mediante SCA

- **Hallazgo de HALLAZGOS.md:** versiones originales Quarkus 3.27.4 y Commons
  Text 1.9 requerían análisis; el manifiesto por sí solo no confirma un advisory
  aplicable.
- **Problema:** una dependencia afectada podría mantener vulnerabilidades
  conocidas; la exposición concreta aún no está comprobada.
- **Cambio propuesto:** revisar los resultados de CodeQL, Dependency Review y
  análisis disponibles, verificar advisories y árbol de dependencias resuelto;
  actualizar o retirar solo con evidencia y validación de compatibilidad. No
  silenciar controles ni sumar herramientas/dependencias injustificadas.
- **Evidencia SCA consultada:** [Apache Commons Text Security Reports](https://commons.apache.org/proper/commons-text/security.html)
  identifica CVE-2022-42889 en versiones anteriores a 1.10.0. El árbol local
  resolvía 1.9 antes del cambio y 1.10.0 después. Apache también condiciona la
  exposición al uso de `StringSubstitutor` con entrada no confiable; el código
  de `auth` usaba `StringEscapeUtils`, por lo que el riesgo de explotación en
  esta aplicación no se afirma como comprobado.
- **Archivos previstos:** `auth/pom.xml` únicamente si el análisis justifica
  cambios; pruebas/configuración de `auth/` solo si resultan necesarias.
- **Caracterización previa:** registrar árbol de dependencias, versión
  efectiva, estado de compilación y pruebas antes del cambio; repetir controles
  luego. No declarar CVE confirmado hasta tener evidencia del análisis.
- **Corrección y evidencia final:** Commons Text 1.10.0 resuelve la versión
  previa a 1.10.0 afectada por CVE-2022-42889 según Apache. Se actualizó el
  BOM de Quarkus a 3.27.6.1, release de emergencia publicado el 2026-10-19;
  Quarkus indica que incluye Jackson 2.21.7, Netty 4.1.139.Final y Vert.x
  4.5.35 con sus correcciones. El árbol resuelto confirma Quarkus 3.27.6.1,
  Jackson 2.21.7, Netty 4.1.139.Final, SQLite JDBC 3.50.3.0 y Commons Text
  1.10.0. Se consultó el advisory [Apache Commons Text](https://commons.apache.org/proper/commons-text/security.html),
  la nota oficial [Quarkus 3.27.6.1](https://quarkus.io/blog/quarkus-3-27-6-1-released/)
  y la base [OSV](https://osv.dev/). La consulta OSV abarcó 221 dependencias
  Maven resueltas y no devolvió advisories. La tarea OWASP Dependency-Check se
  inició, pero sin NVD API key seguía actualizando 414,758 registros; se
  detuvo sin producir resultado. No se ejecutaron CodeQL ni Dependency Review
  remotos. El resultado OSV no se presenta como reemplazo de esos quality gates.
- **Estado:** actualizado con evidencia OSV/advisories oficiales; las
  comprobaciones remotas y OWASP/NVD quedan explícitamente no ejecutadas.

### H-AUTH-06 — Exposición de detalles internos en errores

- **Hallazgo de HALLAZGOS.md:** el recurso devuelve mensaje y clase de la
  excepción.
- **Problema:** los detalles internos enviados al cliente pueden revelar
  implementación y facilitar el análisis de fallos.
- **Cambio propuesto:** enviar mensaje genérico y conservar detalle técnico
  solo en logging controlado del servidor; mantener las respuestas 400/401
  observadas.
- **Archivos previstos:** `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java`;
  `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java`.
- **Caracterización previa:** probar request nulo, username/password vacío,
  credenciales inválidas y error interno inducido mediante doble de prueba
  local; registrar status y cuerpo actuales antes de cambiar el manejo.

### H-AUTH-07 — Responsabilidades mezcladas en el recurso

- **Hallazgo de HALLAZGOS.md:** el recurso combina validación, persistencia,
  negocio y respuesta HTTP.
- **Problema:** el método anidado concentra cambios y dificulta pruebas y
  mantenimiento.
- **Cambio propuesto:** separar Resource (HTTP), Service (reglas/coordinación)
  y Repository (persistencia), con DTO de salida explícito. Un método por
  responsabilidad, reutilización sin duplicación, guard clauses y constantes
  con nombre para valores como `expiresIn`; conservar el comportamiento
  observado.
- **Archivos previstos:** `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java`;
  `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java`;
  posibles clases nuevas Service/Repository/DTO bajo
  `auth/src/main/java/com/bcp/workshop/auth/`; pruebas bajo
  `auth/src/test/java/com/bcp/workshop/auth/`.
- **Caracterización previa:** probar request nulo/campos ausentes o vacíos,
  login válido e inválido, incluyendo códigos, cuerpo y campos actuales.
  Ejecutar antes de separar capas y mantener como regresión. Cubrir al menos
  85% del código modificado sin reducir JaCoCo ni otros gates.

## Pendientes comunes de auth

- **Estado actual:** H-AUTH-01 a H-AUTH-07 están marcados `Resuelto` en
  HALLAZGOS.md con pruebas y evidencia por hallazgo.
- **Aprobaciones/decisiones:** además de la aprobación inicial de
  H-AUTH-01–07, el solicitante autorizó revisar y modificar únicamente la
  validación JWT de `api/`, confirmó `http://localhost:4200` como origen CORS
  permitido para `auth/` y pidió continuar con todos los pendientes. Ninguna
  autorización se registra como revisión humana independiente.
- No existe `docs/ENDPOINTS.md`; el comportamiento anterior es observado en
  código/prueba, no un contrato acordado.
- `AUTH_TOKEN_SECRET` debe configurarse con el mismo valor, mínimo 32 bytes,
  en los procesos de `auth` y `api`; solo las pruebas usan un valor ficticio.
- CORS de `auth` permite exclusivamente `http://localhost:4200`. No se cambió
  CORS de `api/`; su middleware actual sigue permitiendo cualquier origen y
  queda fuera de la autorización específica para adaptar el validador JWT.
- H-AUTH-05: consulta OSV a 221 dependencias resueltas sin advisories; se
  actualizaron Commons Text y Quarkus. OWASP Dependency-Check/NVD no terminó
  porque la actualización sin API key no completó; CodeQL y Dependency Review
  remotos tampoco se ejecutaron.
- No crear `docs/ENDPOINTS.md` ni `docs/TESTING.md` en este paso. Los comandos y
  resultados siguientes se registran aquí hasta el paso de documentación.
- La revisión independiente de cierre requerida por el flujo del laboratorio
  sigue pendiente; no se considerará reemplazada por esta aprobación ni por
  una revisión de IA.

### Registro de ejecución auth

| Fecha | IDs | Acción | Resultado |
|---|---|---|---|
| 2026-10-26 | H-AUTH-01–H-AUTH-07 | Aprobación explícita del solicitante para proceder. Autorizó JWT y avanzar sin cambiar CORS hasta confirmar orígenes. | Decisión registrada; no es revisión independiente. |
| 2026-10-26 | H-AUTH-01, H-AUTH-03, H-AUTH-06, H-AUTH-07 | `cd auth; $env:AUTH_DB_URL='jdbc:sqlite:./target/auth-characterization.db'; mvn test` (antes de cambios de producto). | 8 pruebas pasaron; caracterizó login, validaciones, almacenamiento heredado y error interno. |
| 2026-10-26 | H-AUTH-01, H-AUTH-02, H-AUTH-03, H-AUTH-06, H-AUTH-07 | `cd auth; $env:AUTH_DB_URL='jdbc:sqlite:./target/auth-characterization.db'; mvn test` (caracterización ampliada). | 9 pruebas pasaron; login válido devolvía token no nulo y la respuesta HTTP mantenía los campos observados; usuario ficticio con comilla no se encontraba con la consulta concatenada; excepción devolvía detalles internos. |
| 2026-10-26 | H-AUTH-01 | `mvn -Dtest=AuthResourceTest#cannotFindUsernameContainingQuoteWithLegacyQuery test` tras parametrizar. | Falló como se esperaba porque la aserción caracterizaba la limitación antigua; el repositorio ya devolvía el usuario. Se actualizó la prueba para esperar el comportamiento correcto. |
| 2026-10-26 | H-AUTH-01 | `mvn test` después de actualizar la expectativa del caso con comilla. | 9 pruebas pasaron. |
| 2026-10-26 | H-AUTH-01, H-AUTH-03 | `mvn test` después de hashing PBKDF2 y migración gradual. | 10 pruebas pasaron; login y almacenamiento/migración a hash verificados. |
| 2026-10-26 | H-AUTH-06 | `mvn -Dtest=AuthResourceTest#returnsGenericInternalServerErrorWithoutExceptionDetails test`. | 1 prueba pasó; respuesta 500 genérica y detalle solo en log del servidor. |
| 2026-10-26 | H-AUTH-01, H-AUTH-03, H-AUTH-06, H-AUTH-07 | `mvn test` después de separar Resource, Service y DTO. | 10 pruebas pasaron; códigos y estructura observada del login se mantuvieron. |
| 2026-10-26 | H-AUTH-01, H-AUTH-03, H-AUTH-06, H-AUTH-07 | `mvn verify` antes de cambio de dependencia/token. | 13 pruebas pasaron; JaCoCo generado. |
| 2026-10-26 | H-AUTH-05 | `mvn dependency:tree -DoutputType=text` antes de actualización. | Comando pasó; árbol resolvió Quarkus 3.27.4 y Commons Text 1.9. |
| 2026-10-26 | H-AUTH-05 | `mvn dependency:tree -DoutputType=text` después de actualizar Commons Text. | Comando pasó; árbol resolvió Quarkus 3.27.4 y Commons Text 1.10.0. |
| 2026-10-26 | H-AUTH-05 | `mvn verify` después de actualizar Commons Text. | 13 pruebas pasaron; build pasó. |
| 2026-10-26 | H-AUTH-02 | `mvn verify` durante primera implementación JWT. | Falló: error de compilación por pasar bytes a una sobrecarga de codificación de texto; se corrigió antes de continuar. |
| 2026-10-26 | H-AUTH-02 | `mvn -Dtest=LegacyTokenUtilTest test` con `AUTH_TOKEN_SECRET` ficticio de prueba. | 3 pruebas pasaron: claims/firma, token alterado y expiración. |
| 2026-10-26 | H-AUTH-01–H-AUTH-07 | `mvn verify` con `AUTH_DB_URL=jdbc:sqlite:./target/auth-characterization.db` y `AUTH_TOKEN_SECRET` ficticio de prueba. | 17 pruebas pasaron; build pasó. JaCoCo por líneas en clases de producción modificadas: AuthResource 100%, AuthService 100%, LegacyTokenUtil 87.2%, PasswordHasher 90.9%, LegacyDatabase 89.6%, LoginResponse 100%. |
| 2026-10-26 | H-AUTH-01–H-AUTH-07 | Repetición final de `mvn verify` tras revisión de formato/imports, con SQLite temporal y `AUTH_TOKEN_SECRET` ficticio. | 17 pruebas pasaron; build y reporte JaCoCo pasaron. |
| 2026-10-26 | H-AUTH-05 | Revisión del advisory oficial Apache Commons Text CVE-2022-42889. | La página oficial indica versiones anteriores a 1.10.0; se actualizó la dependencia directa a 1.10.0. No sustituye CodeQL/Dependency Review ni confirma la seguridad global del árbol. |
| 2026-10-26 | H-AUTH-05 | CodeQL y Dependency Review remotos. | No ejecutados en este recorrido local, según restricción. |
| 2026-10-26 | H-AUTH-02, H-AUTH-03 | Revisión local del diff `main` vs rama de trabajo y de los archivos nuevos; comprobación de documentación de inicio y estado de `AUTH_TOKEN_SECRET`. | H-AUTH-03 vuelve a `Pendiente` porque el cambio migra contraseñas heredadas solo al login, no todas las filas existentes. H-AUTH-02 permanece `Pendiente`: el emisor requiere una variable de entorno que no se documenta/proporciona en el arranque revisado y la compatibilidad de consumidores no se verificó. |
| 2026-10-26 | H-AUTH-03 | `mvn -Dtest=AuthResourceTest#migratesLegacyPasswordsDuringDatabaseInitialization test` con SQLite temporal y `AUTH_TOKEN_SECRET` ficticio, antes de corregir el test. | Falló en setup con `no such table: legacy_users`; no comprobó la migración. |
| 2026-10-26 | H-AUTH-03 | Mismo test focalizado después de inicializar el esquema y antes del cambio de producción. | Falló en la aserción de hash (esperado como caracterización: la fila heredada seguía en claro). |
| 2026-10-26 | H-AUTH-03 | `mvn -Dtest=AuthResourceTest#migratesLegacyPasswordsDuringDatabaseInitialization test` después de migrar filas en `initialize()`. | 1 prueba pasó; verifica que la contraseña no queda en claro y que el hash permite autenticar esa contraseña. |
| 2026-10-26 | H-AUTH-02 | `mvn -Dtest=LegacyTokenUtilTest test` con `AUTH_TOKEN_SECRET` ficticio, después del parser y las validaciones de claims. | 4 pruebas pasaron, incluidos encabezado/algoritmo incorrecto, emisor no permitido y JSON inválido. |
| 2026-10-26 | H-AUTH-02, H-AUTH-03, H-AUTH-01, H-AUTH-06, H-AUTH-07 | `mvn verify` con `AUTH_DB_URL=jdbc:sqlite:./target/auth-final-verification.db` y `AUTH_TOKEN_SECRET` ficticio de prueba. | BUILD SUCCESS; 18 pruebas pasaron. JaCoCo por líneas en clases de producción modificadas: AuthResource 100%, AuthService 100%, LegacyTokenUtil 87.5%, PasswordHasher 90.9%, LegacyDatabase 92.4%, LoginResponse 100%. |
| 2026-10-26 | H-AUTH-02, H-AUTH-03, H-AUTH-01, H-AUTH-06, H-AUTH-07 | Repetición final de `mvn verify` con `AUTH_DB_URL=jdbc:sqlite:./target/auth-final-verification.db` y `AUTH_TOKEN_SECRET` ficticio, después de añadir rechazo de JSON con tokens finales, tipos de claims, hashes malformados y migración idempotente. | BUILD SUCCESS; 18 pruebas pasaron. JaCoCo por líneas: AuthResource 100%, AuthService 100%, LegacyTokenUtil 88.7%, PasswordHasher 95.6%, LegacyDatabase 92.4%, LoginResponse 100%. Todas las clases de producción modificadas superan 85%. |
| 2026-10-26 | H-AUTH-02 | Revisión autorizada del flujo de consumidores JWT en `web/` y `api/`; `api/tests/test_api.py::test_lists_seeded_tasks` con token JWT de prueba antes del cambio. | Antes del cambio el API devolvía 401 al JWT; `web/` solo transporta el token como Bearer y lo trata como opaco. Se autorizó y limitó el cambio a validación JWT en `api/`. |
| 2026-10-26 | H-AUTH-02 | `api\.venv\Scripts\python.exe -m pytest tests/test_api.py` (cwd `api/`) después de actualizar el validador. | 11 pruebas pasaron, con cobertura de `api/app/main.py` de 87%; incluye firma alterada, algoritmo/claims/issuer/audience/expiración incorrectos y secreto ausente. |
| 2026-10-26 | H-AUTH-02 | `LegacyTokenUtilTest` después de agregar audiencia `ai-labs-api` al JWT emisor. | 4 pruebas pasaron; claims y validación del token emisor incluyen audiencia y expiración. |
| 2026-10-26 | H-AUTH-04 | `mvn '-Dtest=AuthResourceTest#allowsOnlyConfiguredCorsOrigin' test` antes del cambio, con allowlist aún comodín. | Falló como caracterización: el origen no aprobado recibió 200 y `Access-Control-Allow-Origin` reflejó ese origen. |
| 2026-10-26 | H-AUTH-04 | `mvn '-Dtest=AuthResourceTest#allowsOnlyConfiguredCorsOrigin,LegacyTokenUtilTest' test` después de restringir CORS a `http://localhost:4200`. | 5 pruebas pasaron; preflight permitido devolvió 200 y el origen no aprobado devolvió 403. |
| 2026-10-26 | H-AUTH-05 | `mvn dependency:tree -DoutputType=text -DoutputFile=target/dependency-tree-sca.txt` tras actualizar a Quarkus 3.27.6.1; consulta OSV por lote a las 221 coordenadas/versiones resueltas. | BUILD SUCCESS; árbol confirmó Quarkus 3.27.6.1, Jackson 2.21.7, Netty 4.1.139.Final, SQLite JDBC 3.50.3.0 y Commons Text 1.10.0. OSV no devolvió advisories para las 221 dependencias. |
| 2026-10-26 | H-AUTH-05 | `mvn 'org.owasp:dependency-check-maven:12.1.0:check' '-Dformat=HTML,JSON' '-DfailBuildOnCVSS=7'`. | No completado: sin NVD API key seguía actualizando la base de 414,758 registros; proceso detenido sin reporte. CodeQL y Dependency Review remotos tampoco ejecutados. |
| 2026-10-26 | H-AUTH-01–H-AUTH-07 | `mvn verify` con Quarkus 3.27.6.1, SQLite temporal y `AUTH_TOKEN_SECRET` ficticio de prueba. | BUILD SUCCESS; 19 pruebas pasaron. JaCoCo por líneas: AuthResource 100%, AuthService 100%, LegacyTokenUtil 89.5%, PasswordHasher 95.6%, LegacyDatabase 92.4%, LoginResponse 100%; todas las clases productivas modificadas superan 85%. |
| 2026-10-26 | H-AUTH-02 | `api\.venv\Scripts\python.exe -m pytest --cov=app --cov-report=term-missing` (cwd `api/`), validación final tras añadir casos duplicados y token sobredimensionado. | 13 pruebas pasaron; cobertura API total 89%, sin dependencias añadidas. |
| 2026-10-26 | H-AUTH-05 | Reconsulta `POST https://api.osv.dev/v1/querybatch` con las 221 coordenadas Maven/versiones únicas del árbol actual `auth/target/dependency-tree-sca.txt`. | No se devolvieron advisories para ninguna coordenada/version. El archivo temporal se eliminó después de documentar el resultado. |

**Desviación acotada:** al modificar el repositorio para H-AUTH-01/H-AUTH-03,
se dejó de convertir un fallo SQL en `null` (401 indistinguible de credenciales
inválidas); ahora se propaga al recurso, que lo registra en servidor y devuelve
500 genérico conforme a H-AUTH-06.
