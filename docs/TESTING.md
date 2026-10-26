# Evidencia de pruebas

## auth

### Matriz de cobertura por endpoint

| endpoint | caso | tipo | resultado esperado |
|---|---|---|---|
| `GET /q/health` | Consulta de salud sin autenticación | Camino feliz | `200`, `status=UP`, `checks` presente en el JSON |
| `GET /q/health` | Consulta de salud enviando header `Authorization` | Caso borde | `200`, el endpoint ignora autenticación y responde salud |
| `GET /q/health` | Health check en estado DOWN (perfil de prueba) | Error (`503`) | `503`, `status=DOWN`, `checks` incluye `forced-readiness-check` en `DOWN` |
| `POST /auth/login` | Credenciales válidas (`student`/`Workshop123!`) | Camino feliz | `200`, devuelve `token`, `expiresIn=3600`, `user` y `debug` |
| `POST /auth/login` | Usuario con comilla simple (`unknown-user'`) | Caso borde | `401`, no rompe la consulta y no autentica |
| `POST /auth/login` | `username` en blanco (`"   "`) | Caso borde | `400`, `message="Username is required"` |
| `POST /auth/login` | `password` en blanco (`"   "`) | Caso borde | `400`, `message="Password is required"` |
| `POST /auth/login` | Body `null` | Error (`400`) | `400`, `message="Request is null"` |
| `POST /auth/login` | `username` ausente | Error (`400`) | `400`, `message="Username is required"` |
| `POST /auth/login` | `password` ausente | Error (`400`) | `400`, `message="Password is required"` |
| `POST /auth/login` | Credenciales inválidas | Error (`401`) | `401`, `message="Invalid user <username> or password"` |
| `POST /auth/login` | Excepción interna en autenticación | Error (`500`) | `500`, `message="Internal server error"` sin detalle de excepción |

### Código de los tests (auth)

- [AuthResourceTest.java](C:/Users/Usuario/Documents/T23842-fernando-gordillo/ai-labs/auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java)
  - [reportsHealthWithoutAuthentication](C:/Users/Usuario/Documents/T23842-fernando-gordillo/ai-labs/auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java:50)
  - [ignoresAuthorizationHeaderOnHealthEndpoint](C:/Users/Usuario/Documents/T23842-fernando-gordillo/ai-labs/auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java:60)
  - [logsInWithWorkshopUser](C:/Users/Usuario/Documents/T23842-fernando-gordillo/ai-labs/auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java:70)
  - [rejectsInvalidCredentials](C:/Users/Usuario/Documents/T23842-fernando-gordillo/ai-labs/auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java:86)
  - [rejectsUsernameContainingQuoteAsInvalidCredentials](C:/Users/Usuario/Documents/T23842-fernando-gordillo/ai-labs/auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java:97)
  - [rejectsMissingUsername](C:/Users/Usuario/Documents/T23842-fernando-gordillo/ai-labs/auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java:107)
  - [rejectsBlankUsername](C:/Users/Usuario/Documents/T23842-fernando-gordillo/ai-labs/auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java:118)
  - [rejectsMissingPassword](C:/Users/Usuario/Documents/T23842-fernando-gordillo/ai-labs/auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java:129)
  - [rejectsBlankPassword](C:/Users/Usuario/Documents/T23842-fernando-gordillo/ai-labs/auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java:140)
  - [rejectsNullRequest](C:/Users/Usuario/Documents/T23842-fernando-gordillo/ai-labs/auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java:151)
  - [returnsGenericInternalServerErrorWithoutExceptionDetails](C:/Users/Usuario/Documents/T23842-fernando-gordillo/ai-labs/auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java:293)

- [HealthEndpointDownTest.java](C:/Users/Usuario/Documents/T23842-fernando-gordillo/ai-labs/auth/src/test/java/com/bcp/workshop/auth/HealthEndpointDownTest.java)
  - [returnsServiceUnavailableWhenHealthIsDown](C:/Users/Usuario/Documents/T23842-fernando-gordillo/ai-labs/auth/src/test/java/com/bcp/workshop/auth/HealthEndpointDownTest.java:15)
- [HealthDownTestProfile.java](C:/Users/Usuario/Documents/T23842-fernando-gordillo/ai-labs/auth/src/test/java/com/bcp/workshop/auth/HealthDownTestProfile.java)
  - Activa `auth.test.force-health-down=true` para forzar el `503` en `/q/health`.
- [ToggleableReadinessCheck.java](C:/Users/Usuario/Documents/T23842-fernando-gordillo/ai-labs/auth/src/test/java/com/bcp/workshop/auth/ToggleableReadinessCheck.java)
  - Health check de prueba que devuelve `UP`/`DOWN` según propiedad de configuración.

### Comandos ejecutados y resultados reales

1) **Ejecución inicial (fallida por entorno)**

- Directorio de ejecución: raíz `ai-labs/`
- Comando:
  ```powershell
  python tools/lab.py test auth
  ```
- Código de salida: `1`
- Resultado real:
  - `BUILD FAILURE`
  - `Tests run: 24, Failures: 3, Errors: 4, Skipped: 0`
  - Causa principal observada: variable de entorno `AUTH_TOKEN_SECRET` no configurada para pruebas que emiten/verifican JWT.

2) **Ejecución de validación (exitosa)**

- Directorio de ejecución: raíz `ai-labs/`
- Comando:
  ```powershell
  $env:AUTH_TOKEN_SECRET='LocalTestingSecret-AtLeast-32-Bytes-Value'; python tools/lab.py test auth
  ```
- Código de salida: `0`
- Resultado real:
  - `BUILD SUCCESS`
  - `HealthEndpointDownTest`: `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`
  - `AuthResourceTest`: `Tests run: 16, Failures: 0, Errors: 0, Skipped: 0`
  - `LegacyTokenUtilTest`: `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`
  - `PasswordHasherTest`: `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`
  - Total: `Tests run: 24, Failures: 0, Errors: 0, Skipped: 0`

### Cobertura (método y resultado)

- Método de medición:
  - `mvn verify` (invocado por `python tools/lab.py test auth`) genera JaCoCo en [jacoco.csv](C:/Users/Usuario/Documents/T23842-fernando-gordillo/ai-labs/auth/target/site/jacoco/jacoco.csv).
  - Se calculó cobertura de líneas por clase relacionada al contrato de endpoints (`AuthResource`, `AuthService`, `LegacyTokenUtil`, `LegacyDatabase`) como:  
    `LINE_COVERED / (LINE_COVERED + LINE_MISSED)`.

- Resultado:
  - `AuthResource`: `21/21` = `100.00%`
  - `AuthService`: `13/13` = `100.00%`
  - `LegacyTokenUtil`: `70/76` = `92.11%`
  - `LegacyDatabase`: `61/66` = `92.42%`
  - Total ponderado de clases de endpoint: `165/176` = `93.75%`

- Conclusión:
  - La cobertura del código de endpoint medido está por encima del umbral de `85%`.

### Fallos, pendientes y pruebas propuestas

#### Fallos
- La primera ejecución falló por configuración de entorno (`AUTH_TOKEN_SECRET` ausente), no por una regresión funcional del endpoint.

#### Pendientes
- No hay pruebas pendientes para los endpoints de `auth` documentados en [ENDPOINTS.md](C:/Users/Usuario/Documents/T23842-fernando-gordillo/ai-labs/docs/ENDPOINTS.md) en este alcance.

#### Pruebas propuestas
- Ninguna adicional obligatoria para cumplir la matriz actual de endpoints `auth`; el conjunto mínimo de camino feliz, bordes y errores documentados quedó ejecutado.

