package com.dnsmanager.services;

import com.dnsmanager.config.DatabaseConfig;

import java.io.BufferedReader;
import java.io.FileReader;
import java.sql.*;
import java.util.*;

/**
 * Cleanup Service - Handle DNS record deletions with network validation
 * 
 * SAFETY RULES:
 * 1. Record exists + IP responding = BLOCK deletion (device active!)
 * 2. Record exists + IP not responding = ALLOW deletion (safe)
 * 3. Record doesn't exist + IP responding = SKIP (no record to delete)
 * 4. Record doesn't exist + IP not responding = SKIP (no record)
 * 
 * Features:
 * - Delete by hostname, pattern, IP, zone
 * - Network validation before deletion
 * - Bulk deletion from CSV
 * - Test data cleanup
 * - Transaction support with rollback
 */
public class CleanupService extends DatabaseService {
    
    private EnterpriseNetworkValidator networkValidator;
    
    public CleanupService() {
        this.networkValidator = new EnterpriseNetworkValidator();
    }
    
    /**
     * Validate records before deletion (check network status)
     */
    public DeletionValidationResult validateForDeletion(List<Map<String, Object>> records) {
        DeletionValidationResult result = new DeletionValidationResult();
        
        // Extract IPs from records
        List<String> ipsToCheck = new ArrayList<>();
        Map<String, Map<String, Object>> ipToRecordMap = new HashMap<>();
        
        for (Map<String, Object> record : records) {
            String ip = record.get("value").toString();
            // Only validate A records with IP addresses
            if (record.get("type").equals("A") && isValidIP(ip)) {
                ipsToCheck.add(ip);
                ipToRecordMap.put(ip, record);
            }
        }
        
        if (ipsToCheck.isEmpty()) {
            // No IPs to validate (e.g., CNAME records)
            result.safeToDelete.addAll(records);
            return result;
        }
        
        System.out.println("🔍 Validating " + ipsToCheck.size() + " IP(s) before deletion...");
        
        // Validate IPs in parallel
        Map<String, EnterpriseNetworkValidator.IPValidationResult> validationResults = 
            networkValidator.validateIPs(ipsToCheck);
        
        // Categorize records based on validation
        for (Map<String, Object> record : records) {
            String ip = record.get("value").toString();
            
            if (!record.get("type").equals("A") || !isValidIP(ip)) {
                // Non-A records or invalid IPs - safe to delete
                result.safeToDelete.add(record);
                continue;
            }
            
            EnterpriseNetworkValidator.IPValidationResult validation = validationResults.get(ip);
            
            if (validation != null && validation.isActive) {
                // CASE 1: Record exists + IP responding = BLOCK
                result.activeIPs.add(record);
                result.blockedReasons.put(ip, "IP is currently active (" + 
                    validation.detectionMethod + ")");
            } else {
                // CASE 2: Record exists + IP not responding = SAFE
                result.safeToDelete.add(record);
            }
        }
        
        return result;
    }
    
    /**
     * Result of deletion validation
     */
    public static class DeletionValidationResult {
        public List<Map<String, Object>> safeToDelete = new ArrayList<>();
        public List<Map<String, Object>> activeIPs = new ArrayList<>();
        public Map<String, String> blockedReasons = new HashMap<>();
        
        public boolean hasBlockedRecords() {
            return !activeIPs.isEmpty();
        }
        
        public int getSafeCount() {
            return safeToDelete.size();
        }
        
        public int getBlockedCount() {
            return activeIPs.size();
        }
    }
    
    /**
     * Check if string is valid IP address
     */
    private boolean isValidIP(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        
        try {
            for (String part : parts) {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255) {
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * Find records by exact hostname
     */
    public List<Map<String, Object>> findByHostname(String hostname) throws SQLException {
        String sql = "SELECT dr.id, dr.hostname, dr.type, dr.value, z.name as zone " +
                     "FROM dns_records dr " +
                     "JOIN zones z ON dr.zone_id = z.id " +
                     "WHERE dr.hostname = ? " +
                     "ORDER BY z.name, dr.hostname";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, hostname);
            return executeQuery(stmt);
        }
    }
    
    /**
     * Find records by pattern (wildcard)
     * Pattern: "test-*" converts to SQL "test-%"
     */
    public List<Map<String, Object>> findByPattern(String pattern) throws SQLException {
        // Convert shell wildcard to SQL wildcard
        String sqlPattern = pattern.replace("*", "%");
        
        String sql = "SELECT dr.id, dr.hostname, dr.type, dr.value, z.name as zone " +
                     "FROM dns_records dr " +
                     "JOIN zones z ON dr.zone_id = z.id " +
                     "WHERE dr.hostname LIKE ? " +
                     "ORDER BY z.name, dr.hostname";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, sqlPattern);
            return executeQuery(stmt);
        }
    }
    
    /**
     * Find records by IP address
     */
    public List<Map<String, Object>> findByIP(String ip) throws SQLException {
        String sql = "SELECT dr.id, dr.hostname, dr.type, dr.value, z.name as zone " +
                     "FROM dns_records dr " +
                     "JOIN zones z ON dr.zone_id = z.id " +
                     "WHERE dr.value = ? " +
                     "ORDER BY z.name, dr.hostname";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, ip);
            return executeQuery(stmt);
        }
    }
    
    /**
     * Find records by zone name
     */
    public List<Map<String, Object>> findByZone(String zoneName) throws SQLException {
        String sql = "SELECT dr.id, dr.hostname, dr.type, dr.value, z.name as zone " +
                     "FROM dns_records dr " +
                     "JOIN zones z ON dr.zone_id = z.id " +
                     "WHERE z.name = ? " +
                     "ORDER BY dr.hostname";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, zoneName);
            return executeQuery(stmt);
        }
    }
    
    /**
     * Find records from CSV file
     * CSV format: hostname,zone (optional)
     */
    public List<Map<String, Object>> findFromCSV(String csvFile) throws SQLException {
        List<Map<String, Object>> allRecords = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(csvFile))) {
            String line;
            boolean firstLine = true;
            
            while ((line = reader.readLine()) != null) {
                // Skip header
                if (firstLine) {
                    firstLine = false;
                    if (line.toLowerCase().contains("hostname")) {
                        continue;
                    }
                }
                
                line = line.trim();
                if (line.isEmpty()) continue;
                
                String[] parts = line.split(",");
                String hostname = parts[0].trim();
                
                // Find records for this hostname
                List<Map<String, Object>> records = findByHostname(hostname);
                allRecords.addAll(records);
            }
            
        } catch (Exception e) {
            throw new SQLException("Error reading CSV file: " + e.getMessage(), e);
        }
        
        return allRecords;
    }
    
    /**
     * Find test data by patterns
     */
    public List<Map<String, Object>> findTestData(String[] patterns) throws SQLException {
        List<Map<String, Object>> allRecords = new ArrayList<>();
        Set<Integer> seenIds = new HashSet<>();  // Avoid duplicates
        
        for (String pattern : patterns) {
            List<Map<String, Object>> records = findByPattern(pattern);
            
            for (Map<String, Object> record : records) {
                Integer id = (Integer) record.get("id");
                if (!seenIds.contains(id)) {
                    seenIds.add(id);
                    allRecords.add(record);
                }
            }
        }
        
        return allRecords;
    }
    
    /**
     * Delete records by exact hostname (with network validation)
     */
    public int deleteByHostname(String hostname, boolean skipValidation) throws SQLException {
        if (skipValidation) {
            return deleteByHostnameUnsafe(hostname);
        }
        
        // Find records first
        List<Map<String, Object>> records = findByHostname(hostname);
        if (records.isEmpty()) {
            return 0;
        }
        
        // Validate network status
        DeletionValidationResult validation = validateForDeletion(records);
        
        if (validation.hasBlockedRecords()) {
            throw new SQLException("Cannot delete: " + validation.getBlockedCount() + 
                " record(s) have active IPs. Use --force to override.");
        }
        
        // Delete only safe records
        return deleteSafeRecords(validation.safeToDelete);
    }
    
    /**
     * Delete without validation (use with caution)
     */
    private int deleteByHostnameUnsafe(String hostname) throws SQLException {
        String sql = "DELETE FROM dns_records WHERE hostname = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, hostname);
            return stmt.executeUpdate();
        }
    }
    
    /**
     * Delete safe records (after validation)
     */
    private int deleteSafeRecords(List<Map<String, Object>> records) throws SQLException {
        if (records.isEmpty()) {
            return 0;
        }
        
        List<Integer> ids = new ArrayList<>();
        for (Map<String, Object> record : records) {
            ids.add((Integer) record.get("id"));
        }
        
        return deleteByIds(ids);
    }
    
    /**
     * Delete records by pattern
     */
    public int deleteByPattern(String pattern, boolean skipValidation) throws SQLException {
        if (skipValidation) {
            return deleteByPatternUnsafe(pattern);
        }
        
        List<Map<String, Object>> records = findByPattern(pattern);
        if (records.isEmpty()) {
            return 0;
        }
        
        DeletionValidationResult validation = validateForDeletion(records);
        
        if (validation.hasBlockedRecords()) {
            throw new SQLException("Cannot delete: " + validation.getBlockedCount() + 
                " record(s) have active IPs");
        }
        
        return deleteSafeRecords(validation.safeToDelete);
    }
    
    private int deleteByPatternUnsafe(String pattern) throws SQLException {
        String sqlPattern = pattern.replace("*", "%");
        String sql = "DELETE FROM dns_records WHERE hostname LIKE ?";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, sqlPattern);
            return stmt.executeUpdate();
        }
    }
    
    /**
     * Delete records by IP (with validation)
     */
    public int deleteByIP(String ip, boolean skipValidation) throws SQLException {
        if (skipValidation) {
            return deleteByIPUnsafe(ip);
        }
        
        List<Map<String, Object>> records = findByIP(ip);
        if (records.isEmpty()) {
            return 0;
        }
        
        DeletionValidationResult validation = validateForDeletion(records);
        
        if (validation.hasBlockedRecords()) {
            throw new SQLException("Cannot delete: IP " + ip + " is currently active");
        }
        
        return deleteSafeRecords(validation.safeToDelete);
    }
    
    private int deleteByIPUnsafe(String ip) throws SQLException {
        String sql = "DELETE FROM dns_records WHERE value = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, ip);
            return stmt.executeUpdate();
        }
    }
    
    /**
     * Delete records by zone (with validation)
     */
    public int deleteByZone(String zoneName, boolean skipValidation) throws SQLException {
        if (skipValidation) {
            return deleteByZoneUnsafe(zoneName);
        }
        
        List<Map<String, Object>> records = findByZone(zoneName);
        if (records.isEmpty()) {
            return 0;
        }
        
        DeletionValidationResult validation = validateForDeletion(records);
        
        if (validation.hasBlockedRecords()) {
            throw new SQLException("Cannot delete zone: " + validation.getBlockedCount() + 
                " record(s) have active IPs");
        }
        
        return deleteSafeRecords(validation.safeToDelete);
    }
    
    private int deleteByZoneUnsafe(String zoneName) throws SQLException {
        String sql = "DELETE FROM dns_records " +
                     "WHERE zone_id = (SELECT id FROM zones WHERE name = ?)";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, zoneName);
            return stmt.executeUpdate();
        }
    }
    
    /**
     * Delete records from CSV (with validation)
     */
    public int deleteFromCSV(String csvFile, boolean skipValidation) throws SQLException {
        List<Map<String, Object>> records = findFromCSV(csvFile);
        if (records.isEmpty()) {
            return 0;
        }
        
        if (!skipValidation) {
            DeletionValidationResult validation = validateForDeletion(records);
            
            if (validation.hasBlockedRecords()) {
                throw new SQLException("Cannot delete: " + validation.getBlockedCount() + 
                    " record(s) have active IPs");
            }
            
            return deleteSafeRecords(validation.safeToDelete);
        }
        
        // Unsafe delete
        return deleteFromCSVUnsafe(csvFile);
    }
    
    private int deleteFromCSVUnsafe(String csvFile) throws SQLException {
        int totalDeleted = 0;
        Connection conn = null;
        
        try {
            conn = getConnection();
            conn.setAutoCommit(false);  // Transaction
            
            PreparedStatement stmt = conn.prepareStatement(
                "DELETE FROM dns_records WHERE hostname = ?"
            );
            
            BufferedReader reader = new BufferedReader(new FileReader(csvFile));
            String line;
            boolean firstLine = true;
            
            while ((line = reader.readLine()) != null) {
                // Skip header
                if (firstLine) {
                    firstLine = false;
                    if (line.toLowerCase().contains("hostname")) {
                        continue;
                    }
                }
                
                line = line.trim();
                if (line.isEmpty()) continue;
                
                String[] parts = line.split(",");
                String hostname = parts[0].trim();
                
                stmt.setString(1, hostname);
                totalDeleted += stmt.executeUpdate();
            }
            
            reader.close();
            conn.commit();
            
        } catch (Exception e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    // Ignore rollback errors
                }
            }
            throw new SQLException("Error deleting from CSV: " + e.getMessage(), e);
            
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
        
        return totalDeleted;
    }
    
    /**
     * Cleanup test data (with validation)
     */
    public int cleanupTestData(String[] patterns, boolean skipValidation) throws SQLException {
        List<Map<String, Object>> records = findTestData(patterns);
        if (records.isEmpty()) {
            return 0;
        }
        
        if (!skipValidation) {
            DeletionValidationResult validation = validateForDeletion(records);
            
            if (validation.hasBlockedRecords()) {
                System.out.println("\n⚠️  WARNING: " + validation.getBlockedCount() + 
                    " test record(s) have active IPs:");
                printBlockedRecords(validation.activeIPs, validation.blockedReasons);
                System.out.println("\nWill only delete " + validation.getSafeCount() + 
                    " safe record(s)");
            }
            
            return deleteSafeRecords(validation.safeToDelete);
        }
        
        // Unsafe cleanup
        int totalDeleted = 0;
        for (String pattern : patterns) {
            totalDeleted += deleteByPatternUnsafe(pattern);
        }
        return totalDeleted;
    }
    
    /**
     * Print blocked records with reasons
     */
    private void printBlockedRecords(List<Map<String, Object>> records, 
                                     Map<String, String> reasons) {
        for (Map<String, Object> record : records) {
            String ip = record.get("value").toString();
            String reason = reasons.getOrDefault(ip, "Unknown");
            System.out.printf("  ❌ %s → %s [%s]%n", 
                record.get("hostname"), ip, reason);
        }
    }
    
    /**
     * Execute query and return results as list of maps
     */
    private List<Map<String, Object>> executeQuery(PreparedStatement stmt) throws SQLException {
        List<Map<String, Object>> results = new ArrayList<>();
        
        try (ResultSet rs = stmt.executeQuery()) {
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = metaData.getColumnName(i);
                    Object value = rs.getObject(i);
                    row.put(columnName, value);
                }
                
                results.add(row);
            }
        }
        
        return results;
    }
    
    /**
     * Batch delete by IDs (for internal use)
     */
    public int deleteByIds(List<Integer> ids) throws SQLException {
        if (ids.isEmpty()) {
            return 0;
        }
        
        // Build SQL with placeholders
        StringBuilder sql = new StringBuilder("DELETE FROM dns_records WHERE id IN (");
        for (int i = 0; i < ids.size(); i++) {
            sql.append("?");
            if (i < ids.size() - 1) {
                sql.append(",");
            }
        }
        sql.append(")");
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            
            for (int i = 0; i < ids.size(); i++) {
                stmt.setInt(i + 1, ids.get(i));
            }
            
            return stmt.executeUpdate();
        }
    }
    
    /**
     * Get deletion statistics
     */
    public Map<String, Integer> getStatistics() throws SQLException {
        Map<String, Integer> stats = new HashMap<>();
        
        String sql = "SELECT " +
                     "COUNT(*) as total_records, " +
                     "COUNT(DISTINCT zone_id) as total_zones, " +
                     "COUNT(CASE WHEN hostname LIKE 'test-%' THEN 1 END) as test_records " +
                     "FROM dns_records";
        
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            if (rs.next()) {
                stats.put("total_records", rs.getInt("total_records"));
                stats.put("total_zones", rs.getInt("total_zones"));
                stats.put("test_records", rs.getInt("test_records"));
            }
        }
        
        return stats;
    }
}
