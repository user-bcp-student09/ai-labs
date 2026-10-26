# Contratos de endpoints

## auth

### Endpoint: `GET /q/health`

1. **Qué hace**  
   Expone el estado de salud del servicio de autenticación para saber si está disponible.

2. **Autenticación requerida**  
   No requiere autenticación.  
   Header exacto: no aplica `Authorization`.

3. **Request**

   | campo | tipo | obligatorio | descripcion |
   |---|---|---|---|
   | body | JSON object | No | No usa cuerpo JSON para responder el estado. |

   Ejemplo JSON real y completo del request:
   ```json
   {}
   ```

4. **Response exitosa**

   - Código: `200 OK`
   - Cuerpo JSON:

   | campo | tipo | descripcion |
   |---|---|---|
   | status | string | Estado general de salud (`UP`). |
   | checks | array | Lista de chequeos individuales (puede estar vacía). |

   Ejemplo JSON real y completo de respuesta exitosa:
   ```json
   {
     "status": "UP",
     "checks": []
   }
   ```

5. **Respuestas de error posibles**

   - `503 Service Unavailable`: ocurre cuando el framework de health reporta estado no saludable.
     ```json
     {
       "status": "DOWN",
       "checks": [
         {
           "name": "example-check",
           "status": "DOWN"
         }
       ]
     }
     ```

6. **Notas de seguridad**

   - Es un endpoint operativo sin autenticación; no debe exponer datos sensibles de negocio.
   - La disponibilidad de este endpoint depende de la extensión de health de Quarkus.

   **Evidencia verificable**
   - Dependencia que publica el endpoint: `quarkus-smallrye-health` en `auth/pom.xml`.
   - `auth/pom.xml:40`

   **Pregunta abierta**
   - La estructura exacta de cada elemento dentro de `checks` depende de los health checks registrados en runtime. En este paso no se ejecutó el servicio para capturar una muestra local del entorno actual.

---

### Endpoint: `POST /auth/login`

1. **Qué hace**  
   Autentica credenciales de usuario y devuelve un token de sesión con datos básicos del usuario.

2. **Autenticación requerida**  
   No requiere autenticación previa.  
   Header exacto: no aplica `Authorization`.

3. **Request**

   | campo | tipo | obligatorio | descripcion |
   |---|---|---|---|
   | username | string | Sí | Nombre de usuario. No puede ser nulo ni vacío/espacios. |
   | password | string | Sí | Contraseña. No puede ser nula ni vacía/espacios. |
   | client | string | No | Campo aceptado por contrato de entrada; no altera la autenticación en el código actual. |

   Ejemplo JSON real y completo del request:
   ```json
   {
     "username": "student",
     "password": "Workshop123!",
     "client": "angular-workshop"
   }
   ```

4. **Response exitosa**

   - Código: `200 OK`
   - Cuerpo JSON:

   | campo | tipo | descripcion |
   |---|---|---|
   | token | string | JWT firmado (HS256) con claims de identidad y expiración. |
   | expiresIn | number (long) | Tiempo de expiración en segundos (`3600`). |
   | user | object | Datos del usuario autenticado. |
   | user.username | string | Username del usuario autenticado. |
   | user.displayName | string | Nombre visible del usuario (escapado HTML). |
   | user.role | string | Rol del usuario. |
   | debug | string | Marcador del flujo legado (`authenticated-with-legacy-flow`). |

   Ejemplo JSON real y completo de respuesta exitosa:
   ```json
   {
     "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJhaS1sYWJzLWF1dGgiLCJhdWQiOiJhaS1sYWJzLWFwaSIsInN1YiI6InN0dWRlbnQiLCJyb2xlIjoiU1RVREVOVCIsImlhdCI6MTcwMDAwMDAwMCwiZXhwIjoxNzAwMDAzNjAwfQ.JV6gzhRrO6r8v4jN4Fto3Jm4y2fQ8yXFCb_1oQ4mL7Y",
     "expiresIn": 3600,
     "user": {
       "username": "student",
       "displayName": "Workshop Student",
       "role": "STUDENT"
     },
     "debug": "authenticated-with-legacy-flow"
   }
   ```

5. **Respuestas de error posibles**

   - `400 Bad Request`: cuando el body es `null`.
     ```json
     {
       "message": "Request is null"
     }
     ```

   - `400 Bad Request`: cuando falta `username` o llega vacío.
     ```json
     {
       "message": "Username is required"
     }
     ```

   - `400 Bad Request`: cuando falta `password` o llega vacío.
     ```json
     {
       "message": "Password is required"
     }
     ```

   - `401 Unauthorized`: cuando las credenciales no son válidas.
     ```json
     {
       "message": "Invalid user unknown-user or password"
     }
     ```

   - `500 Internal Server Error`: cuando ocurre un error no controlado durante la autenticación.
     ```json
     {
       "message": "Internal server error"
     }
     ```

6. **Notas de seguridad**

   - El token de sesión se firma con `AUTH_TOKEN_SECRET` y expira (claim `exp`).
   - El secreto no está hardcodeado; debe configurarse por variable de entorno y tener longitud mínima de 32 bytes.
   - El JWT incluye `iss=ai-labs-auth` y `aud=ai-labs-api`; el consumidor debe validar firma, emisor, audiencia y expiración.
   - El origen CORS permitido en `auth` está restringido a `http://localhost:4200`.
   - La persistencia usa SQL parametrizado para autenticación (evita concatenar credenciales).

   **Evidencia verificable**
   - Ruta HTTP y validaciones de entrada/salidas: `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:14-57`
   - Campos del request: `auth/src/main/java/com/bcp/workshop/auth/LoginRequest.java:3-6`
   - Campos del response: `auth/src/main/java/com/bcp/workshop/auth/LoginResponse.java:3-4`
   - Construcción del response y marcador `debug`: `auth/src/main/java/com/bcp/workshop/auth/AuthService.java:9-28`
   - Claims JWT, expiración y validación: `auth/src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java:20-102`
   - Secreto por variable de entorno: `auth/src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java:22,130-141`
   - CORS: `auth/src/main/resources/application.properties:4-5`
   - Consulta parametrizada: `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:54-57`
   - Pruebas de contrato (200/400/401/500 y CORS): `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java:32-116,251-266`

