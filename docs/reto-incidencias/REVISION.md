# Revisión del reto de incidencias

Skill: [caso-02-acceso](../../.agents/skills/caso-02-acceso/SKILL.md). Este documento no incluye valores sensibles del log; los extractos citados en los JSON ya están redactados.

## Pedidos usados

| # | Pedido | Caso |
| --- | --- | --- |
| 1 | Analizar el caso con el Skill y preparar el JSON, sin registrar. | caso-02 |
| 2 | Aprobación explícita: «ok aprobado». | caso-02 |
| 3 | Reenviar exactamente el mismo contenido aprobado. | caso-02 |
| 4 | Ejecutar el mismo Skill sobre un segundo caso, solo hasta el JSON. | caso-01 |
| 5 | Invocar el Skill sin indicar la ruta del log. | ninguno |

## Resultados observados

- **caso-02** ([incidencia.json](./caso-02/incidencia.json)): `acceso` / `AUTH_DEPENDENCY_UNAVAILABLE` / `LOGIN-DEMO-201`, 2 eventos únicos (5 líneas, una copia de `evt-202`), 2026-11-03T15:00:01Z a 15:00:04Z, P2. La línea 5 (401 de otra correlación) quedó fuera.
- **caso-01** ([incidencia.json](./caso-01/incidencia.json)): `transferencias` / `TRF_TIMEOUT` / `TRX-DEMO-101`, 2 eventos únicos (líneas 2 y 3 son copias de `evt-102`), 2026-11-03T14:00:05Z a 14:00:12Z, P2, estado PENDING. La línea 5 (otra correlación, completada) quedó fuera. Registrado tras aprobación humana: ticket `INC-0C19D1E54402`, `created: true`; la consulta MCP devolvió el mismo contenido (creado 2026-11-04T15:19:10Z).
- **Sin ruta**: el Skill obliga a pedir la ruta o el caso y detenerse, sin elegir archivo ni inventar evidencia. Respuesta esperada: «Indica la ruta del log o el caso a analizar; no puedo elegir uno ni preparar el JSON sin él». Esta comprobación la hice aplicando el procedimiento del Skill en esta sesión; **no fue una invocación independiente en otra conversación de Copilot Chat**.

## Correcciones al Skill

Ninguna.

## Ticket del caso-02

- **ID:** `INC-933064EA541C`.
- **Registro:** `registrar_incidencia` con `approved: true` devolvió `created: false`: el ticket ya existía antes de esta sesión.
- **Consulta:** `consultar_incidencia` devolvió el mismo contenido (fecha de creación 2026-11-04T15:10:26Z).
- **Reintento:** reintento idéntico devolvió el mismo ID con `created: false`. No hubo duplicado y no se cambió la correlación.

## Pendientes

- Ejecutar el Skill sobre caso-03 (no se hizo).
- Repetir la prueba sin ruta en una conversación nueva de Copilot Chat con el Skill invocado de forma independiente.
- Los ejemplos de `examples/` solo cubren caso-02; falta un ejemplo en formato clave=valor.
- No se localizó `CONTRATO.json` ni los criterios de S2 en el repo; se usó el contrato y las reglas de `obtener_caso`.
- No hay edición ni borrado de tickets en este ejercicio; cualquier corrección del ticket de caso-02 queda documentada como pendiente.
