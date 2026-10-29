# Proposal

## Why

El Issue [#25](https://github.com/user-bcp-student09/ai-labs/issues/25) identifica que el token de `POST /auth/login` debe exponer claims mínimos coherentes y una expiración verificable, mientras la respuesta informa una vigencia consistente. Esto importa para que consumidores y pruebas puedan comprobar a quién y a qué cliente pertenece la sesión, cuándo vence y que los valores sensibles no queden expuestos.

## What Changes

### Alcance

- Definir el contrato de un token firmado con los claims `sub`, `client`, `iat` y `exp`, manteniendo los claims y el formato de JWT existentes que no contradigan el Issue.
- Asegurar que `client` proceda de la solicitud, se valide y quede reflejado en el token.
- Hacer que `expiresIn` corresponda al tiempo restante hasta `exp`, con tolerancia máxima de 1 segundo.
- Establecer que los secretos de firma no tengan un valor predeterminado fuera del laboratorio y que tokens y secretos no aparezcan en logs.
- Definir escenarios verificables para login válido, credenciales inválidas, expiración y ausencia o invalidez de `client`.

### Fuera de alcance

- Cambios a endpoints distintos de `POST /auth/login`, al formato general de su respuesta o al flujo de autenticación de credenciales.
- Cambios en `api/`, `web/`, persistencia de usuarios, roles, CORS o gestión de sesión del frontend.
- Elegir la biblioteca o el mecanismo técnico de firma: el Issue deja abierta la decisión entre conservar HMAC o adoptar una biblioteca JWT.

### Comportamiento que se preserva y cambio del contrato

- Se preservan la ruta y el método `POST /auth/login`, el token JWT firmado, `iss=ai-labs-auth`, `aud=ai-labs-api`, los datos de usuario, `debug`, y los errores existentes para cuerpo nulo, username/password faltantes o credenciales inválidas.
- El contrato de `docs/ENDPOINTS.md` describe `client` como opcional y sin efecto; el Issue requiere obtenerlo, validarlo y reflejarlo en el token. Por ello, esta propuesta cambia ese aspecto: una solicitud sin `client` válido se rechaza con `400`, sin alterar el contrato de autenticación restante.
- El contrato del Día 1 fija `expiresIn` en `3600`. El Issue requiere coherencia con `exp - now`; se preservan el campo y su unidad en segundos, pero su valor debe representar el tiempo restante dentro de la tolerancia acordada de 1 segundo.
- Se mantiene la configuración de firma mediante `AUTH_TOKEN_SECRET` y el mínimo de 32 bytes documentado; no se permite depender de un secreto predeterminado fuera del laboratorio.

## Capabilities

### New Capabilities

- `auth-token`: contrato observable para la emisión, claims, expiración y protección del token de autenticación.

### Modified Capabilities

- Ninguna. El repositorio no tiene specs existentes; la capacidad se introduce como spec nueva.

## Impact

- Área afectada: `auth/`, principalmente el contrato observable de `POST /auth/login` y las garantías de sus tokens.
- Contrato de referencia: `docs/ENDPOINTS.md`. Sus diferencias sobre obligatoriedad de `client` y valor de `expiresIn` quedan identificadas arriba como cambios solicitados por el Issue.
- No se proponen dependencias ni decisiones de implementación en esta etapa.
