package com.sitm.mio.loader;

import com.sitm.mio.persistence.DBConnection;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Carga datos de velocidades desde archivos CSV de results a la base de datos H2
 */
public class VelocityDataLoader {
    
    private static final String RESULTS_DIR = "results";
    private static final String INSERT_SQL = 
        "INSERT INTO velocity_records (arc_id, velocity_m_s, velocity_km_h, sample_count, line_id, test_label, timestamp, created_at) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";
    
    /**
     * Carga todos los archivos CSV de velocidades desde el directorio results
     */
    public static int loadAllVelocityFiles() throws IOException, SQLException {
        Path resultsPath = Paths.get(RESULTS_DIR);
        
        if (!Files.exists(resultsPath)) {
            System.err.println("Directorio results no existe: " + resultsPath.toAbsolutePath());
            return 0;
        }
        
        int totalRecords = 0;
        
        try (Stream<Path> paths = Files.walk(resultsPath)) {
            List<Path> csvFiles = paths
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().startsWith("velocities_10_MILLONES"))
                .toList();
            
            System.out.println("Encontrados " + csvFiles.size() + " archivos de velocidades (10_MILLONES)");
            
            for (Path csvFile : csvFiles) {
                // Extraer test_label del nombre del archivo (e.g., "velocities_1_MIL_..." -> "1_MIL")
                String fileName = csvFile.getFileName().toString();
                String testLabel = fileName.replace("velocities_", "").replaceAll("_\\d{8}_\\d{6}\\.csv$", "");
                
                int records = loadVelocityFile(csvFile, testLabel);
                totalRecords += records;
                System.out.println("  - " + csvFile.getFileName() + ": " + records + " registros");
            }
        }
        
        return totalRecords;
    }
    
    /**
     * Carga un archivo CSV de velocidades a la base de datos
     */
    public static int loadVelocityFile(Path csvFile, String testLabel) throws IOException, SQLException {
        int recordCount = 0;
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            conn = DBConnection.getConnection();
            if (conn == null) {
                throw new SQLException("No se pudo obtener conexión a la base de datos");
            }
            
            conn.setAutoCommit(false);
            stmt = conn.prepareStatement(INSERT_SQL);
            
            try (BufferedReader reader = Files.newBufferedReader(csvFile, java.nio.charset.StandardCharsets.ISO_8859_1)) {
                String line;
                boolean headerFound = false;
                
                while ((line = reader.readLine()) != null) {
                    // Saltar comentarios
                    if (line.startsWith("#")) continue;
                    
                    // Saltar header
                    if (!headerFound) {
                        if (line.startsWith("arc_id")) {
                            headerFound = true;
                        }
                        continue;
                    }
                    
                    // Parsear línea
                    String[] parts = line.split(",");
                    if (parts.length < 6) continue;
                    
                    try {
                        stmt.setString(1, parts[0].trim()); // arc_id
                        stmt.setDouble(2, parseDouble(parts[1])); // velocity_m_s
                        stmt.setDouble(3, parseDouble(parts[2])); // velocity_km_h
                        stmt.setInt(4, parseInt(parts[3])); // sample_count
                        stmt.setString(5, parts[4].trim()); // line_id
                        stmt.setString(6, testLabel); // test_label
                        stmt.setString(7, parts[5].trim()); // timestamp
                        // created_at se establece automáticamente con CURRENT_TIMESTAMP
                        
                        stmt.addBatch();
                        recordCount++;
                        
                        // Ejecutar batch cada 1000 registros
                        if (recordCount % 1000 == 0) {
                            stmt.executeBatch();
                            conn.commit();
                        }
                    } catch (NumberFormatException e) {
                        // Ignorar líneas con errores
                        System.err.println("Error parseando línea: " + line);
                    }
                }
                
                // Ejecutar registros restantes
                if (recordCount % 1000 != 0) {
                    stmt.executeBatch();
                    conn.commit();
                }
            }
            
        } finally {
            if (stmt != null) stmt.close();
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
        
        return recordCount;
    }
    
    /**
     * Crea la tabla velocity_records si no existe
     */
    public static void createTableIfNotExists() throws SQLException {
        String createTableSQL = 
            "CREATE TABLE IF NOT EXISTS velocity_records (" +
            "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
            "  arc_id VARCHAR(100) NOT NULL," +
            "  line_id VARCHAR(50)," +
            "  velocity_m_s DOUBLE NOT NULL," +
            "  velocity_km_h DOUBLE NOT NULL," +
            "  sample_count INT NOT NULL," +
            "  test_label VARCHAR(200)," +
            "  timestamp TIMESTAMP NOT NULL," +
            "  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP" +
            ")";
        
        String createIndexArc = "CREATE INDEX IF NOT EXISTS idx_velocity_arc_id ON velocity_records (arc_id)";
        String createIndexLine = "CREATE INDEX IF NOT EXISTS idx_velocity_line_id ON velocity_records (line_id)";
        String createIndexTest = "CREATE INDEX IF NOT EXISTS idx_velocity_test_label ON velocity_records (test_label)";
        
        try (Connection conn = DBConnection.getConnection()) {
            if (conn == null) return;
            
            conn.createStatement().execute(createTableSQL);
            conn.createStatement().execute(createIndexArc);
            conn.createStatement().execute(createIndexLine);
            conn.createStatement().execute(createIndexTest);
            
            // Crear vista (eliminar primero si existe)
            try {
                conn.createStatement().execute("DROP VIEW IF EXISTS velocities");
                conn.createStatement().execute(
                    "CREATE VIEW velocities AS " +
                    "SELECT id, arc_id, line_id, velocity_m_s, velocity_km_h, " +
                    "sample_count, test_label, timestamp " +
                    "FROM velocity_records"
                );
                System.out.println("✓ Vista 'velocities' creada correctamente");
            } catch (SQLException e) {
                System.err.println("⚠ Error creando vista: " + e.getMessage());
            }
            
            System.out.println("✓ Tabla velocity_records verificada/creada");
        }
    }
    
    /**
     * Limpia la tabla de velocidades
     */
    public static void clearVelocities() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            if (conn == null) return;
            
            conn.createStatement().execute("DELETE FROM velocity_records");
            System.out.println("Tabla velocity_records limpiada");
        }
    }
    
    /**
     * Cuenta registros en la tabla velocities
     */
    public static int countVelocities() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            if (conn == null) return 0;
            
            var rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM velocity_records");
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }
    
    private static double parseDouble(String value) {
        return Double.parseDouble(value.replace(",", ".").trim());
    }
    
    private static int parseInt(String value) {
        return Integer.parseInt(value.trim());
    }
    
    /**
     * Método main para ejecutar la carga de datos
     */
    public static void main(String[] args) {
        System.out.println("=== Cargador de Datos de Velocidades ===");
        
        try {
            // Verificar conexión
            Connection conn = DBConnection.getConnection();
            if (conn == null) {
                System.err.println("ERROR: No se pudo conectar a la base de datos");
                return;
            }
            conn.close();
            
            System.out.println("Conexión a H2 establecida correctamente");
            
            // Crear tabla si no existe
            createTableIfNotExists();
            
            // Contar registros existentes
            int existingCount = countVelocities();
            System.out.println("Registros existentes en la tabla: " + existingCount);
            
            if (existingCount > 0) {
                System.out.println("Limpiando tabla de velocidades...");
                clearVelocities();
            }
            
            // Cargar archivos
            System.out.println("\nCargando archivos CSV...");
            int totalRecords = loadAllVelocityFiles();
            
            System.out.println("\n=== Resumen ===");
            System.out.println("Total de registros cargados: " + totalRecords);
            System.out.println("Registros en la tabla: " + countVelocities());
            
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
