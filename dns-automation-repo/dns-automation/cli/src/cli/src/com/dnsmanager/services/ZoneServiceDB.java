package com.dnsmanager.services;

import com.dnsmanager.models.Zone;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Database service for managing DNS zones
 * Now uses Zone model from models package
 */
public class ZoneServiceDB extends DatabaseService {
    
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
            
            stmt.setString(1, zone.getNsIp());
            stmt.setLong(2, zone.getSerial());
            stmt.setInt(3, zone.getRefresh());
            stmt.setInt(4, zone.getRetry());
            stmt.setInt(5, zone.getExpire());
            stmt.setInt(6, zone.getMinimumTtl());
            stmt.setInt(7, zone.getId());
            
            stmt.executeUpdate();
        }
    }
    
    /**
     * Increment zone serial
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
     * Delete zone
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
     * Map ResultSet to Zone model
     */
    private Zone mapResultSetToZone(ResultSet rs) throws SQLException {
        Zone zone = new Zone();
        zone.setId(rs.getInt("id"));
        zone.setName(rs.getString("name"));
        zone.setNsIp(rs.getString("ns_ip"));
        zone.setSerial(rs.getLong("serial"));
        zone.setRefresh(rs.getInt("refresh"));
        zone.setRetry(rs.getInt("retry"));
        zone.setExpire(rs.getInt("expire"));
        zone.setMinimumTtl(rs.getInt("minimum_ttl"));
        zone.setCreatedAt(rs.getTimestamp("created_at"));
        zone.setUpdatedAt(rs.getTimestamp("updated_at"));
        return zone;
    }
}
