package com.dnsmanager.services;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Database service for managing DNS zones
 */
public class ZoneServiceDB extends DatabaseService {
    
    /**
     * Zone data class
     */
    public static class Zone {
        public int id;
        public String name;
        public String nsIp;
        public long serial;
        public int refresh;
        public int retry;
        public int expire;
        public int minimumTtl;
        public Timestamp createdAt;
        public Timestamp updatedAt;
        
        @Override
        public String toString() {
            return String.format("Zone{name='%s', serial=%d, nsIp='%s'}", 
                name, serial, nsIp);
        }
    }
    
    /**
     * Create a new zone
     */
    public Zone createZone(String name, String nsIp) throws SQLException {
        // Generate initial serial (YYYYMMDD01)
        long serial = Long.parseLong(
            new java.text.SimpleDateFormat("yyyyMMdd").format(new java.util.Date()) + "01"
        );
        
        String sql = """
            INSERT INTO zones (name, ns_ip, serial, refresh, retry, expire, minimum_ttl)
            VALUES (?, ?::inet, ?, 3600, 1800, 604800, 86400)
            RETURNING id, name, ns_ip, serial, refresh, retry, expire, minimum_ttl, created_at, updated_at
            """;
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, name);
            stmt.setString(2, nsIp);
            stmt.setLong(3, serial);
            
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return mapResultSetToZone(rs);
            }
            
            throw new SQLException("Failed to create zone");
        }
    }
    
    /**
     * Get zone by name
     */
    public Zone getZone(String name) throws SQLException {
        String sql = "SELECT * FROM zones WHERE name = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, name);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return mapResultSetToZone(rs);
            }
            
            return null;
        }
    }
    
    /**
     * Get zone by ID
     */
    public Zone getZoneById(int id) throws SQLException {
        String sql = "SELECT * FROM zones WHERE id = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return mapResultSetToZone(rs);
            }
            
            return null;
        }
    }
    
    /**
     * List all zones
     */
    public List<Zone> listZones() throws SQLException {
        List<Zone> zones = new ArrayList<>();
        String sql = "SELECT * FROM zones ORDER BY name";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                zones.add(mapResultSetToZone(rs));
            }
        }
        
        return zones;
    }
    
    /**
     * Update zone
     */
    public void updateZone(Zone zone) throws SQLException {
        String sql = """
            UPDATE zones 
            SET ns_ip = ?::inet, serial = ?, refresh = ?, retry = ?, expire = ?, minimum_ttl = ?
            WHERE id = ?
            """;
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, zone.nsIp);
            stmt.setLong(2, zone.serial);
            stmt.setInt(3, zone.refresh);
            stmt.setInt(4, zone.retry);
            stmt.setInt(5, zone.expire);
            stmt.setInt(6, zone.minimumTtl);
            stmt.setInt(7, zone.id);
            
            stmt.executeUpdate();
        }
    }
    
    /**
     * Increment zone serial (auto-update)
     */
    public long incrementSerial(String zoneName) throws SQLException {
        String sql = """
            UPDATE zones 
            SET serial = serial + 1
            WHERE name = ?
            RETURNING serial
            """;
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, zoneName);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return rs.getLong("serial");
            }
            
            throw new SQLException("Zone not found: " + zoneName);
        }
    }
    
    /**
     * Delete zone (and all its records - cascade)
     */
    public void deleteZone(String name) throws SQLException {
        String sql = "DELETE FROM zones WHERE name = ?";
        
        int rowsAffected = executeUpdate(sql, name);
        
        if (rowsAffected == 0) {
            throw new SQLException("Zone not found: " + name);
        }
    }
    
    /**
     * Check if zone exists
     */
    public boolean zoneExists(String name) throws SQLException {
        String sql = "SELECT COUNT(*) FROM zones WHERE name = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, name);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
            
            return false;
        }
    }
    
    /**
     * Get zone statistics
     */
    public ZoneStats getZoneStats(String zoneName) throws SQLException {
        String sql = """
            SELECT 
                z.name,
                COUNT(dr.id) as record_count,
                COUNT(dr.id) FILTER (WHERE dr.type = 'A') as a_records,
                COUNT(dr.id) FILTER (WHERE dr.type = 'PTR') as ptr_records,
                COUNT(dr.id) FILTER (WHERE dr.type = 'CNAME') as cname_records
            FROM zones z
            LEFT JOIN dns_records dr ON z.id = dr.zone_id
            WHERE z.name = ?
            GROUP BY z.name
            """;
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, zoneName);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                ZoneStats stats = new ZoneStats();
                stats.zoneName = rs.getString("name");
                stats.totalRecords = rs.getInt("record_count");
                stats.aRecords = rs.getInt("a_records");
                stats.ptrRecords = rs.getInt("ptr_records");
                stats.cnameRecords = rs.getInt("cname_records");
                return stats;
            }
            
            return null;
        }
    }
    
    /**
     * Map ResultSet to Zone object
     */
    private Zone mapResultSetToZone(ResultSet rs) throws SQLException {
        Zone zone = new Zone();
        zone.id = rs.getInt("id");
        zone.name = rs.getString("name");
        zone.nsIp = rs.getString("ns_ip");
        zone.serial = rs.getLong("serial");
        zone.refresh = rs.getInt("refresh");
        zone.retry = rs.getInt("retry");
        zone.expire = rs.getInt("expire");
        zone.minimumTtl = rs.getInt("minimum_ttl");
        zone.createdAt = rs.getTimestamp("created_at");
        zone.updatedAt = rs.getTimestamp("updated_at");
        return zone;
    }
    
    /**
     * Zone statistics class
     */
    public static class ZoneStats {
        public String zoneName;
        public int totalRecords;
        public int aRecords;
        public int ptrRecords;
        public int cnameRecords;
        
        @Override
        public String toString() {
            return String.format("Zone Stats: %s - Total: %d (A: %d, PTR: %d, CNAME: %d)",
                zoneName, totalRecords, aRecords, ptrRecords, cnameRecords);
        }
    }
}
