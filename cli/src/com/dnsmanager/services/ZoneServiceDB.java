package com.dnsmanager.services;

import com.dnsmanager.models.Zone;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ZoneServiceDB extends DatabaseService {
    
    public Zone createZone(String name, String nsIp) throws SQLException {
        long serial = Long.parseLong(
            new java.text.SimpleDateFormat("yyyyMMdd").format(new java.util.Date()) + "01"
        );
        
        String sql = "INSERT INTO zones (name, ns_ip, serial, refresh, retry, expire, minimum_ttl) " +
                     "VALUES (?, ?::inet, ?, 3600, 1800, 604800, 86400) " +
                     "RETURNING id, name, ns_ip, serial, refresh, retry, expire, minimum_ttl, created_at, updated_at";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, name);
            stmt.setString(2, nsIp);
            stmt.setLong(3, serial);
            
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
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
            
            throw new SQLException("Failed to create zone");
        }
    }
    
    public Zone getZone(String name) throws SQLException {
        String sql = "SELECT * FROM zones WHERE name = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, name);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
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
            
            return null;
        }
    }
}
