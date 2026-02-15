package com.dnsmanager.services;

import com.dnsmanager.config.DatabaseConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Base database service with common operations
 * Provides connection management and helper methods
 */
public abstract class DatabaseService {
    
    /**
     * Get a database connection from the pool
     */
    protected Connection getConnection() throws SQLException {
        return DatabaseConfig.getDataSource().getConnection();
    }
    
    /**
     * Execute a query and return ResultSet
     * Note: Caller must close the ResultSet, Statement, and Connection
     */
    protected ResultSet executeQuery(String sql, Object... params) throws SQLException {
        Connection conn = getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql);
        
        // Set parameters
        for (int i = 0; i < params.length; i++) {
            stmt.setObject(i + 1, params[i]);
        }
        
        return stmt.executeQuery();
    }
    
    /**
     * Execute an update (INSERT, UPDATE, DELETE)
     * Returns number of rows affected
     */
    protected int executeUpdate(String sql, Object... params) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            // Set parameters
            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }
            
            return stmt.executeUpdate();
        }
    }
    
    /**
     * Execute an insert and return generated ID
     */
    protected int executeInsert(String sql, Object... params) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, 
                 PreparedStatement.RETURN_GENERATED_KEYS)) {
            
            // Set parameters
            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }
            
            int affectedRows = stmt.executeUpdate();
            
            if (affectedRows == 0) {
                throw new SQLException("Insert failed, no rows affected");
            }
            
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                } else {
                    throw new SQLException("Insert failed, no ID obtained");
                }
            }
        }
    }
    
    /**
     * Begin a transaction
     */
    protected Connection beginTransaction() throws SQLException {
        Connection conn = getConnection();
        conn.setAutoCommit(false);
        return conn;
    }
    
    /**
     * Commit a transaction
     */
    protected void commit(Connection conn) throws SQLException {
        if (conn != null) {
            conn.commit();
            conn.setAutoCommit(true);
        }
    }
    
    /**
     * Rollback a transaction
     */
    protected void rollback(Connection conn) {
        if (conn != null) {
            try {
                conn.rollback();
                conn.setAutoCommit(true);
            } catch (SQLException e) {
                System.err.println("Error rolling back transaction: " + e.getMessage());
            }
        }
    }
    
    /**
     * Close resources safely
     */
    protected void closeQuietly(AutoCloseable... resources) {
        for (AutoCloseable resource : resources) {
            if (resource != null) {
                try {
                    resource.close();
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
    }
}
