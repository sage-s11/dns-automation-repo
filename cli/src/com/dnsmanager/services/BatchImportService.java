package com.dnsmanager.services;


import com.dnsmanager.models.Zone;
import com.dnsmanager.services.CSVParser.CSVRecord;
import java.sql.*;
import java.util.List;

/**
 * Batch import service for high-performance bulk inserts
 * Uses single connection + batch operations
 */
public class BatchImportService extends DatabaseService {
    
    private ZoneServiceDB zoneService;
    
    public BatchImportService() {
        this.zoneService = new ZoneServiceDB();
    }
    
    /**
     * Import multiple records in a single batch transaction
     * FAST: Single connection + batch insert
     */
    public BatchResult batchImport(List<CSVRecord> records) throws SQLException {
        BatchResult result = new BatchResult();
        
        // Single connection for entire operation
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);  // Start transaction
            
            try {
                // Group records by zone
                for (CSVRecord record : records) {
                    // Get or create zone (cached to avoid repeated queries)
                    Zone zone = getOrCreateZone(record.zone, conn);
                    
                    if (zone == null) {
                        result.failed++;
                        result.errors.add("Zone creation failed: " + record.zone);
                        continue;
                    }
                    
                    // Add to batch
                    if (record.type.equals("A")) {
                        insertARecordBatch(zone.getId(), record, conn);
                        result.created++;
                    } else {
                        result.skipped++;
                    }
                }
                
                // Execute batch (all inserts at once!)
                executeBatch(conn);
                
                // Commit transaction
                conn.commit();
                
            } catch (SQLException e) {
                conn.rollback();  // Rollback on error
                throw e;
            }
        }
        
        return result;
    }
    
    /**
     * Batch insert A records (all at once)
     */
    private PreparedStatement batchStmt = null;
    
    private void insertARecordBatch(int zoneId, CSVRecord record, Connection conn) throws SQLException {
        if (batchStmt == null) {
            String sql = "INSERT INTO dns_records (zone_id, hostname, type, value, ttl) " +
                        "VALUES (?, ?, 'A', ?::inet, ?)";
            batchStmt = conn.prepareStatement(sql);
        }
        
        batchStmt.setInt(1, zoneId);
        batchStmt.setString(2, record.hostname);
        batchStmt.setString(3, record.ip);
        batchStmt.setInt(4, record.ttl);
        batchStmt.addBatch();  // Add to batch
    }
    
    private void executeBatch(Connection conn) throws SQLException {
        if (batchStmt != null) {
            batchStmt.executeBatch();  // Execute all at once!
            batchStmt.close();
            batchStmt = null;
        }
    }
    
    /**
     * Get or create zone (with caching to avoid repeated queries)
     */
    private Zone cachedZone = null;
    
    private Zone getOrCreateZone(String zoneName, Connection conn) throws SQLException {
        // Check cache first
        if (cachedZone != null && cachedZone.getName().equals(zoneName)) {
            return cachedZone;
        }
        
        // Query database
        Zone zone = zoneService.getZone(zoneName);
        
        if (zone == null) {
            // Create zone if it doesn't exist
            zone = zoneService.createZone(zoneName, "10.10.10.1");
        }
        
        cachedZone = zone;  // Cache it
        return zone;
    }
    
    /**
     * Result of batch import
     */
    public static class BatchResult {
        public int created = 0;
        public int failed = 0;
        public int skipped = 0;
        public java.util.List<String> errors = new java.util.ArrayList<>();
    }
}
