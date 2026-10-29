# Tasks

## 1. Pruebas de caracterización

- [x] 1.1 Caracterizar el contrato HTTP de `POST /auth/login` antes de cambiarlo: login válido y forma de respuesta, credenciales inválidas, body nulo y username/password ausentes o en blanco; verificar resultados con `mvn verify` desde `auth/` usando un secreto de prueba aislado de al menos 32 bytes.
- [x] 1.2 Caracterizar el JWT existente antes de cambiar su vigencia: firma HS256, `iss`, `aud`, `sub`, `client`, `role`, `iat`, `exp`, token alterado/malformado y token expirado; verificar los casos con `mvn verify` desde `auth/` usando un secreto de prueba aislado.

Evidencia de ejecución para 1.1 y 1.2:

- Directorio: `auth/`
- Comando: `$env:AUTH_TOKEN_SECRET='LocalTestingSecret-AtLeast-32-Bytes-Value'; $env:QUARKUS_HTTP_TEST_PORT='0'; mvn verify`
- Resultado: salida `0`, `BUILD SUCCESS`; 27 pruebas, 0 fallos, 0 errores y 0 omitidas. `AuthResourceTest`: 18; `LegacyTokenUtilTest`: 5; `HealthEndpointDownTest`: 1; `PasswordHasherTest`: 3.
- El secreto usado es sintético y exclusivo de esta validación local.
- Nota de alcance: al iniciar esta caracterización ya había cambios locales en producción y pruebas de `auth/` relacionados con el Issue. Se conservaron intactos; por ello, esta ejecución verifica los contratos protegidos en el estado actual, no reconstruye un baseline previo a esos cambios.

## 2. Cliente y claims mínimos

- [x] 2.1 Añadir o ajustar pruebas del recurso y del token para comprobar que `client` ausente, nulo o en blanco produce `400` sin token y que un `client` válido aparece idéntico en el JWT; verificar los casos con `mvn verify` desde `auth/`.
- [x] 2.2 Implementar o conservar la validación de `client` y su propagación al token, manteniendo el contrato de credenciales y los claims `sub`, `iat`, `exp`, `iss`, `aud` y `role`; verificar las pruebas enfocadas de login y claims con `mvn verify` desde `auth/`.
- [x] 2.3 Actualizar la sección de `POST /auth/login` en `docs/ENDPOINTS.md` para documentar `client` como requerido y validado, conservando el resto del contrato; verificar que la tabla, errores y ejemplo JSON sean coherentes con la spec.

## 3. Vigencia coherente con la expiración

- [x] 3.1 Añadir pruebas que comparen `expiresIn` con `exp - now` y fallen cuando la diferencia absoluta supere 1 segundo, además de conservar una prueba de rechazo de token expirado; verificar con `mvn verify` desde `auth/`.
- [x] 3.2 Ajustar la generación de token y la respuesta para que `exp` y `expiresIn` deriven del mismo cálculo de expiración, conservando segundos como unidad y los campos de respuesta; verificar la tolerancia con las pruebas enfocadas y `mvn verify` desde `auth/`.
- [x] 3.3 Actualizar `docs/ENDPOINTS.md` para describir `expiresIn` como tiempo restante hasta `exp` dentro de la tolerancia definida, en lugar de fijarlo en `3600`; verificar que el ejemplo y la descripción no contradigan ese comportamiento.

## 4. Configuración de secreto y protección de logs

- [x] 4.1 Añadir o ajustar pruebas de configuración para confirmar que `AUTH_TOKEN_SECRET` es requerido fuera del laboratorio y conserva el mínimo de 32 bytes, sin introducir secretos reales; verificar los resultados con pruebas aisladas y `mvn verify` desde `auth/`.
- [x] 4.2 Añadir una prueba de logs para login válido y fallido que compruebe que ni el token ni el secreto de prueba aparecen en los eventos capturados; verificar sin imprimir esos valores durante la prueba.
- [x] 4.3 Mantener o ajustar únicamente la documentación de configuración de `AUTH_TOKEN_SECRET` afectada por el Issue para identificar ejemplos/valores de laboratorio como exclusivos de ese entorno; verificar que se documenta el origen por variable de entorno y no se incluye un secreto real.

## 5. Validación integrada

- [x] 5.1 Ejecutar el conjunto completo de pruebas de `auth` con un secreto de prueba aislado mediante `mvn verify` desde `auth/` y confirmar los escenarios de login válido, credenciales inválidas, token expirado, claims, vigencia y logs.
- [x] 5.2 Verificar que la cobertura de líneas del código modificado en `auth/` alcanza al menos 85% y que la documentación y las respuestas HTTP mantienen los contratos protegidos salvo los cambios aprobados para `client` y `expiresIn`.

Evidencia de validación integrada para 5.1:

- Directorio: `auth/`
- Comando: `$env:AUTH_TOKEN_SECRET='LocalTestingSecret-AtLeast-32-Bytes-Value'; $env:QUARKUS_HTTP_TEST_PORT='0'; mvn verify`
- Resultado: salida `0`, `BUILD SUCCESS`; 28 pruebas, 0 fallos, 0 errores y 0 omitidas. `AuthResourceTest`: 19; `LegacyTokenUtilTest`: 5; `HealthEndpointDownTest`: 1; `PasswordHasherTest`: 3.
- El secreto usado es sintético y exclusivo de esta validación local; el puerto HTTP de prueba se asignó dinámicamente.

Evidencia de cobertura y contrato para 5.2:

- JaCoCo `auth/target/site/jacoco/jacoco.csv`: `AuthResource` 26/26 líneas (100%), `AuthService` 17/17 (100%) y `LegacyTokenUtil` 77/81 (95.06%). Total ponderado de las clases de producción modificadas: 120/124 (96.77%), superior al mínimo de 85%.
- Los escenarios HTTP comprobados mantienen el contrato salvo `client` requerido y `expiresIn` coherente con `exp`, conforme a la spec. `docs/ENDPOINTS.md` documenta ambos cambios.
