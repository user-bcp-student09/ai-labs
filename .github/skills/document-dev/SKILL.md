---
name: document-dev
description: "Genera documentación técnica de cambios o del estado actual de cualquier proyecto de software, lista para Confluence y respaldada por evidencia."
---

# Documentación de proyectos de software para Confluence

## Propósito

Preparar documentación técnica en español, en Markdown compatible con
Confluence, que permita entender un cambio o el estado observado de cualquier
proyecto de software, sistema, aplicación, biblioteca o componente sin depender
de conocimiento tácito. La página puede servir como registro de cambio; no
sustituye especificaciones versionadas, informes formales de seguridad,
aprobaciones de despliegue ni registros operativos.

## Cuándo usarla

- Antes o después de un cambio funcional o técnico en cualquier proyecto que
  afecte usuarios, consumidores, arquitectura, datos, operación, seguridad,
  interfaces o pruebas.
- Para dejar una línea base de un proyecto existente cuando no se proporciona
  un diff, commit, versión o release.
- Para preparar contenido que una persona revisará y publicará en Confluence.

No usarla para afirmar que un cambio fue aprobado, desplegado o probado en
producción sin evidencia explícita.

## Entradas necesarias

Recopila, cuando estén disponibles:

- Nombre, tipo y alcance del proyecto o componente.
- Tipo de documento: cambio con referencia identificable o estado actual.
- Contexto, objetivo, comportamiento anterior/nuevo e impacto esperado/medido.
- Fuentes de verdad: diff, commit, PR, historia, release o archivos del servicio.
- Arquitectura, estructura de módulos, dependencias y sistemas externos.
- Interfaces afectadas (por ejemplo API/endpoints, UI, CLI, eventos, SDK o
  contratos de biblioteca), solo cuando apliquen.
- Flujos funcionales, errores, persistencia, configuración, distribución y
  despliegue, solo cuando apliquen.
- Comandos ejecutados, entorno, resultado, conteo de pruebas y cobertura.
- Riesgos, controles, compatibilidad, despliegue y reversa, si se conocen.
- Audiencia, responsable, revisores, fecha y referencias de Confluence, si existen.

Si no hay diff ni referencia de cambio, etiqueta el documento como **estado
actual observado**. No describas como "realizado", "nuevo", "publicado" o
"desplegado" algo que solo se observa en el código presente.

## Procedimiento

1. **Fijar el alcance.** Identifica el proyecto y alcance solicitados; lee las
   instrucciones pertinentes del repositorio. No inspecciones componentes fuera
   del alcance acordado.
2. **Establecer la fuente de verdad.** Revisa la referencia de cambio indicada
   y el código/configuración/pruebas del alcance acordado. Distingue hechos
   observados, afirmaciones recibidas y puntos pendientes de confirmar. No
   infieras impacto real en usuarios, integraciones o consumidores sin evidencia.
3. **Mapear el diseño.** Describe módulos, límites, responsabilidades,
   dependencias y flujos relevantes según el tipo de proyecto. Traza caminos
   exitosos y errores desde la entrada hasta el resultado. No fuerces un modelo
   de servicio/API si el proyecto no lo usa.
4. **Documentar interfaces afectadas.** Para API HTTP, describe cada endpoint
   con método/ruta, autenticación, parámetros, cuerpo, respuestas y errores.
   Para UI, CLI, eventos, SDK, bibliotecas u otras interfaces, documenta las
   entradas, salidas, compatibilidad y ejemplos pertinentes. Omite lo que no
   aplique e indica qué interfaces no se verificaron.
5. **Verificar pruebas.** Usa el comando prescrito por el repositorio/componente
   cuando sea seguro y posible. Registra el comando exacto, directorio de
   ejecución, resultado, conteos, fallos/skips, cobertura y ubicación de
   reportes disponibles. Usa los comandos y métricas aplicables al proyecto;
   una herramienta de cobertura, análisis o calidad solo si está configurada y
   medida. Si no se ejecutó una verificación, indícalo. No conviertas pruebas
   existentes en evidencia de escenarios que no cubren.
6. **Evaluar impactos y riesgos.** Separa impacto medido de impacto esperado.
   Describe los riesgos observables, su consecuencia y mitigación/acción; marca
   responsables o fechas como pendientes cuando no se proporcionaron.
7. **Preparar la página.** Usa la estructura de salida de abajo, con referencias
   rastreables al código, pruebas y fuentes. Mantén Markdown sencillo para
   pegarlo en Confluence. No presupongas que macros, Mermaid o extensiones se
   renderizan.
8. **Revisar antes de entregar.** Verifica exactitud, coherencia, privacidad,
   enlaces, comandos y que cada afirmación de ejecución tenga evidencia.

## Estructura requerida del documento

Adapta las secciones sin eliminar las que sean pertinentes:

1. **Metadatos:** título, proyecto/componente, versión/referencia, estado del
   documento, fecha, responsable, revisores y enlaces. Usa "Pendiente" si falta
   un dato.
2. **Resumen ejecutivo:** propósito, qué cambió (o que es estado actual) y
   resultado/impacto sin exageraciones.
3. **Contexto y alcance:** dentro/fuera, usuarios o consumidores conocidos,
   supuestos y limitaciones.
4. **Descripción del cambio e impacto:** antes/después respaldado por la fuente;
   comportamiento, compatibilidad, datos, dependencias e impacto medido frente
   al esperado. Adapta a estado actual si no hay cambio identificado.
5. **Estructura y arquitectura:** módulos, responsabilidades, interfaces,
   almacenamiento, configuración y dependencias según aplique; identifica
   deuda técnica pertinente.
6. **Flujos de uso/proceso:** pasos numerados para caminos exitosos, alternos y
   errores importantes, adecuados al proyecto.
7. **Interfaces y contratos:** endpoints, UI, CLI, eventos, SDK, formatos de
   archivo o API de biblioteca según aplique; ejemplos seguros, entradas,
   salidas, errores y efectos secundarios.
8. **Configuración, distribución y operación:** opciones requeridas (nunca
   valores secretos), build, instalación, migraciones, health checks o
   despliegue cuando apliquen. Marca lo no observado.
9. **Pruebas y evidencia:** tabla con tipo, comando, resultado, conteo,
   cobertura y ubicación; separa pruebas ejecutadas, no ejecutadas y pendientes.
10. **Seguridad, riesgos y mitigaciones:** hechos observables y acciones
    concretas, sin presentar una revisión informal como aprobación de seguridad.
11. **Despliegue, reversa y observabilidad:** solo instrucciones verificadas;
    cuando no existan, indícalo como pendiente.
12. **Decisiones, pendientes y trazabilidad:** decisión/contexto/consecuencia,
    preguntas abiertas, referencias a cambios y lista de revisión/publicación.

## Reglas de precisión y seguridad

- No inventes requisitos, fechas, aprobaciones, propietarios, consumidores,
  disponibilidad, SLAs, métricas, resultados ni estado de despliegue.
- Marca lo no conocido como **No informado**, **No verificado** o
  **Pendiente**. Separa claramente inferencias del código y decisiones
  confirmadas por personas.
- No copies contraseñas, tokens, claves, datos de clientes, identificadores
  sensibles, URL internas no autorizadas ni valores de producción. En ejemplos
  usa marcadores como `<TOKEN_REDACTADO>` y credenciales ficticias claramente
  no utilizables; preferiblemente omite credenciales.
- No documentes secretos encontrados en el repositorio; describe el riesgo sin
  revelar el valor. Evita ejemplos que permitan reutilizar credenciales reales.
- Los ejemplos de request/response deben corresponder al contrato observado,
  con datos sintéticos. No ocultes campos inseguros o inesperados: descríbelos
  como riesgos, sin normalizarlos como recomendación.
- No presentes una vulnerabilidad como mitigada hasta verificar el cambio y
  sus pruebas. No rebajes una alerta ni recomiendes ignorar controles.
- No afirmes que pruebas, cobertura, seguridad, análisis de calidad o
  integración pasaron sin leer su salida. Si una métrica no está disponible,
  dilo.
- No clasifiques un cambio como retrocompatible sin evaluar a los usuarios,
  integraciones o consumidores afectados. Si no se inspeccionaron, dilo.

## Formato Confluence

- Usa títulos Markdown (`#`, `##`, `###`), listas, tablas sencillas y bloques
  cercados para JSON/comandos/diagramas de texto.
- Mantén encabezados explícitos y contenido autocontenido; evita abreviaturas
  no definidas, tablas demasiado anchas, HTML y sintaxis exclusiva de macros.
- Usa enlaces Markdown a páginas, commits y archivos solo cuando las referencias
  existan y sean accesibles a la audiencia.
- Incluye responsable y fecha de revisión. Si no se conoce la cadencia, deja
  "Próxima revisión: pendiente de acordar"; no impongas una política.
- Conserva la trazabilidad: una página por cambio significativo, con vínculos
  a la especificación versionada y al registro de decisión en vez de duplicar
  fuentes extensas.
- La página es un borrador hasta que el responsable/revisor indicado la apruebe.

## Lista de aceptación

- [ ] Se identifica el proyecto, la versión/referencia y si es cambio o estado
      actual.
- [ ] El resumen, alcance e impacto distinguen hechos de pendientes.
- [ ] La arquitectura y los flujos coinciden con el código/configuración.
- [ ] Cada interfaz afectada tiene entradas, salidas, errores y requisitos de
      acceso descritos cuando corresponda; lo no probado está señalado.
- [ ] El informe incluye evidencia de pruebas real y cobertura si está
      disponible.
- [ ] Riesgos y mitigaciones son accionables y no revelan información sensible.
- [ ] Despliegue/reversa no se inventan; incógnitas quedan explícitas.
- [ ] Markdown se puede pegar en Confluence sin macros específicas.
- [ ] Responsable, revisión y enlaces están completos o marcados pendientes.

## Referencias de buenas prácticas consultadas

Estas fuentes informan el formato y la trazabilidad; no prueban que una
organización específica use exactamente esta plantilla:

- Atlassian, [Use Confluence for technical documentation](https://support.atlassian.com/confluence-cloud/docs/use-confluence-for-technical-documentation/):
  estructura de espacio, reutilización de contenido y mantenimiento de
  documentación técnica.
- Atlassian, [Confluence best practices](https://www.atlassian.com/software/confluence/resources/guides/get-started/best-practices):
  plantillas consistentes, formato y permisos.
- OpenAPI Initiative, [OpenAPI Specification 3.1.1](https://spec.openapis.org/oas/v3.1.1.html):
  contrato de API legible por personas y herramientas para reducir ambigüedad.
- Microsoft Azure Well-Architected, [Architecture decision records](https://learn.microsoft.com/en-us/azure/well-architected/architect-role/architecture-decision-record):
  contexto, alternativas, decisión, consecuencias, estado y trazabilidad de
  decisiones arquitectónicas.
- OWASP, [Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html):
  consideraciones de autenticación, manejo de fallos y monitoreo.
- Google Cloud, [API Design Guide](https://cloud.google.com/apis/design):
  consistencia y convenciones para contratos de API.

## Ejemplos

- Entrada ficticia: `examples/input.sample.md`
- Página ilustrativa correspondiente: `examples/output.sample.md`

Los ejemplos son inventados para mostrar el nivel de detalle y la transformación
de entrada a salida. No corresponden a un repositorio, cliente, sistema,
ejecución de pruebas ni despliegue real. Conserva esa advertencia al reutilizar
los ejemplos y no copies sus datos como evidencia de otro proyecto.