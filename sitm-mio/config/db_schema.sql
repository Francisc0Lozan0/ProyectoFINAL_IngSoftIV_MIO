-- DB schema proposal for SITM-MIO
-- Target: PostgreSQL + PostGIS + TimescaleDB (recommended)
-- Date: 2025-11-30
-- Updated: Added tables for velocity processing results

-- Enable extensions (run as superuser)
-- CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
-- CREATE EXTENSION IF NOT EXISTS postgis;
-- CREATE EXTENSION IF NOT EXISTS timescaledb;

-- ============================================================================
-- TABLES FOR VELOCITY PROCESSING RESULTS (SPRING BOOT)
-- ============================================================================

-- Table: velocity_records (stores individual arc velocity calculations)
CREATE TABLE IF NOT EXISTS velocity_records (
  id BIGSERIAL PRIMARY KEY,
  arc_id VARCHAR(100) NOT NULL,
  line_id VARCHAR(50),
  velocity_m_s DOUBLE PRECISION NOT NULL,
  velocity_km_h DOUBLE PRECISION NOT NULL,
  sample_count INTEGER NOT NULL,
  test_label VARCHAR(200),
  datagram_count BIGINT,
  processing_time_ms BIGINT,
  timestamp TIMESTAMP NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_velocity_arc_id ON velocity_records (arc_id);
CREATE INDEX IF NOT EXISTS idx_velocity_line_id ON velocity_records (line_id);
CREATE INDEX IF NOT EXISTS idx_velocity_timestamp ON velocity_records (timestamp);
CREATE INDEX IF NOT EXISTS idx_velocity_test_label ON velocity_records (test_label);

-- Table: performance_metrics (stores performance test results)
CREATE TABLE IF NOT EXISTS performance_metrics (
  id BIGSERIAL PRIMARY KEY,
  test_label VARCHAR(200) NOT NULL,
  datagram_count BIGINT NOT NULL,
  processing_time_ms BIGINT NOT NULL,
  batch_count INTEGER,
  workers INTEGER,
  throughput_dps DOUBLE PRECISION NOT NULL,
  throughput_dpm DOUBLE PRECISION NOT NULL,
  timestamp TIMESTAMP NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_perf_test_label ON performance_metrics (test_label);
CREATE INDEX IF NOT EXISTS idx_perf_timestamp ON performance_metrics (timestamp);

-- Table: cutoff_analysis (stores cutoff point analysis data)
CREATE TABLE IF NOT EXISTS cutoff_analysis (
  id BIGSERIAL PRIMARY KEY,
  scale VARCHAR(50) NOT NULL,
  workers INTEGER NOT NULL,
  batches INTEGER NOT NULL,
  processing_time_ms BIGINT NOT NULL,
  throughput_dps DOUBLE PRECISION NOT NULL,
  timestamp TIMESTAMP NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_cutoff_scale ON cutoff_analysis (scale);
CREATE INDEX IF NOT EXISTS idx_cutoff_timestamp ON cutoff_analysis (timestamp);

-- Table: summary_stats (stores summary statistics for each test run)
CREATE TABLE IF NOT EXISTS summary_stats (
  id BIGSERIAL PRIMARY KEY,
  test_label VARCHAR(200) NOT NULL,
  datagram_count BIGINT NOT NULL,
  processing_time_ms BIGINT NOT NULL,
  valid_results INTEGER,
  total_results INTEGER,
  total_samples INTEGER,
  avg_velocity_ms DOUBLE PRECISION,
  max_velocity_ms DOUBLE PRECISION,
  min_velocity_ms DOUBLE PRECISION,
  timestamp TIMESTAMP NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_summary_test_label ON summary_stats (test_label);
CREATE INDEX IF NOT EXISTS idx_summary_timestamp ON summary_stats (timestamp);

-- ============================================================================
-- ORIGINAL SCHEMA FOR REAL-TIME SYSTEM
-- ============================================================================

-- Table: event types (catalog)
CREATE TABLE IF NOT EXISTS event_types (
  event_type_id SERIAL PRIMARY KEY,
  code INTEGER NOT NULL UNIQUE,
  name TEXT NOT NULL,
  category TEXT,
  default_priority SMALLINT DEFAULT 5,
  description TEXT
);

-- Table: datagrams (raw events)
CREATE TABLE IF NOT EXISTS datagrams (
  datagram_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  datagram_timestamp TIMESTAMPTZ NOT NULL,
  bus_id TEXT,
  line_id TEXT,
  trip_id TEXT,
  stop_id TEXT,
  arc_id TEXT,
  latitude DOUBLE PRECISION,
  longitude DOUBLE PRECISION,
  odometer DOUBLE PRECISION,
  event_type INTEGER REFERENCES event_types(code),
  priority SMALLINT,
  payload JSONB,
  geom GEOMETRY(POINT, 4326)
);

-- Timescale: convert to hypertable by time
-- SELECT create_hypertable('datagrams', 'datagram_timestamp', chunk_time_interval => interval '7 day');

-- Indexes
CREATE INDEX IF NOT EXISTS idx_datagrams_time ON datagrams (datagram_timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_datagrams_line ON datagrams (line_id);
CREATE INDEX IF NOT EXISTS idx_datagrams_arc ON datagrams (arc_id);
CREATE INDEX IF NOT EXISTS idx_datagrams_bus ON datagrams (bus_id);
CREATE INDEX IF NOT EXISTS idx_datagrams_geom ON datagrams USING GIST (geom);

-- Table: bus_status (latest known state per bus) - optimized for real-time map
CREATE TABLE IF NOT EXISTS bus_status (
  bus_id TEXT PRIMARY KEY,
  last_update TIMESTAMPTZ,
  line_id TEXT,
  trip_id TEXT,
  latitude DOUBLE PRECISION,
  longitude DOUBLE PRECISION,
  speed DOUBLE PRECISION,
  status JSONB,
  geom GEOMETRY(POINT, 4326)
);

CREATE INDEX IF NOT EXISTS idx_bus_status_geom ON bus_status USING GIST (geom);

-- Table: aggregated velocities by arc (partitioning recommended by year_month and line_id)
CREATE TABLE IF NOT EXISTS velocity_by_arc (
  id BIGSERIAL PRIMARY KEY,
  year_month TEXT NOT NULL,
  line_id TEXT NOT NULL,
  arc_id TEXT NOT NULL,
  avg_velocity DOUBLE PRECISION NOT NULL,
  sample_count BIGINT NOT NULL,
  updated_at TIMESTAMPTZ DEFAULT now(),
  UNIQUE (year_month, line_id, arc_id)
);

-- Table: hourly rollups (materialized or job-updated)
CREATE TABLE IF NOT EXISTS velocity_hourly (
  id BIGSERIAL PRIMARY KEY,
  hour TIMESTAMPTZ NOT NULL,
  line_id TEXT NOT NULL,
  arc_id TEXT NOT NULL,
  avg_velocity DOUBLE PRECISION NOT NULL,
  sample_count BIGINT NOT NULL
);

-- Users / roles / permissions
CREATE TABLE IF NOT EXISTS roles (
  id SERIAL PRIMARY KEY,
  name TEXT UNIQUE NOT NULL,
  description TEXT
);

CREATE TABLE IF NOT EXISTS users (
  id SERIAL PRIMARY KEY,
  username TEXT UNIQUE NOT NULL,
  password_hash TEXT NOT NULL,
  role_id INTEGER REFERENCES roles(id),
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS permissions (
  id SERIAL PRIMARY KEY,
  name TEXT UNIQUE NOT NULL,
  description TEXT
);

CREATE TABLE IF NOT EXISTS role_permissions (
  role_id INTEGER REFERENCES roles(id),
  permission_id INTEGER REFERENCES permissions(id),
  PRIMARY KEY (role_id, permission_id)
);

-- Controller assignments (which controller monitors which zone or line)
CREATE TABLE IF NOT EXISTS controller_assignments (
  id BIGSERIAL PRIMARY KEY,
  controller_user_id INTEGER REFERENCES users(id),
  zone_id TEXT,
  line_id TEXT,
  assigned_at TIMESTAMPTZ DEFAULT now()
);

-- Controllers and zones (optional)
CREATE TABLE IF NOT EXISTS zones (
  zone_id TEXT PRIMARY KEY,
  name TEXT,
  geom GEOMETRY(POLYGON, 4326),
  description TEXT
);

-- Event logs (audit) - store ingestion/audit events
CREATE TABLE IF NOT EXISTS ingestion_log (
  id BIGSERIAL PRIMARY KEY,
  source TEXT,
  event TEXT,
  details JSONB,
  created_at TIMESTAMPTZ DEFAULT now()
);

-- Example materialized view creation for velocity_by_arc monthly aggregation
-- CREATE MATERIALIZED VIEW mv_velocity_monthly AS
-- SELECT to_char(datagram_timestamp, 'YYYY_MM') AS year_month, line_id, arc_id,
--        avg(distance_seconds) AS avg_velocity, count(*) as sample_count
-- FROM datagrams
-- WHERE arc_id IS NOT NULL
-- GROUP BY year_month, line_id, arc_id;

-- Notes:
-- - For very high ingestion rates, consider ClickHouse for analytics tables and keep PostgreSQL/Timescale for operational tables like `bus_status`.
-- - Use hashed shard key by line_id when distributing storage across nodes: e.g., shard_key = mod(hashtext(line_id), N_shards)
-- - Implement scheduled jobs (cron/pg_cron) to generate `velocity_hourly` and to downsample older data.


-- Table: datagrams (raw events)
CREATE TABLE IF NOT EXISTS datagrams (
  datagram_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  datagram_timestamp TIMESTAMPTZ NOT NULL,
  bus_id TEXT,
  line_id TEXT,
  trip_id TEXT,
  stop_id TEXT,
  arc_id TEXT,
  latitude DOUBLE PRECISION,
  longitude DOUBLE PRECISION,
  odometer DOUBLE PRECISION,
  event_type INTEGER REFERENCES event_types(code),
  priority SMALLINT,
  payload JSONB,
  geom GEOMETRY(POINT, 4326)
);

-- Timescale: convert to hypertable by time
-- SELECT create_hypertable('datagrams', 'datagram_timestamp', chunk_time_interval => interval '7 day');

-- Indexes
CREATE INDEX IF NOT EXISTS idx_datagrams_time ON datagrams (datagram_timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_datagrams_line ON datagrams (line_id);
CREATE INDEX IF NOT EXISTS idx_datagrams_arc ON datagrams (arc_id);
CREATE INDEX IF NOT EXISTS idx_datagrams_bus ON datagrams (bus_id);
CREATE INDEX IF NOT EXISTS idx_datagrams_geom ON datagrams USING GIST (geom);

-- Table: bus_status (latest known state per bus) - optimized for real-time map
CREATE TABLE IF NOT EXISTS bus_status (
  bus_id TEXT PRIMARY KEY,
  last_update TIMESTAMPTZ,
  line_id TEXT,
  trip_id TEXT,
  latitude DOUBLE PRECISION,
  longitude DOUBLE PRECISION,
  speed DOUBLE PRECISION,
  status JSONB,
  geom GEOMETRY(POINT, 4326)
);

CREATE INDEX IF NOT EXISTS idx_bus_status_geom ON bus_status USING GIST (geom);

-- Table: aggregated velocities by arc (partitioning recommended by year_month and line_id)
CREATE TABLE IF NOT EXISTS velocity_by_arc (
  id BIGSERIAL PRIMARY KEY,
  year_month TEXT NOT NULL,
  line_id TEXT NOT NULL,
  arc_id TEXT NOT NULL,
  avg_velocity DOUBLE PRECISION NOT NULL,
  sample_count BIGINT NOT NULL,
  updated_at TIMESTAMPTZ DEFAULT now(),
  UNIQUE (year_month, line_id, arc_id)
);

-- Table: hourly rollups (materialized or job-updated)
CREATE TABLE IF NOT EXISTS velocity_hourly (
  id BIGSERIAL PRIMARY KEY,
  hour TIMESTAMPTZ NOT NULL,
  line_id TEXT NOT NULL,
  arc_id TEXT NOT NULL,
  avg_velocity DOUBLE PRECISION NOT NULL,
  sample_count BIGINT NOT NULL
);

-- Users / roles / permissions
CREATE TABLE IF NOT EXISTS roles (
  id SERIAL PRIMARY KEY,
  name TEXT UNIQUE NOT NULL,
  description TEXT
);

CREATE TABLE IF NOT EXISTS users (
  id SERIAL PRIMARY KEY,
  username TEXT UNIQUE NOT NULL,
  password_hash TEXT NOT NULL,
  role_id INTEGER REFERENCES roles(id),
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS permissions (
  id SERIAL PRIMARY KEY,
  name TEXT UNIQUE NOT NULL,
  description TEXT
);

CREATE TABLE IF NOT EXISTS role_permissions (
  role_id INTEGER REFERENCES roles(id),
  permission_id INTEGER REFERENCES permissions(id),
  PRIMARY KEY (role_id, permission_id)
);

-- Controller assignments (which controller monitors which zone or line)
CREATE TABLE IF NOT EXISTS controller_assignments (
  id BIGSERIAL PRIMARY KEY,
  controller_user_id INTEGER REFERENCES users(id),
  zone_id TEXT,
  line_id TEXT,
  assigned_at TIMESTAMPTZ DEFAULT now()
);

-- Controllers and zones (optional)
CREATE TABLE IF NOT EXISTS zones (
  zone_id TEXT PRIMARY KEY,
  name TEXT,
  geom GEOMETRY(POLYGON, 4326),
  description TEXT
);

-- Event logs (audit) - store ingestion/audit events
CREATE TABLE IF NOT EXISTS ingestion_log (
  id BIGSERIAL PRIMARY KEY,
  source TEXT,
  event TEXT,
  details JSONB,
  created_at TIMESTAMPTZ DEFAULT now()
);

-- Example materialized view creation for velocity_by_arc monthly aggregation
-- CREATE MATERIALIZED VIEW mv_velocity_monthly AS
-- SELECT to_char(datagram_timestamp, 'YYYY_MM') AS year_month, line_id, arc_id,
--        avg(distance_seconds) AS avg_velocity, count(*) as sample_count
-- FROM datagrams
-- WHERE arc_id IS NOT NULL
-- GROUP BY year_month, line_id, arc_id;

-- Notes:
-- - For very high ingestion rates, consider ClickHouse for analytics tables and keep PostgreSQL/Timescale for operational tables like `bus_status`.
-- - Use hashed shard key by line_id when distributing storage across nodes: e.g., shard_key = mod(hashtext(line_id), N_shards)
-- - Implement scheduled jobs (cron/pg_cron) to generate `velocity_hourly` and to downsample older data.
