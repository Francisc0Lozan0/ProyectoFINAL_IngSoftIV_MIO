-- Script de configuración rápida para SITM-MIO con PostgreSQL
-- Ejecutar este script para configurar la base de datos

-- 1. Crear base de datos (ejecutar como superusuario)
-- DROP DATABASE IF EXISTS sitm_mio;
-- CREATE DATABASE sitm_mio;

-- 2. Conectar a la base de datos
\c sitm_mio

-- 3. Crear extensiones necesarias (opcional, comentar si no tienes permisos)
-- CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
-- CREATE EXTENSION IF NOT EXISTS postgis;

-- 4. Verificar tablas creadas
SELECT 
    schemaname,
    tablename,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS size
FROM pg_tables
WHERE schemaname = 'public'
ORDER BY tablename;

-- 5. Consultas útiles para verificación

-- Ver últimos resultados de velocidad
SELECT 
    test_label, 
    COUNT(*) as total_registros,
    AVG(velocity_km_h) as velocidad_promedio_kmh,
    MAX(created_at) as ultima_actualizacion
FROM velocity_records 
GROUP BY test_label 
ORDER BY ultima_actualizacion DESC;

-- Ver métricas de performance
SELECT 
    test_label,
    datagram_count,
    processing_time_ms,
    throughput_dps,
    workers,
    timestamp
FROM performance_metrics
ORDER BY timestamp DESC
LIMIT 10;

-- Ver estadísticas resumidas
SELECT 
    test_label,
    datagram_count,
    valid_results,
    total_results,
    avg_velocity_ms * 3.6 as avg_velocity_kmh,
    timestamp
FROM summary_stats
ORDER BY timestamp DESC
LIMIT 10;

-- Ver análisis de punto de corte
SELECT * FROM cutoff_analysis
ORDER BY timestamp DESC
LIMIT 10;
