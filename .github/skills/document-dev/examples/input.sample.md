# Ejemplo ficticio de entrada: alertas de inventario

> **Aviso:** este escenario y todos sus nombres, decisiones, comandos y
> resultados son inventados con fines ilustrativos. No representan un producto
> ni una ejecución real.

## Solicitud

Documentar para Confluence el cambio de umbral de stock bajo en el proyecto
ficticio **Lumen Stock**, una aplicación web para que pequeños comercios
consulten y administren su inventario.

## Alcance y fuentes

- **Tipo de proyecto:** aplicación web con frontend y API.
- **Tipo de documento:** cambio funcional.
- **Objetivo:** permitir que el personal configure por producto la cantidad a
  partir de la cual el inventario se considera bajo.
- **Cambio:** se agrega `alertThreshold` a la respuesta de producto y la
  operación `PATCH /api/products/{productId}/alert-threshold` para actualizarlo.
  La lista de productos marca `lowStock: true` cuando `quantity` es menor o
  igual que el umbral.
- **Antes:** el sistema mostraba cantidad disponible, pero no permitía
  configurar umbrales individuales ni identificar productos con stock bajo.
- **Después esperado:** el usuario autorizado puede actualizar el umbral y ver
  el indicador de stock bajo en la lista.
- **Referencia:** cambio ficticio `LST-248`, sin URL real.
- **Fuentes para este ejemplo:** descripción hipotética del cambio y contrato
  hipotético incluido abajo. No hay repositorio real que inspeccionar.
- **Versión/entorno:** versión de ejemplo `2.4.0`; despliegue no incluido.
- **Audiencia:** desarrollo, QA, soporte y responsable de producto.
- **Dentro del alcance:** endpoint descrito, lista de productos y pruebas
  unitarias/funcionales del ejemplo.
- **Fuera del alcance:** notificaciones por correo/SMS, importación masiva,
  rediseño visual y cambios de infraestructura.
- **Responsable/revisores:** equipo ficticio Lumen; nombres no definidos.

## Contrato hipotético

- Requiere sesión de usuario con permiso de edición de inventario.
- Request `PATCH /api/products/{productId}/alert-threshold`:

  ```json
  {
    "alertThreshold": 8
  }
  ```

- `alertThreshold` es entero entre 0 y 100000 inclusive.
- Éxito: `200`, con `productId`, `quantity`, `alertThreshold` y `lowStock`.
- `400` para valores fuera de rango o formato incorrecto, `401` para sesión
  ausente/inválida, `403` sin permiso y `404` para producto inexistente.
- El umbral inicial de productos existentes es `0`.
- No se envían notificaciones externas.

## Evidencia ficticia para el ejemplo de salida

- Comando hipotético: `npm test -- --runInBand`.
- Resultado hipotético: 18 pruebas pasaron y 0 fallaron.
- Se afirma cobertura de código modificado de 88% en un reporte hipotético.
- Escenarios hipotéticamente cubiertos: umbral válido, límites 0 y 100000,
  valores inválidos, producto inexistente, autorización denegada e indicador de
  stock bajo.
- No se realizaron pruebas reales, pruebas de carga ni validación en producción.

## Riesgos conocidos del escenario hipotético

- Un límite incorrecto puede marcar inventario como bajo antes o después de lo
  esperado.
- Un control de autorización incompleto puede permitir a usuarios sin permiso
  cambiar umbrales.
- El cambio de contrato puede afectar otros consumidores de la lista de
  productos, que no se han especificado en este ejemplo.
