# Evidencia de pruebas

## auth

### Matriz de cobertura por endpoint

| endpoint | caso | tipo | resultado esperado |
|---|---|---|---|
| `GET /q/health` | Consulta de salud sin autenticación | Camino feliz | `200`, `status=UP`, `checks` presente en el JSON |
| `GET /q/health` | Consulta de salud enviando header `Authorization` | Caso borde | `200`, el endpoint ignora autenticación y responde salud |
| `GET /q/health` | Health check en estado DOWN (perfil de prueba) | Error (`503`) | `503`, `status=DOWN`, `checks` incluye `forced-readiness-check` en `DOWN` |
| `POST /auth/login` | Credenciales válidas (`student`/`Workshop123!`) | Camino feliz | `200`, devuelve `token`, `expiresIn`, `user` y `debug`; `expiresIn` coincide con `exp - now` dentro de 1 segundo |
| `POST /auth/login` | `client` ausente, nulo o en blanco | Validación | `400`, `message="Client is required"` |
| `POST /auth/login` | Usuario con comilla simple (`unknown-user'`) | Caso borde | `401`, no rompe la consulta y no autentica |
| `POST /auth/login` | `username` en blanco (`"   "`) | Caso borde | `400`, `message="Username is required"` |
| `POST /auth/login` | `password` en blanco (`"   "`) | Caso borde | `400`, `message="Password is required"` |
| `POST /auth/login` | Body `null` | Error (`400`) | `400`, `message="Request is null"` |
| `POST /auth/login` | `username` ausente | Error (`400`) | `400`, `message="Username is required"` |
| `POST /auth/login` | `password` ausente | Error (`400`) | `400`, `message="Password is required"` |
| `POST /auth/login` | Credenciales inválidas | Error (`401`) | `401`, `message="Invalid user <username> or password"` |
| `POST /auth/login` | Excepción interna con marcadores sintéticos de token/secreto | Error (`500`) | `500`, mensaje genérico y ningún marcador sensible en los logs capturados |
| JWT | Claims, firma y expiración | Camino feliz/borde | Claims requeridos presentes; firma alterada, estructura malformada y token expirado se rechazan |
| JWT | Secreto de firma | Configuración | Secreto ausente, vacío o menor de 32 bytes se rechaza; 32 bytes se aceptan |

### Código de los tests (auth)

- [AuthResourceTest.java](../auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java)
  - `logsInWithWorkshopUser` comprueba que el claim `client` coincide con la solicitud y que `expiresIn` concuerda con `exp`.
  - `rejectsMissingClient`, `rejectsNullClient` y `rejectsBlankClient` comprueban que no se emite token si falta el cliente válido.
  - `returnsGenericInternalServerErrorWithoutExceptionDetails` comprueba respuesta genérica y que los logs capturados no contienen token ni secreto.
- [LegacyTokenUtilTest.java](../auth/src/test/java/com/bcp/workshop/auth/LegacyTokenUtilTest.java)
  - `createsSignedTokenWithExpectedClaimsAndExpiration`, `rejectsAlteredSignature` y `rejectsExpiredToken` cubren claims y vigencia.
  - `requiresConfiguredSecretOfAtLeastThirtyTwoBytes` cubre secreto ausente, vacío y longitud mínima.

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

- Fecha: `2026-10-29`
- Directorio de ejecución: raíz `ai-labs/`
- Comando:
  ```powershell
  $env:AUTH_TOKEN_SECRET='LocalTestingSecret-AtLeast-32-Bytes-Value'; $env:QUARKUS_HTTP_TEST_PORT='0'; python tools/lab.py test auth
  ```
- El secreto usado es sintético y exclusivo de esta validación local; no es un valor de despliegue.
- Código de salida: `0`
- Resultado real:
  - `BUILD SUCCESS`
  - `HealthEndpointDownTest`: `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`
  - `AuthResourceTest`: `Tests run: 19, Failures: 0, Errors: 0, Skipped: 0`
  - `LegacyTokenUtilTest`: `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`
  - `PasswordHasherTest`: `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`
  - Total: `Tests run: 28, Failures: 0, Errors: 0, Skipped: 0`

### Cobertura (método y resultado)

- Método de medición:
  - `mvn verify` (invocado por `python tools/lab.py test auth`) genera JaCoCo en [jacoco.csv](C:/Users/Usuario/Documents/T23842-fernando-gordillo/ai-labs/auth/target/site/jacoco/jacoco.csv).
  - Se calculó cobertura de líneas por clase relacionada al contrato de endpoints (`AuthResource`, `AuthService`, `LegacyTokenUtil`, `LegacyDatabase`) como:  
    `LINE_COVERED / (LINE_COVERED + LINE_MISSED)`.

- Resultado:
  - `AuthResource`: `26/26` = `100.00%`
  - `AuthService`: `17/17` = `100.00%`
  - `LegacyTokenUtil`: `77/81` = `95.06%`
  - `LegacyDatabase`: `61/66` = `92.42%`
  - Total ponderado de clases de endpoint: `181/190` = `95.26%`

- Conclusión:
  - La cobertura del código de endpoint medido está por encima del umbral de `85%`.

3) **Verificación integrada del Issue #25**

- Fecha: `2026-10-29`
- Directorio de ejecución: `auth/`
- Comando:
  ```powershell
  $env:AUTH_TOKEN_SECRET='LocalTestingSecret-AtLeast-32-Bytes-Value'; $env:QUARKUS_HTTP_TEST_PORT='0'; mvn verify
  ```
- El secreto usado es sintético y exclusivo de esta validación local; el puerto HTTP de prueba se asignó dinámicamente.
- Código de salida: `0`
- Resultado real: `BUILD SUCCESS`; 28 pruebas, 0 fallos, 0 errores y 0 omitidas:
  - `HealthEndpointDownTest`: 1
  - `AuthResourceTest`: 19
  - `LegacyTokenUtilTest`: 5
  - `PasswordHasherTest`: 3
- Cobertura de líneas JaCoCo de las clases de producción modificadas: `AuthResource` 26/26 (100%), `AuthService` 17/17 (100%) y `LegacyTokenUtil` 77/81 (95.06%); total ponderado 120/124 (96.77%).

### Fallos, pendientes y pruebas propuestas

#### Fallos
- La primera ejecución falló por configuración de entorno (`AUTH_TOKEN_SECRET` ausente), no por una regresión funcional del endpoint.

#### Pendientes
- No hay pruebas pendientes para los endpoints de `auth` documentados en [ENDPOINTS.md](C:/Users/Usuario/Documents/T23842-fernando-gordillo/ai-labs/docs/ENDPOINTS.md) en este alcance.

#### Pruebas propuestas
- Ninguna adicional obligatoria para cumplir la matriz actual de endpoints `auth`; el conjunto mínimo de camino feliz, bordes y errores documentados quedó ejecutado.
