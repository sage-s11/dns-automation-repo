package com.ipam.utils;

/**
 * IP Address Utilities - Pure static methods for IP manipulation
 * No dependencies, fully testable
 */
public class IPAddressUtil {
    
    /**
     * Convert IP string to long (for range calculations)
     * Example: "10.20.0.1" -> 169410561
     */
    public static long ipToLong(String ipAddress) {
        String[] octets = ipAddress.split("\\.");
        if (octets.length != 4) {
            throw new IllegalArgumentException("Invalid IP address: " + ipAddress);
        }
        
        long result = 0;
        for (int i = 0; i < 4; i++) {
            int octet = Integer.parseInt(octets[i]);
            if (octet < 0 || octet > 255) {
                throw new IllegalArgumentException("Invalid octet: " + octet);
            }
            result = (result << 8) | octet;
        }
        return result;
    }
    
    /**
     * Convert long back to IP string
     * Example: 169410561 -> "10.20.0.1"
     */
    public static String longToIP(long ip) {
        return String.format("%d.%d.%d.%d",
            (ip >> 24) & 0xFF,
            (ip >> 16) & 0xFF,
            (ip >> 8) & 0xFF,
            ip & 0xFF
        );
    }
    
    /**
     * Parse CIDR notation
     * Example: "10.20.0.0/24" -> ["10.20.0.0", "24"]
     */
    public static String[] parseCIDR(String cidr) {
        if (!cidr.contains("/")) {
            throw new IllegalArgumentException("Invalid CIDR format: " + cidr);
        }
        
        String[] parts = cidr.split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid CIDR format: " + cidr);
        }
        
        String ip = parts[0];
        int prefix = Integer.parseInt(parts[1]);
        
        if (prefix < 0 || prefix > 32) {
            throw new IllegalArgumentException("Invalid prefix length: " + prefix);
        }
        
        return parts;
    }
    
    /**
     * Calculate subnet mask from prefix length
     * Example: 24 -> "255.255.255.0"
     */
    public static String prefixToMask(int prefix) {
        if (prefix < 0 || prefix > 32) {
            throw new IllegalArgumentException("Invalid prefix: " + prefix);
        }
        
        long mask = (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
        return longToIP(mask);
    }
    
    /**
     * Calculate subnet mask as long
     * Example: 24 -> 4294967040
     */
    public static long prefixToMaskLong(int prefix) {
        if (prefix < 0 || prefix > 32) {
            throw new IllegalArgumentException("Invalid prefix: " + prefix);
        }
        return (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
    }
    
    /**
     * Validate IP address format
     */
    public static boolean isValidIP(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        
        String[] octets = ip.split("\\.");
        if (octets.length != 4) {
            return false;
        }
        
        try {
            for (String octet : octets) {
                int value = Integer.parseInt(octet);
                if (value < 0 || value > 255) {
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * Check if IP is in subnet
     * Example: isInSubnet("10.20.0.50", "10.20.0.0/24") -> true
     */
    public static boolean isInSubnet(String ip, String cidr) {
        String[] parts = parseCIDR(cidr);
        long ipLong = ipToLong(ip);
        long networkLong = ipToLong(parts[0]);
        int prefix = Integer.parseInt(parts[1]);
        long maskLong = prefixToMaskLong(prefix);
        
        return (ipLong & maskLong) == (networkLong & maskLong);
    }
    
    /**
     * Increment IP address
     * Example: "10.20.0.1" -> "10.20.0.2"
     */
    public static String incrementIP(String ip) {
        long ipLong = ipToLong(ip);
        return longToIP(ipLong + 1);
    }
    
    /**
     * Decrement IP address
     * Example: "10.20.0.2" -> "10.20.0.1"
     */
    public static String decrementIP(String ip) {
        long ipLong = ipToLong(ip);
        return longToIP(ipLong - 1);
    }
}
