package com.dnsmanager.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;

public class DatabaseConfig {
    
    private static HikariDataSource dataSource;
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/dnsmanager";
    private static final String DB_USER = "dnsadmin";
    private static final String DB_PASSWORD = "root";
    private static final int MAX_POOL_SIZE = 10;
    private static final int MIN_IDLE = 2;
    
    public static synchronized void initialize() {
        if (dataSource == null) {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(DB_URL);
            config.setUsername(DB_USER);
            config.setPassword(DB_PASSWORD);
            config.setMaximumPoolSize(MAX_POOL_SIZE);
            config.setMinimumIdle(MIN_IDLE);
            config.setPoolName("DNSManagerPool");
            
            dataSource = new HikariDataSource(config);
            System.out.println("✅ Database connection pool initialized");
        }
    }
    
    public static DataSource getDataSource() {
        if (dataSource == null) {
            initialize();
        }
        return dataSource;
    }
    
    public static void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            System.out.println("✅ Database connection pool closed");
        }
    }
    
    public static boolean testConnection() {
        try (var conn = getDataSource().getConnection()) {
            return conn.isValid(5);
        } catch (Exception e) {
            System.err.println("❌ Database connection test failed: " + e.getMessage());
            return false;
        }
    }
}
