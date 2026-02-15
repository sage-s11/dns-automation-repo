package com.ipam.utils;

/**
 * Subnet Calculation Utilities
 * Pure math, no state, fully testable
 */
public class SubnetUtil {
    
    /**
     * Calculate network address from CIDR
     * Example: "10.20.0.50/24" -> "10.20.0.0"
     */
    public static String getNetworkAddress(String cidr) {
        String[] parts = IPAddressUtil.parseCIDR(cidr);
        long ip = IPAddressUtil.ipToLong(parts[0]);
        int prefix = Integer.parseInt(parts[1]);
        long mask = IPAddressUtil.prefixToMaskLong(prefix);
        
        long network = ip & mask;
        return IPAddressUtil.longToIP(network);
    }
    
    /**
     * Calculate broadcast address
     * Example: "10.20.0.0/24" -> "10.20.0.255"
     */
    public static String getBroadcastAddress(String cidr) {
        String[] parts = IPAddressUtil.parseCIDR(cidr);
        long network = IPAddressUtil.ipToLong(getNetworkAddress(cidr));
        int prefix = Integer.parseInt(parts[1]);
        long mask = IPAddressUtil.prefixToMaskLong(prefix);
        
        long broadcast = network | (~mask & 0xFFFFFFFFL);
        return IPAddressUtil.longToIP(broadcast);
    }
    
    /**
     * Get first usable IP (network + 1)
     * Example: "10.20.0.0/24" -> "10.20.0.1"
     */
    public static String getFirstUsableIP(String cidr) {
        String network = getNetworkAddress(cidr);
        return IPAddressUtil.incrementIP(network);
    }
    
    /**
     * Get last usable IP (broadcast - 1)
     * Example: "10.20.0.0/24" -> "10.20.0.254"
     */
    public static String getLastUsableIP(String cidr) {
        String broadcast = getBroadcastAddress(cidr);
        return IPAddressUtil.decrementIP(broadcast);
    }
    
    /**
     * Calculate total number of IPs in subnet
     * Example: /24 -> 256
     */
    public static long getTotalIPs(int prefix) {
        if (prefix < 0 || prefix > 32) {
            throw new IllegalArgumentException("Invalid prefix: " + prefix);
        }
        return 1L << (32 - prefix);
    }
    
    /**
     * Calculate usable IPs (total - 2 for network/broadcast)
     * Example: /24 -> 254
     */
    public static long getUsableIPs(int prefix) {
        if (prefix == 32) return 1;  // Single host
        if (prefix == 31) return 2;  // Point-to-point
        return getTotalIPs(prefix) - 2;
    }
    
    /**
     * Check if IP is network address
     */
    public static boolean isNetworkAddress(String ip, String cidr) {
        return ip.equals(getNetworkAddress(cidr));
    }
    
    /**
     * Check if IP is broadcast address
     */
    public static boolean isBroadcastAddress(String ip, String cidr) {
        return ip.equals(getBroadcastAddress(cidr));
    }
    
    /**
     * Check if IP is usable (not network or broadcast)
     */
    public static boolean isUsableIP(String ip, String cidr) {
        return !isNetworkAddress(ip, cidr) && !isBroadcastAddress(ip, cidr);
    }
    
    /**
     * Get subnet size category
     */
    public static String getSubnetSizeCategory(int prefix) {
        if (prefix >= 30) return "Tiny (1-4 hosts)";
        if (prefix >= 24) return "Small (up to 254 hosts)";
        if (prefix >= 20) return "Medium (up to 4094 hosts)";
        if (prefix >= 16) return "Large (up to 65534 hosts)";
        return "Very Large (65535+ hosts)";
    }
}
