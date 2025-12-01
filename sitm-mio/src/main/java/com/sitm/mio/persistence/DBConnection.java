package com.sitm.mio.persistence;

import java.sql.Connection;
import java.sql.SQLException;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class DBConnection {
    private static HikariDataSource ds;
    private static boolean dbAvailable = false;

    static {
        try {
            HikariConfig config = new HikariConfig();
            
            // Intentar H2 primero (para desarrollo sin Docker)
            String jdbcUrl = System.getenv().getOrDefault("JDBC_URL", 
                "jdbc:h2:file:./data/sitm_mio;DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE");
            config.setJdbcUrl(jdbcUrl);
            
            if (jdbcUrl.contains("h2")) {
                config.setDriverClassName("org.h2.Driver");
                config.setUsername("sa");
                config.setPassword("");
            } else {
                config.setDriverClassName("org.postgresql.Driver");
                config.setUsername(System.getenv().getOrDefault("JDBC_USER", "postgres"));
                config.setPassword(System.getenv().getOrDefault("JDBC_PASSWORD", "postgres"));
            }
            
            config.setMaximumPoolSize(Integer.parseInt(System.getenv().getOrDefault("JDBC_MAX_POOL", "10")));
            
            // Configuración de timeouts para fallar rápido si no hay DB
            config.setConnectionTimeout(3000); // 3 segundos
            config.setValidationTimeout(2000);
            
            ds = new HikariDataSource(config);
            
            // Prueba la conexión
            try (Connection conn = ds.getConnection()) {
                dbAvailable = true;
                System.out.println("✓ Database connection established successfully");
            }
        } catch (Exception e) {
            System.err.println("⚠ Warning: Database not available - " + e.getMessage());
            System.err.println("  System will run WITHOUT persistence (velocities won't be saved)");
            System.err.println("  To enable DB: Create 'sitm' database and set JDBC_* environment variables");
            dbAvailable = false;
            ds = null;
        }
    }

    private DBConnection() {}

    public static Connection getConnection() throws SQLException {
        if (!dbAvailable || ds == null) {
            throw new SQLException("Database not available - running in memory-only mode");
        }
        return ds.getConnection();
    }
    
    public static boolean isAvailable() {
        return dbAvailable;
    }
}