# Árbol de particionamiento (hasta segundo nivel)

Este documento describe alternativas de particionamiento refinadas hasta dos niveles para el sistema de cálculo de velocidad promedio por arco del SITM-MIO. Se incluyen claves de partición, justificación y recomendaciones para volúmenes de 1M / 10M / 100M datagramas.

Resumen rápido de la recomendación:
- Recomendación primaria: particionamiento por tiempo (mes) como primer nivel y `lineId` (ruta) como segundo nivel. Funciona bien con bases de datos orientadas a series temporales (TimescaleDB) y columnstores (ClickHouse).
- Alternativa: particionamiento primario por `lineId` y secundario por `arcId` para consultas centradas en rutas/arcos.

-------------------------------------------------------------------------------

1) Modelo A — Time-first (recomendado para análisis histórico y streaming)

- Nivel 0 (bucket global): Contenedor de datos del sistema (db.table)
- Nivel 1 (partición primaria): `year_month` (ej. `2025_11`)  <-- particionamiento temporal mensual
- Nivel 2 (sub-partición): `lineId` (ej. `L1`, `L20`)  <-- particiones por ruta dentro del mes

Ejemplo de path lógico: /velocities/year_month=2025_11/lineId=L1/

Claves/columnas importantes:
- `datagram_timestamp` (datetime) — clave temporal
- `lineId` (string) — id de la ruta
- `arcId` (string) — identificador del arco
- `zoneId` (string) — zona geográfica (opcional)
- `busId` (string) — id del bus (para trazabilidad)

Ventajas:
- Facilita consultas temporales (rango de fechas) y agregaciones históricas.
- Permite limpiar/archivar meses antiguos rápidamente.
- Buena compresión y ordenamiento en TSDB o ClickHouse.

Desventajas:
- Consultas centradas en una ruta concreta que abarcan muchos meses necesitarán acceder a múltiples particiones (mitigable con índices por `lineId`).

Escalado sugerido:
- Bucket mensual para 3M eventos/día → ~90M/mes. Si los buckets mensuales son grandes, usar buckets semanales (`year_week`) o particionar por `day`.

-------------------------------------------------------------------------------

2) Modelo B — Route-first (recomendado para dashboards por controlador)

- Nivel 0: db.table
- Nivel 1: `lineId` (ruta)  <-- particionamiento primario por ruta
- Nivel 2: `arcId` (arco)   <-- particionamiento secundario por arco

Ejemplo de path lógico: /velocities/lineId=L1/arcId=ARC_L1_10_11/

Ventajas:
- Consultas por ruta o por arco son muy rápidas (útil para controladores y APIs por zona).
- Fácil asignación y despliegue de shards por rutas con más tráfico.

Desventajas:
- Agrupaciones temporales largas requieren acceso a muchos shards; less optimal para analytics temporales extensos.

-------------------------------------------------------------------------------

3) Modelo C — Zone-first (útil para asignación a controladores)

- Nivel 1: `zoneId`
- Nivel 2: `year_month` o `lineId` (según prioridad de consultas)

Uso recomendado: cuando la unidad de operación (controlador) monitorea zonas geográficas y se requiere baja latencia por zona.

-------------------------------------------------------------------------------

Recomendaciones operativas y diseño de particionamiento

- Elegir Time-first (Modelo A) si la prioridad es análisis histórico y facilidad de archivado.
- Usar composite partitioning en sistemas que lo soporten: por ejemplo `PARTITION BY (year_month, lineId)`.
- Para ClickHouse: usar `PARTITION BY toYYYYMM(datagram_timestamp)`, `ORDER BY (lineId, arcId, datagram_timestamp)`.
- Para TimescaleDB: crear hypertable por `datagram_timestamp` y usar `lineId` como columna de segmentación (space partitioning) cuando se necesite.

Shard key y balanceo

- Shard key sugerida: `hash(lineId) % N_shards` cuando el primer nivel no sea `lineId` (por ejemplo en time-first). Esto permite distribuir carga de rutas populares.
- Mantener N_shards ≥ número de workers/nodos y ajustar con pruebas (4, 8, 16 según hardware).

Compaction / Retención

- Política de retención: conservar datos detallados por 6–12 meses; luego downsample (promedios por arco por hora/día) y archivar.
- Ejecutar compaction semanal para ClickHouse o retention jobs en TimescaleDB.

Particionamiento físico y tamaños objetivo

- Objetivos de bucket para pruebas (guía para D):
  - Pequeño (1M datagramas): usar partición mensual con 1–2 shards.
  - Medio (10M): partición mensual, 4–8 shards.
  - Grande (100M): partición semanal o daily, 8–32 shards.

Clave de consultas típicas y cómo las cubre el particionamiento

- Q1: "Velocidad promedio por arco entre fecha A y B" → Time-first facilita (leer particiones por rango temporal, filtrar por arcId/lineId).
- Q2: "Velocidad promedio en zona Z en tiempo real" → Zone-first o route-first con índice de zoneId.
- Q3: "Mapa en tiempo real de buses" → Tabla/tema separado optimizado por `busId` y `timestamp` (último estado por bus) — no mezclar con tablas históricas.

Implementación práctica (pasos)

1. Crear tabla principal `datagrams` con columnas:
   - `datagram_id` (uuid)
   - `datagram_timestamp` (timestamp)
   - `lineId`, `tripId`, `arcId`, `stopId`, `busId`, `zoneId`
   - `latitude`, `longitude`, `odometer`, `eventType`

2. Para analytics, crear tabla `velocity_by_arc` con particionamiento por `year_month` y `lineId` (según Modelo A).

3. Crear materialized views o jobs de downsample (por ejemplo `velocity_hourly`, `velocity_daily`).

4. Añadir tabla ligera `bus_status` (estado actual por bus) para consultas en tiempo real y el frontend del mapa.

-------------------------------------------------------------------------------

Anexo: ejemplos de particionamiento SQL (ClickHouse)

-- Tabla de ejemplo (ClickHouse)
-- CREATE TABLE datagrams (
--   datagram_timestamp DateTime,
--   lineId String,
--   arcId String,
--   busId String,
--   latitude Float64,
--   longitude Float64,
--   odometer Float64,
--   eventType UInt8
-- )
-- ENGINE = MergeTree()
-- PARTITION BY toYYYYMM(datagram_timestamp)
-- ORDER BY (lineId, arcId, datagram_timestamp);

-------------------------------------------------------------------------------

Notas finales:
- El árbol aquí es una guía; la elección final depende del motor de almacenamiento.
- Para el entregable A adjuntaré este documento y, si quieres, lo convierto en PlantUML/diagrama simple.

Autor: Equipo de desarrollo SITM-MIO
Fecha: 2025-11-29
