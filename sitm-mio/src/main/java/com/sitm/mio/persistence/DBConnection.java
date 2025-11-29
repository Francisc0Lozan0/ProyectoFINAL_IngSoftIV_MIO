package com.sitm.mio.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;

public class DBConnection {
    private static HikariDataSource ds;

    static {
        try {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(System.getenv().getOrDefault("JDBC_URL", "jdbc:postgresql://localhost:5432/sitm"));
            config.setUsername(System.getenv().getOrDefault("JDBC_USER", "postgres"));
            config.setPassword(System.getenv().getOrDefault("JDBC_PASSWORD", "postgres"));
            config.setMaximumPoolSize(Integer.parseInt(System.getenv().getOrDefault("JDBC_MAX_POOL", "10")));
            config.setDriverClassName("org.postgresql.Driver");
            ds = new HikariDataSource(config);
        } catch (Exception e) {
            System.err.println("Error initializing DB pool: " + e.getMessage());
        }
    }

    private DBConnection() {}

    public static Connection getConnection() throws SQLException {
        if (ds == null) throw new SQLException("DataSource not initialized");
        return ds.getConnection();
    }
}
