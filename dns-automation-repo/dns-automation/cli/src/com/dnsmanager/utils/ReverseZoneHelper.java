package com.dnsmanager.utils;

/**
 * Helper utilities for reverse DNS operations
 * Handles IP address to PTR conversion and reverse zone name generation
 */
public class ReverseZoneHelper {
    
    /**
     * Convert IP address to PTR record name
     * Example: 10.10.10.110 → 110.10.10.10.in-addr.arpa
     */
    public static String ipToPtrRecord(String ipAddress) {
        String[] parts = ipAddress.split("\\.");
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid IP address: " + ipAddress);
        }
        
        return parts[3] + "." + parts[2] + "." + parts[1] + "." + parts[0] + ".in-addr.arpa";
    }
    
    /**
     * Get reverse zone name from IP address
     * Example: 10.10.10.110 → 10.10.10.in-addr.arpa
     */
    public static String getReverseZoneName(String ipAddress) {
        String[] parts = ipAddress.split("\\.");
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid IP address: " + ipAddress);
        }
        
        return parts[2] + "." + parts[1] + "." + parts[0] + ".in-addr.arpa";
    }
    
    /**
     * Get PTR record host part (just the last octet)
     * Example: 10.10.10.110 → 110
     */
    public static String getPtrHost(String ipAddress) {
        String[] parts = ipAddress.split("\\.");
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid IP address: " + ipAddress);
        }
        
        return parts[3];
    }
    
    /**
     * Create FQDN from hostname and zone
     * Example: mail, example.com → mail.example.com.
     */
    public static String createFqdn(String hostname, String zoneName) {
        // Handle @ symbol (zone apex)
        if (hostname.equals("@")) {
            return zoneName + ".";
        }
        
        // Add trailing dot for FQDN
        return hostname + "." + zoneName + ".";
    }
    
    /**
     * Check if an IP address belongs to a reverse zone
     * Example: 10.10.10.110 belongs to 10.10.10.in-addr.arpa
     */
    public static boolean ipBelongsToReverseZone(String ipAddress, String reverseZoneName) {
        String calculatedZone = getReverseZoneName(ipAddress);
        return calculatedZone.equals(reverseZoneName);
    }
    
    /**
     * Validate reverse zone name format
     */
    public static boolean isValidReverseZoneName(String zoneName) {
        return zoneName != null && zoneName.endsWith(".in-addr.arpa");
    }
}
