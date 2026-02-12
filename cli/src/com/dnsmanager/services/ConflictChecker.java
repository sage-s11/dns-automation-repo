package com.dnsmanager.services;

import java.sql.*;

/**
 * Check for IP conflicts in database
 */
public class ConflictChecker extends DatabaseService {
    
    /**
     * Check if IP already exists in DNS records
     */
    public String checkIPConflict(String ip) {
        String sql = "SELECT hostname FROM dns_records WHERE value = ? AND type = 'A' LIMIT 1";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, ip);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return rs.getString("hostname");  // Return conflicting hostname
            }
            
        } catch (SQLException e) {
            // Database error - return null (skip check)
        }
        
        return null;  // No conflict
    }
    
    /**
     * Check if hostname already exists in zone
     */
    public boolean hostnameExists(String hostname, int zoneId) {
        String sql = "SELECT COUNT(*) FROM dns_records WHERE hostname = ? AND zone_id = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, hostname);
            stmt.setInt(2, zoneId);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
            
        } catch (SQLException e) {
            // Error - assume doesn't exist
        }
        
        return false;
    }
}
