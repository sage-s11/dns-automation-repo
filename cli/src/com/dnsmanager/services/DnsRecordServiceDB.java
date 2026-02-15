package com.dnsmanager.services;

import com.dnsmanager.models.DnsRecord;
import com.dnsmanager.models.DnsRecord.RecordType;
import com.dnsmanager.models.Zone;
import com.dnsmanager.utils.ReverseZoneHelper;
import java.sql.*;

public class DnsRecordServiceDB extends DatabaseService {
    
    private ZoneServiceDB zoneService;
    
    public DnsRecordServiceDB() {
        this.zoneService = new ZoneServiceDB();
    }
    
    public DnsRecord addARecord(String zoneName, String hostname, String ip, int ttl, boolean createPTR) throws SQLException {
        
        // Get zone
        Zone zone = zoneService.getZone(zoneName);
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
                
                // Create PTR record if requested
                if (createPTR) {
                    try {
                        createPtrRecord(ip, hostname, zoneName);
                        System.out.println("   ✅ PTR record created for " + ip);
                    } catch (Exception e) {
                        System.out.println("   ⚠️  PTR creation failed: " + e.getMessage());
                    }
                }
                
                return record;
            }
            
            throw new SQLException("Failed to create record");
        }
    }
    
    private void createPtrRecord(String ip, String hostname, String zoneName) throws SQLException {
        // Get reverse zone name (e.g., 10.10.10.in-addr.arpa)
        String reverseZoneName = ReverseZoneHelper.getReverseZoneName(ip);
        
        // Get reverse zone
        Zone reverseZone = zoneService.getZone(reverseZoneName);
        if (reverseZone == null) {
            throw new SQLException("Reverse zone not found: " + reverseZoneName + ". Create it first.");
        }
        
        // Get PTR host (last octet)
        String ptrHost = ReverseZoneHelper.getPtrHost(ip);
        
        // Create FQDN
        String fqdn = ReverseZoneHelper.createFqdn(hostname, zoneName);
        
        // Insert PTR record
        String sql = "INSERT INTO dns_records (zone_id, hostname, type, value, ttl) VALUES (?, ?, 'PTR', ?, 86400) ON CONFLICT DO NOTHING";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, reverseZone.getId());
            stmt.setString(2, ptrHost);
            stmt.setString(3, fqdn);
            
            stmt.executeUpdate();
        }
    }
}
