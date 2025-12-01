package com.sitm.mio.loader;

import com.sitm.mio.persistence.DBConnection;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Stream;

/**
 * Carga los datos de los archivos CSV de results/ a la base de datos H2
 */
public class ResultsLoader {
    
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;
    
    public static void main(String[] args) {
        String resultsDir = args.length > 0 ? args[0] : "./results";
        
        try {
            System.out.println("🔄 Iniciando carga de datos desde: " + resultsDir);
            
            // Crear tablas
            createTables();
            
            // Cargar velocidades
            loadVelocityFiles(resultsDir);
            
            // Cargar métricas de rendimiento
            loadPerformanceFiles(resultsDir);
            
            // Actualizar estadísticas agregadas
            updateArcStatistics();
            
            System.out.println("✅ Carga completada exitosamente");
            
        } catch (Exception e) {
            System.err.println("❌ Error durante la carga: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void createTables() throws Exception {
        System.out.println("📋 Creando tablas...");
        
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            
            // Tabla de velocidades
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS velocities (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                "arc_id VARCHAR(100) NOT NULL, " +
                "velocity_m_s DOUBLE NOT NULL, " +
                "velocity_km_h DOUBLE NOT NULL, " +
                "sample_count INT NOT NULL, " +
                "line_id VARCHAR(50), " +
                "timestamp TIMESTAMP NOT NULL, " +
                "test_label VARCHAR(100) NOT NULL)"
            );
            
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_arc_id ON velocities(arc_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_test_label ON velocities(test_label)");
            
            // Tabla de métricas de rendimiento
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS performance_metrics (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                "test_label VARCHAR(100) NOT NULL UNIQUE, " +
                "timestamp TIMESTAMP NOT NULL, " +
                "datagram_count BIGINT NOT NULL, " +
                "processing_time_ms BIGINT NOT NULL, " +
                "batch_count INT NOT NULL, " +
                "workers INT NOT NULL, " +
                "throughput_dps DOUBLE NOT NULL, " +
                "throughput_dpm DOUBLE NOT NULL)"
            );
            
            System.out.println("✓ Tablas creadas/verificadas");
        }
    }
    
    private static void loadVelocityFiles(String resultsDir) throws Exception {
        System.out.println("📂 Buscando archivos de velocidades...");
        
        try (Stream<Path> paths = Files.walk(Paths.get(resultsDir))) {
            paths.filter(p -> p.getFileName().toString().startsWith("velocities_") 
                           && p.getFileName().toString().endsWith(".csv"))
                 .forEach(path -> {
                     try {
                         loadVelocityFile(path);
                     } catch (Exception e) {
                         System.err.println("Error cargando " + path + ": " + e.getMessage());
                     }
                 });
        }
    }
    
    private static void loadVelocityFile(Path filePath) throws Exception {
        System.out.println("  Cargando: " + filePath.getFileName());
        
        String testLabel = extractTestLabel(filePath.getFileName().toString());
        int loaded = 0;
        
        try (Connection conn = DBConnection.getConnection();
             BufferedReader reader = new BufferedReader(new FileReader(filePath.toFile()))) {
            
            String line;
            boolean headerSkipped = false;
            
            // Preparar statement
            String sql = "INSERT INTO velocities (arc_id, velocity_m_s, velocity_km_h, " +
                        "sample_count, line_id, timestamp, test_label) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)";
            
            PreparedStatement pstmt = conn.prepareStatement(sql);
            
            while ((line = reader.readLine()) != null) {
                // Saltar comentarios y cabecera
                if (line.startsWith("#") || line.trim().isEmpty()) continue;
                
                if (!headerSkipped) {
                    headerSkipped = true;
                    continue; // Saltar línea de encabezado CSV
                }
                
                String[] parts = line.split(",");
                if (parts.length < 6) continue;
                
                pstmt.setString(1, parts[0]); // arc_id
                pstmt.setDouble(2, Double.parseDouble(parts[1])); // velocity_m_s
                pstmt.setDouble(3, Double.parseDouble(parts[2])); // velocity_km_h
                pstmt.setInt(4, Integer.parseInt(parts[3])); // sample_count
                pstmt.setString(5, parts[4]); // line_id
                pstmt.setTimestamp(6, Timestamp.valueOf(LocalDateTime.parse(parts[5], ISO_FORMATTER)));
                pstmt.setString(7, testLabel);
                
                pstmt.addBatch();
                loaded++;
                
                if (loaded % 1000 == 0) {
                    pstmt.executeBatch();
                }
            }
            
            pstmt.executeBatch();
            pstmt.close();
        }
        
        System.out.println("    ✓ Cargados " + loaded + " registros");
    }
    
    private static void loadPerformanceFiles(String resultsDir) throws Exception {
        System.out.println("📊 Buscando archivos de rendimiento...");
        
        try (Stream<Path> paths = Files.walk(Paths.get(resultsDir))) {
            paths.filter(p -> p.getFileName().toString().startsWith("performance_") 
                           && p.getFileName().toString().endsWith(".csv"))
                 .forEach(path -> {
                     try {
                         loadPerformanceFile(path);
                     } catch (Exception e) {
                         System.err.println("Error cargando " + path + ": " + e.getMessage());
                     }
                 });
        }
    }
    
    private static void loadPerformanceFile(Path filePath) throws Exception {
        System.out.println("  Cargando: " + filePath.getFileName());
        
        try (Connection conn = DBConnection.getConnection();
             BufferedReader reader = new BufferedReader(new FileReader(filePath.toFile()))) {
            
            String line;
            boolean headerSkipped = false;
            
            String sql = "MERGE INTO performance_metrics (test_label, timestamp, datagram_count, " +
                        "processing_time_ms, batch_count, workers, throughput_dps, throughput_dpm) " +
                        "KEY(test_label) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            
            PreparedStatement pstmt = conn.prepareStatement(sql);
            
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                
                if (!headerSkipped) {
                    headerSkipped = true;
                    continue;
                }
                
                String[] parts = line.split(",");
                if (parts.length < 8) continue;
                
                pstmt.setString(1, parts[0]); // test_label
                pstmt.setTimestamp(2, Timestamp.valueOf(LocalDateTime.parse(parts[1], ISO_FORMATTER)));
                pstmt.setLong(3, Long.parseLong(parts[2])); // datagram_count
                pstmt.setLong(4, Long.parseLong(parts[3])); // processing_time_ms
                pstmt.setInt(5, Integer.parseInt(parts[4])); // batch_count
                pstmt.setInt(6, Integer.parseInt(parts[5])); // workers
                pstmt.setDouble(7, Double.parseDouble(parts[6])); // throughput_dps
                pstmt.setDouble(8, Double.parseDouble(parts[7])); // throughput_dpm
                
                pstmt.executeUpdate();
            }
            
            pstmt.close();
            System.out.println("    ✓ Métricas cargadas");
        }
    }
    
    private static void updateArcStatistics() throws Exception {
        System.out.println("📈 Actualizando estadísticas agregadas...");
        
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS arc_statistics (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                "arc_id VARCHAR(100) NOT NULL UNIQUE, " +
                "line_id VARCHAR(50), " +
                "avg_velocity_km_h DOUBLE NOT NULL, " +
                "min_velocity_km_h DOUBLE NOT NULL, " +
                "max_velocity_km_h DOUBLE NOT NULL, " +
                "total_samples INT NOT NULL, " +
                "last_updated TIMESTAMP NOT NULL)"
            );
            
            stmt.execute("DELETE FROM arc_statistics");
            
            stmt.execute(
                "INSERT INTO arc_statistics " +
                "(arc_id, line_id, avg_velocity_km_h, min_velocity_km_h, " +
                "max_velocity_km_h, total_samples, last_updated) " +
                "SELECT arc_id, MAX(line_id), " +
                "AVG(velocity_km_h), MIN(velocity_km_h), MAX(velocity_km_h), " +
                "SUM(sample_count), MAX(timestamp) " +
                "FROM velocities GROUP BY arc_id"
            );
            
            System.out.println("✓ Estadísticas actualizadas");
        }
    }
    
    private static String extractTestLabel(String fileName) {
        // velocities_1_MIL_20251130_161751.csv -> 1_MIL
        String name = fileName.replace("velocities_", "").replace(".csv", "");
        int lastUnderscore = name.lastIndexOf('_');
        if (lastUnderscore > 0) {
            name = name.substring(0, lastUnderscore);
            lastUnderscore = name.lastIndexOf('_');
            if (lastUnderscore > 0) {
                return name.substring(0, lastUnderscore);
            }
        }
        return name;
    }
}
