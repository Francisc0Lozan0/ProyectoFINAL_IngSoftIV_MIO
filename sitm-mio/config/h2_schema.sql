-- Schema para base de datos H2
-- Tabla de velocidades calculadas
CREATE TABLE IF NOT EXISTS velocities (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    arc_id VARCHAR(100) NOT NULL,
    velocity_m_s DOUBLE NOT NULL,
    velocity_km_h DOUBLE NOT NULL,
    sample_count INT NOT NULL,
    line_id VARCHAR(50),
    timestamp TIMESTAMP NOT NULL,
    test_label VARCHAR(100) NOT NULL,
    INDEX idx_arc_id (arc_id),
    INDEX idx_line_id (line_id),
    INDEX idx_test_label (test_label),
    INDEX idx_timestamp (timestamp)
);

-- Tabla de métricas de rendimiento
CREATE TABLE IF NOT EXISTS performance_metrics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    test_label VARCHAR(100) NOT NULL UNIQUE,
    timestamp TIMESTAMP NOT NULL,
    datagram_count BIGINT NOT NULL,
    processing_time_ms BIGINT NOT NULL,
    batch_count INT NOT NULL,
    workers INT NOT NULL,
    throughput_dps DOUBLE NOT NULL,
    throughput_dpm DOUBLE NOT NULL,
    INDEX idx_test_label (test_label),
    INDEX idx_timestamp (timestamp)
);

-- Tabla para estadísticas agregadas por arco
CREATE TABLE IF NOT EXISTS arc_statistics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    arc_id VARCHAR(100) NOT NULL,
    line_id VARCHAR(50),
    avg_velocity_km_h DOUBLE NOT NULL,
    min_velocity_km_h DOUBLE NOT NULL,
    max_velocity_km_h DOUBLE NOT NULL,
    total_samples INT NOT NULL,
    last_updated TIMESTAMP NOT NULL,
    UNIQUE (arc_id),
    INDEX idx_line_id (line_id)
);
