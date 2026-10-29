# Design

## Context

Ver [proposal.md](./proposal.md) para motivación y alcance; los requisitos observables están en [specs/auth-token/spec.md](./specs/auth-token/spec.md).

El cambio se limita a `auth/` y al contrato de `POST /auth/login`. Actualmente `AuthResource` valida cuerpo, username, password y `client`; `AuthService` consulta credenciales, construye la respuesta y asigna `expiresIn` fijo de 3600; `LegacyTokenUtil` crea y valida un JWT HS256 con `sub`, `client`, `iat`, `exp`, `iss`, `aud` y `role`, y obtiene `AUTH_TOKEN_SECRET` del entorno comprobando un mínimo de 32 bytes. Las pruebas existentes cubren la respuesta de login, errores, claims y expiración. El contrato de `docs/ENDPOINTS.md` aún documenta `client` como opcional y `expiresIn` fijo en 3600; la propuesta identifica los dos cambios de comportamiento solicitados por el Issue #25.

## Goals / Non-Goals

**Goals:**

- Cumplir los requisitos de `auth-token` con un cambio acotado y revisable en el flujo actual.
- Proteger mediante pruebas el formato HTTP existente, los resultados de login, los claims compatibles y la vigencia del token antes y durante el cambio.
- Mantener la compatibilidad con los consumidores del JWT y evitar exposición de material sensible en logs.

**Non-Goals:**

- Reestructurar capas, persistencia, autenticación de credenciales, `api/` o `web/`.
- Alterar endpoints distintos de `/auth/login`, CORS, roles o la forma general de la respuesta exitosa.
- Cambiar la estrategia criptográfica o incorporar una dependencia sin necesidad demostrada por los criterios del Issue.

## Protected Contracts

- Mantener `POST /auth/login`, el código y cuerpo de respuesta documentados para credenciales inválidas, cuerpo nulo y username/password faltantes, y los campos `token`, `expiresIn`, `user` y `debug` en una respuesta exitosa.
- Preservar el escapado de `user.displayName`, `iss=ai-labs-auth`, `aud=ai-labs-api`, el rol, y el uso de JWT firmado HS256 salvo que una revisión de implementación demuestre una incompatibilidad que requiera volver a decidirlo.
- Mantener el secreto mediante `AUTH_TOKEN_SECRET`, su mínimo documentado de 32 bytes y el requisito de no usar un secreto implícito fuera del laboratorio.
- Aplicar únicamente los dos cambios contractuales descritos en la propuesta: `client` debe validarse como requerido y `expiresIn` debe corresponder al tiempo hasta `exp` dentro de 1 segundo. No cambiar otros aspectos por el hecho de que `docs/ENDPOINTS.md` describa el contrato previo.

## Components Affected

- `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java`: validación de `client` y mapeo de errores HTTP.
- `auth/src/main/java/com/bcp/workshop/auth/AuthService.java`: composición de la respuesta y coordinación entre vigencia y token.
- `auth/src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java`: emisión/validación del token y claims de tiempo.
- Pruebas existentes de `auth`, en particular `AuthResourceTest` y `LegacyTokenUtilTest`.
- Documentación del contrato del endpoint y de la configuración de `AUTH_TOKEN_SECRET`, solo donde el cambio de comportamiento o evidencia del Issue requiera mantenerla coherente.

No se prevén cambios en API, frontend, modelo de usuario, esquema de persistencia ni dependencias.

## Decisions

### Mantener la firma HMAC HS256 existente

Se conserva el formato JWT HS256 y la construcción actual, fortaleciendo sus garantías observables y manteniendo los claims compatibles. El Issue deja abierta la elección entre HMAC y una biblioteca JWT; mantener la estrategia actual evita cambios de dependencia y reduce el riesgo de incompatibilidad para el consumidor API que valida firma, issuer y audience. Una biblioteca JWT se consideraría solo si el código actual no puede cumplir los requisitos de forma segura o verificable, y requeriría justificar el cambio antes de implementarlo.

### Representar la vigencia con el mismo instante de expiración del token

El valor de `exp` y `expiresIn` deben derivarse de un único cálculo de expiración para evitar divergencias por relojes consultados en momentos distintos. La respuesta mantiene segundos como unidad y satisface la tolerancia de 1 segundo definida en la spec. Se evita conservar un literal fijo independiente de la expiración.

### Tratar `client` como dato de contexto del token, no como identidad autenticadora

Se valida la presencia de `client`, se conserva el valor aceptado para incluirlo en los claims y se rechaza ausente, nulo o en blanco con el error de validación indicado en la spec. El cambio no atribuye al cliente poderes ni altera cómo se verifican username/password.

### Caracterización previa y cambios incrementales

Antes de cambiar cálculos de token o respuesta, se caracterizará el contrato actual mediante pruebas del login válido (status, campos de respuesta, usuario, debug y claims relevantes), credenciales inválidas, validaciones de request y token expirado. Estas pruebas deben distinguir explícitamente el baseline documentado del nuevo comportamiento requerido para `client` y `expiresIn`, de modo que no conviertan accidentalmente el valor anterior de 3600 en una expectativa permanente.

Después se cambiará el comportamiento por partes: primero `client` y la coherencia token/respuesta con sus pruebas enfocadas; luego se comprobarán configuración segura del secreto y ausencia de valores sensibles en logs. Cada parte debe mantener sus pruebas en el mismo grupo de trabajo y conservar los contratos protegidos.

## Incremental Strategy

1. Añadir o ajustar pruebas de caracterización del endpoint y del token, incluyendo login válido, credenciales incorrectas, cuerpo/username/password inválidos, claims, expiración y forma de respuesta. Ejecutarlas desde el módulo `auth/`.
2. Ajustar el rechazo de `client` ausente o en blanco y comprobar que el `client` aceptado se refleja en el token. Conservar las respuestas HTTP y mensajes no afectados.
3. Unificar el cálculo de expiración del token y el `expiresIn` de la respuesta, verificando `exp - now` dentro de la tolerancia de 1 segundo y la invalidez del token al expirar.
4. Verificar que la configuración del secreto mantiene el origen por variable de entorno, el mínimo de bytes, la ausencia de secreto por defecto fuera del laboratorio y que los logs de login no contienen token ni secreto.
5. Actualizar solo la documentación de autenticación directamente afectada y ejecutar el conjunto de pruebas de `auth` y las comprobaciones pertinentes de calidad/cobertura exigidas por el repositorio.

Cada etapa es revisable y no requiere migración de datos. No se modificarán `api/` ni `web/`.

## Characterization Tests

Antes de cambiar comportamiento crítico, las pruebas deben capturar:

- Login válido: `200`, forma JSON completa (`token`, `expiresIn`, `user`, `debug`), usuario y claims del token, incluyendo issuer, audience y rol.
- Credenciales incorrectas: `401`, cuerpo de error existente y ausencia de emisión exitosa.
- Request nulo y username/password ausentes o en blanco: `400` con los mensajes ya documentados.
- Token alterado, malformado y expirado: no válido; el token válido conserva firma y claims requeridos.
- `client` ausente o en blanco: comparar el contrato documentado de Día 1 con el comportamiento actual del recurso y fijar el resultado objetivo de `400` indicado por la spec.
- Vigencia: comprobar numéricamente la diferencia entre `expiresIn` y `exp - now`, usando la tolerancia acordada, en lugar de fijar `expiresIn=3600`.

La comprobación de logs debe usar valores de prueba aislados y verificar que ni el token ni el secreto aparecen en la salida capturada; no se deben imprimir valores reales durante la prueba.

## Risks / Trade-offs

- [Cambio de compatibilidad para clientes que omiten `client`] → Mantener el código 400 definido en la spec, documentar el cambio del contrato del Día 1 y probar request válido e inválido.
- [Desfase de red/serialización al comparar `expiresIn` con `exp - now`] → Derivar ambos del mismo instante de expiración y comprobar el umbral explícito de 1 segundo.
- [Cambiar la respuesta puede revelar errores de reloj o cálculo previamente ocultos] → Añadir pruebas deterministas con instantes controlados donde sea posible y pruebas de integración que comparen claims y respuesta.
- [Pruebas de logs pueden producir falsos positivos o exponer secretos de prueba] → Usar secretos sintéticos aislados y revisar la captura sin imprimir su contenido; evitar que los mensajes incluyan valores de credenciales o tokens.
- [Cambio no coordinado con consumidores fuera de `ai-labs`] → Preservar HS256, issuer y audience; limitar los cambios observables a los aprobados y verificar el consumidor solo si forma parte de las validaciones existentes sin ampliar el alcance de código.

## Rollback

Si la validación del cambio descubre incompatibilidad no aceptable, revertir únicamente los cambios de implementación y pruebas de este Issue en `auth/`, preservando el resto de la rama. El rollback restaura el valor previo de vigencia y la regla previa de `client` solo mediante una decisión de producto explícita, pues contradicen requisitos aprobados. No hay migración de datos que revertir. No se debe restaurar un secreto predeterminado ni incluir secretos en configuración o logs durante el rollback.

## Open Questions

No hay decisiones abiertas que cambien las specs o el enfoque: la tolerancia de vigencia es 1 segundo y la decisión criptográfica propuesta es mantener HS256 existente, conforme al contrato actual y al criterio del Issue de documentar esa decisión.
