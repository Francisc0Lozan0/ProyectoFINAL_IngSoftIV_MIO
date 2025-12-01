# Especificaciones (formato bicolumnar)

Este documento presenta los requisitos y su especificación/criterios de aceptación en dos columnas: a la izquierda el requerimiento o sub-caso, a la derecha la especificación técnica o criterio de aceptación.

| Requisito / Caso | Especificación / Criterio de aceptación |
|---|---|
| R1 — Registro y priorización de eventos (buses/estaciones) | El sistema debe exponer un catálogo de `eventType` en la DB (`event_types`), con `category`, `defaultPriority` y `description`. La GUI del bus consultará este catálogo. Criterio: CRUD de tipos de evento funcionando y ejemplos pre-cargados; envío de evento incluye `eventType` y `priority`. |
| R1.1 — Conductor envía evento vía GUI | La GUI embebida del bus llama a un endpoint local que empaqueta un `BusDatagram` con `eventType`, `priority`, `busId`, `tripId` y `timestamp`. Criterio: evento enviado desde simulador `StreamingClient` llega al `Master` y persiste en `datagrams`. |
| R2 — Perilla de selección en GUI del bus | Interfaz embebida soporta selección por perilla: navegación circular entre opciones y confirmación. Especificación: driver simulado recibe up/down/press y emite `eventType`. Criterio: test de integración con UI simulada/comandos de teclado. |
| R3 — Administración roles y permisos | Añadir tablas `users`, `roles`, `permissions` y `role_permissions`. Implementar endpoints `POST /auth/login`, `GET /users`, `POST /roles`. Usar JWT para sesiones. Criterio: 3 roles mínimos (admin, controller, viewer) y pruebas unitarias de control de acceso. |
| R4 — Visualización en mapa en tiempo real | Backend mantiene `bus_status` (último datagrama por `busId`) y expone WebSocket `/ws/buses` que emite actualizaciones. Frontend (Leaflet) subscribe y dibuja buses. Criterio: mapa muestra posición de al menos 1000 buses en simulación; latencia < 2s. |
| R5 — Diagramas UML | Entregar PlantUML para Use-case, Component, Deployment, Sequence y State-transition. Criterio: archivos `doc/uml/*.puml` y PNG exportados. |
| R6 — Ingesta y persistencia (histórico + real-time) | Definir `datagrams` (historical) y `bus_status` (current) tablas. Ingesta batch vía `PerformanceClient` -> `Master` -> workers; ingest streaming vía Kafka or direct socket (simulado por `StreamingClient`). Criterio: datos persistidos para tamaños de prueba y `bus_status` actualizado en tiempo real. |
| R7 — Cálculo tiempos promedio por arco (histórico + real-time) | Batch: `DistributedMaster.processHistoricalData` reparte tareas y almacena resultados en `velocity_by_arc` (particionado por mes+lineId). Streaming: windows incrementales que actualizan `velocity_by_arc` y `velocity_hourly`. Criterio: resultados históricos reproducibles y actualización incremental observable en UI/API. |
| R8 — Asignación de rutas/zonas a controladores | Tabla `controller_assignments(controllerId, zoneId, lineId)` y endpoints `POST /assignments`. Criterio: 40 controladores pueden recibir asignaciones; UI de controlador filtra por su asignación. |
| R9 — Vista por controlador con velocidades por arco | API `GET /controllers/{id}/zones` y `GET /zones/{zoneId}/velocities` que devuelve `arcId, avgVelocity, sampleCount`. UI muestra lista y mini-mapas. Criterio: cada controlador ve solo sus zonas y métricas en <5s. |
| R10 — Login/Logout | Endpoints `POST /auth/login`, `POST /auth/logout`; uso de JWT con expiración configurable. Criterio: sesiones válidas y logout invalida token. |
| R11 — Adaptabilidad a crecimiento (alta disponibilidad) | Introducir cola ingest (Kafka) entre clientes y master, y persistencia en DB escalable (ClickHouse / Timescale). Uso de healthchecks, retry y backpressure. Criterio: ingest continúa bajo picos; metrics muestran throughput sostenido. |
| R12 — Escalabilidad del procesamiento | Workers auto-registran y master balancea tareas. Añadir `docker-compose` y/o scripts para orquestar N workers. Criterio: ejecutar experimentos con 4+ workers y observar reducción de tiempo proporcional. |
| R13 — Servicios para consulta pública | Endpoints REST públicos `GET /api/velocidad?from=...&to=...&fromLatLon=...&toLatLon=...` con paginación y caché CDN opcional. Criterio: respuesta <200ms para consultas cacheadas; formato JSON estándar. |
| R14 — Diagramas de transición de estados | Entregar `doc/uml/state_events.puml` y `doc/uml/state_controllers.puml`. Criterio: diagramas revisados y aprobados. |

-- Casos técnicos / sub-casos específicos (bicolumnar) --

| Sub-caso: Batch processing (Performance experiments) | Implementar `PerformanceClient` para 3 tamaños (configurables). Medir: loadTime, processTime, throughput, CPU, memoria. Criterio: scripts reproducibles `scripts/run_experiment.sh` que recolecten métricas en `doc/experiments/`. |
| Sub-caso: Streaming windows | Definir tamaño de ventana (configurable), tolerancia a latencia, e implementación de `StreamingClient` que envíe ventanas temporales. Criterio: ventanas procesadas en <window_timeout` y agregados visibles en <2 windows. |
| Sub-caso: Persistencia de resultados analíticos | Almacenar `velocity_by_arc(year_month, lineId, arcId, avgVelocity, sampleCount, updatedAt)`. Crear materialized views `velocity_hourly`. Criterio: queries para `GET /api/velocidad` usan estas tablas. |
| Sub-caso: Seguridad y privacidad | Anonimizar `busId` en APIs públicas, uso de HTTPS, y políticas de retención. Criterio: no exponer identificadores sensibles en endpoints públicos. |

-- Criterios de aceptación generales --

- CI: compilar proyecto con `mvn -q -DskipTests package` sin errores.
- Tests: pruebas unitarias para CSV parsing, particionado y cálculo de velocidad en `VelocityWorker`.
- Documentación reproducible: README con pasos para ejecutar Master, N workers, y los clients de pruebas.

Fecha: 2025-11-29
Autor: Equipo SITM-MIO
