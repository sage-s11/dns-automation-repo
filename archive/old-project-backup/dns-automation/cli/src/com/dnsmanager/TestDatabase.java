package com.dnsmanager;

import com.dnsmanager.config.DatabaseConfig;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class TestDatabase {
    public static void main(String[] args) {
        System.out.println("🔍 Testing database connection...");
        
        // Initialize connection pool
        DatabaseConfig.initialize();
        
        // Test connection
        if (DatabaseConfig.testConnection()) {
            System.out.println("✅ Connection test passed!");
        } else {
            System.out.println("❌ Connection test failed!");
            System.exit(1);
        }
        
        // Query database
        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT name, COUNT(*) as record_count FROM zones z LEFT JOIN dns_records dr ON z.id = dr.zone_id GROUP BY z.name")) {
            
            System.out.println("\n📊 Zones in database:");
            System.out.println("─".repeat(50));
            
            while (rs.next()) {
                String zoneName = rs.getString("name");
                int recordCount = rs.getInt("record_count");
                System.out.printf("%-30s %d records%n", zoneName, recordCount);
            }
            
            System.out.println("─".repeat(50));
            System.out.println("✅ Database query successful!");
            
        } catch (Exception e) {
            System.err.println("❌ Database query failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            DatabaseConfig.close();
        }
    }
}
