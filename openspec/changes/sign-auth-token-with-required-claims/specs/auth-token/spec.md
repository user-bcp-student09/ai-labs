# Spec Delta

## Purpose

Define el contrato observable del token de autenticación emitido por `POST /auth/login`, incluyendo sus claims, vigencia y protección de secretos, para que los consumidores puedan validar la sesión sin depender de detalles de implementación.

## ADDED Requirements

### Requirement: El token firmado contiene los claims mínimos
El sistema MUST emitir para un login válido un JWT firmado con los claims `sub`, `client`, `iat` y `exp`. `sub` MUST identificar al usuario autenticado; `client` MUST coincidir con el valor validado de la solicitud; `iat` y `exp` MUST representar tiempos Unix en segundos y `exp` MUST ser posterior a `iat`. MUST conservarse `iss=ai-labs-auth`, `aud=ai-labs-api` y el claim de rol existentes.

#### Scenario: Login válido emite claims vinculados a la solicitud
- **WHEN** se envían credenciales válidas junto con un `client` no vacío
- **THEN** la respuesta es `200` e incluye un JWT firmado con `sub` del usuario, `client` idéntico al valor enviado, `iat` y `exp` válidos, el emisor y audiencia existentes, y el rol del usuario

### Requirement: El cliente es obligatorio y se valida
El sistema MUST obtener `client` de la solicitud y rechazar un valor ausente o compuesto solo por espacios. Este requisito cambia el contrato de Día 1, que documenta `client` como opcional; no establece otras restricciones de formato no definidas por el Issue.

#### Scenario: Se rechaza un cliente ausente o vacío
- **WHEN** se envía `POST /auth/login` sin `client`, con `client` nulo o con un valor compuesto solo por espacios
- **THEN** la respuesta es `400` con el mensaje `Client is required` y no se emite un token

### Requirement: La vigencia de la respuesta concuerda con la expiración
Para un login válido, `expiresIn` MUST expresarse en segundos y corresponder al tiempo restante hasta el claim `exp` al generar la respuesta, con una diferencia absoluta no superior a 1 segundo.

#### Scenario: La vigencia informada coincide con el tiempo restante
- **WHEN** un consumidor compara `expiresIn` con `exp` menos el tiempo actual al recibir la respuesta de login
- **THEN** la diferencia absoluta entre ambos valores no supera 1 segundo

### Requirement: Un token expirado no es válido
Un token MUST dejar de ser válido cuando el tiempo actual alcanza o supera su claim `exp`.

#### Scenario: Se rechaza un token después de su expiración
- **WHEN** se valida un token cuyo claim `exp` es igual o anterior al tiempo actual
- **THEN** el token se considera expirado y no válido

### Requirement: La firma no depende de secretos predeterminados fuera del laboratorio
El sistema MUST obtener el secreto de firma de `AUTH_TOKEN_SECRET`, conservar el mínimo de 32 bytes documentado y no emitir tokens usando un valor predeterminado en entornos no pertenecientes al laboratorio. La configuración y cualquier valor de ejemplo MUST quedar identificados claramente como exclusivos del laboratorio cuando corresponda.

#### Scenario: No hay secreto configurado fuera del laboratorio
- **WHEN** el servicio se ejecuta fuera del laboratorio sin `AUTH_TOKEN_SECRET`
- **THEN** no emite un token firmado con un secreto implícito o predeterminado

### Requirement: Los logs no exponen el token ni el secreto
Los logs generados por el flujo de autenticación MUST NOT contener el token de sesión ni el valor de `AUTH_TOKEN_SECRET`.

#### Scenario: Se registran eventos del flujo de login
- **WHEN** ocurre un login válido o fallido
- **THEN** los logs no contienen el token emitido ni el secreto de firma

### Requirement: El contrato restante de login se conserva
El sistema MUST conservar `POST /auth/login`, el formato exitoso con `token`, `expiresIn`, `user` y `debug`, incluido `displayName` escapado como HTML, y los resultados documentados para credenciales inválidas. El significado de `expiresIn` cambia de valor fijo `3600` al tiempo restante definido en esta spec.

#### Scenario: Login válido conserva la forma de respuesta
- **WHEN** se autentican credenciales válidas con un `client` no vacío
- **THEN** la respuesta es `200` e incluye `token`, `expiresIn`, `user` con `username`, `displayName` y `role`, y `debug` con el marcador legado documentado

#### Scenario: Credenciales inválidas conservan el error documentado
- **WHEN** se envía un username o password que no autentica a un usuario
- **THEN** la respuesta es `401` con el mensaje de credenciales inválidas documentado y no se emite un token

#### Scenario: Cuerpo nulo conserva el error documentado
- **WHEN** se envía `POST /auth/login` sin cuerpo JSON
- **THEN** la respuesta es `400` con el mensaje `Request is null`

#### Scenario: Username o password faltantes conservan sus errores
- **WHEN** se envía `POST /auth/login` con username o password ausente, nulo o vacío
- **THEN** la respuesta es `400` con el mensaje documentado correspondiente, `Username is required` o `Password is required`
