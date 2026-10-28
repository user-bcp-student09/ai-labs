# Revision local de autorizacion

- PR: https://github.com/user-bcp-student03/ai-labs/pull/22
- Repositorio y numero: user-bcp-student03/ai-labs, #22
- Base: main @ 099fdce769bbd781f9043b071d9cd9955c2a8b55
- Cabeza: fix/auth-hs256-jwt @ 5c7d2867ff0ee13546240f0dc44a941c69cea130
- Criterio: autorizacion de acceso y modificacion de recursos.
- Cobertura y limitaciones: diff completo obtenido por MCP (`get_files` pagina 1 con 22 archivos, pagina 2 vacia). Se verifico consistencia de base/cabeza al consultar metadata antes y despues del diff. Se revisaron cambios de runtime en `auth/src/main/*` y pruebas en `auth/src/test/*` para criterio de autorizacion + Clean Code + patron AAA. MCP no entrego enlace directo por linea en comentarios de diff; evidencia anclada con URL del PR + SHA base/cabeza + archivo/lado/linea/fragmento.

## Observaciones

### Condicion compleja con negaciones reduce legibilidad de regla critica de validez — riesgo por confirmar
- Evidencia del diff: `auth/src/main/java/com/bcp/workshop/auth/JwtTokenService.java`, cabeza, linea 84, `if (issuedAt.isAfter(now) || !expiresAt.isAfter(now) || !expiresAt.isAfter(issuedAt) || Duration.between(issuedAt, expiresAt)... )`; PR https://github.com/user-bcp-student03/ai-labs/pull/22 (base `099fdce769bbd781f9043b071d9cd9955c2a8b55`, cabeza `5c7d2867ff0ee13546240f0dc44a941c69cea130`).
- Analisis: no demuestra bypass de autorizacion por si solo, pero la combinacion de varias negaciones (`!`) en una sola condicion dificulta entender rapidamente la regla de validez temporal del token; esto eleva riesgo de errores futuros en mantenimiento de controles de acceso.
- Confirmacion: refactorizar a predicados positivos o guard clauses (por ejemplo `isExpired`, `hasFutureIat`, `hasInvalidLifetime`) y verificar equivalencia ejecutando `mvn test` en `auth/`; resultado esperado: mismas pruebas en verde y misma semantica de rechazo/aceptacion de tokens.
- Estado de la prueba: pendiente.

### Prueba de login mezcla dos actos (endpoint + verificacion criptografica) y debilita AAA estricto — riesgo por confirmar
- Evidencia del diff: `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java`, cabeza, linea 25, `String token = given()...post("/auth/login")...extract().path("token");` y linea 38, `JwtTokenService.VerifiedToken claims = tokenService.verify(token);`.
- Analisis: la prueba valida contrato HTTP y luego ejecuta una segunda accion relevante (`verify`) dentro del mismo test. Aunque funcionalmente util, en AAA estricto queda menos claro el limite entre Act y Assert; separar en dos pruebas puede mejorar mantenibilidad y diagnostico.
- Confirmacion: separar en (a) prueba de contrato del endpoint y (b) prueba de contenido/validez del token sobre valor obtenido; ejecutar `mvn test` en `auth/`; resultado esperado: mismas garantias funcionales con estructura AAA mas clara.
- Estado de la prueba: pendiente.

## Comprobaciones realizadas

- Criterios de entrada:
  - URL del PR presente y accesible.
  - Herramientas MCP disponibles.
  - Alcance aplicado a autorizacion, y adicionalmente a Clean Code/AAA segun skill.
- Consistencia de referencias:
  - Base `main` @ `099fdce769bbd781f9043b071d9cd9955c2a8b55`.
  - Cabeza `fix/auth-hs256-jwt` @ `5c7d2867ff0ee13546240f0dc44a941c69cea130`.
  - Sin cambios detectados de SHAs durante la consulta.
- Cambios de autorizacion revisados:
  - Eliminacion de emision legacy sin firma (`LegacyTokenUtil`).
  - Introduccion de verificacion de firma/claims/tiempos en `JwtTokenService.verify`.
  - Emision de token en `LoginService` delegada al nuevo servicio JWT.
- Resultado de autorizacion:
  - No se detectaron defectos demostrados de acceso no autorizado en el diff revisado.
- Estado de pruebas de esta revision:
  - No se ejecutaron comandos adicionales; las pruebas asociadas a observaciones de mantenibilidad/AAA quedan pendientes.

Revision local limitada al criterio indicado; no constituye una aprobacion del PR.
