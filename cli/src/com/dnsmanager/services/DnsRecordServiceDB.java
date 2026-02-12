package com.dnsmanager.services;

import com.dnsmanager.models.DnsRecord;
import com.dnsmanager.models.DnsRecord.RecordType;
import java.sql.*;

public class DnsRecordServiceDB extends DatabaseService {
    
    private ZoneServiceDB zoneService;
    
    public DnsRecordServiceDB() {
        this.zoneService = new ZoneServiceDB();
    }
    
    public DnsRecord addARecord(String zoneName, String hostname, String ip, int ttl, boolean createPTR) throws SQLException {
        
        // Get zone
        com.dnsmanager.models.Zone zone = zoneService.getZone(zoneName);
        if (zone == null) {
            throw new SQLException("Zone not found: " + zoneName);
        }
        
        // Insert A record
        String sql = "INSERT INTO dns_records (zone_id, hostname, type, value, ttl) VALUES (?, ?, 'A', ?::inet, ?) RETURNING id";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, zone.getId());
            stmt.setString(2, hostname);
            stmt.setString(3, ip);
            stmt.setInt(4, ttl);
            
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                DnsRecord record = new DnsRecord();
                record.setId(rs.getInt("id"));
                record.setZoneId(zone.getId());
                record.setHostname(hostname);
                record.setType(RecordType.A);
                record.setValue(ip);
                record.setTtl(ttl);
                
                return record;
            }
            
            throw new SQLException("Failed to create record");
        }
    }
}
