package com.ipam;

import com.ipam.models.*;
import com.ipam.utils.IPAddressUtil;
import com.dnsmanager.services.DatabaseService;

import java.sql.*;
import java.util.*;

/**
 * IPAM Service - IP Address Management
 * Coordinates subnet calculations with database queries
 */
public class IPAMService extends DatabaseService {
    
    /**
     * Calculate subnet information (no DB required)
     */
    public Subnet calculateSubnet(String cidr) {
        return new Subnet(cidr);
    }
    
    /**
     * Get next available IP in subnet
     */
    public AllocationResult getNextAvailableIP(String cidr) throws SQLException {
        Subnet subnet = new Subnet(cidr);
        Set<String> usedIPs = getUsedIPsInSubnet(cidr);
        
        String nextIP = IPAllocator.findNextAvailable(subnet, usedIPs);
        
        if (nextIP == null) {
            return AllocationResult.failure("Subnet exhausted - no IPs available", cidr);
        }
        
        return AllocationResult.success(nextIP, cidr);
    }
    
    /**
     * Get multiple available IPs
     */
    public List<String> getAvailableIPs(String cidr, int count) throws SQLException {
        Subnet subnet = new Subnet(cidr);
        Set<String> usedIPs = getUsedIPsInSubnet(cidr);
        return IPAllocator.findAvailable(subnet, usedIPs, count);
    }
    
    /**
     * Get utilization report for subnet
     */
    public UtilizationReport getUtilization(String cidr) throws SQLException {
        Subnet subnet = new Subnet(cidr);
        Set<String> usedIPs = getUsedIPsInSubnet(cidr);
        String nextIP = IPAllocator.findNextAvailable(subnet, usedIPs);
        
        return new UtilizationReport(
            cidr,
            subnet.getTotalIPs(),
            subnet.getUsableIPs(),
            usedIPs.size(),
            nextIP
        );
    }
    
    /**
     * Find gaps in allocation
     */
    public List<String[]> findGaps(String cidr) throws SQLException {
        Subnet subnet = new Subnet(cidr);
        Set<String> usedIPs = getUsedIPsInSubnet(cidr);
        return IPAllocator.findGaps(subnet, usedIPs);
    }
    
    /**
     * Query database for IPs used in this subnet
     */
    private Set<String> getUsedIPsInSubnet(String cidr) throws SQLException {
        Set<String> usedIPs = new HashSet<>();
        Subnet subnet = new Subnet(cidr);
        
        String sql = "SELECT DISTINCT value FROM dns_records WHERE type = 'A'";
        
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                String ip = rs.getString("value");
                
                // Remove CIDR notation if present
                if (ip.contains("/")) {
                    ip = ip.split("/")[0];
                }
                
                // Check if IP is in this subnet
                if (IPAddressUtil.isValidIP(ip) && subnet.contains(ip)) {
                    usedIPs.add(ip);
                }
            }
        }
        
        return usedIPs;
    }
    
    /**
     * Get all subnets in use
     */
    public List<String> getAllSubnets() throws SQLException {
        Set<String> subnets = new TreeSet<>();
        
        String sql = "SELECT DISTINCT value FROM dns_records WHERE type = 'A'";
        
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                String ip = rs.getString("value");
                
                if (ip.contains("/")) {
                    ip = ip.split("/")[0];
                }
                
                if (IPAddressUtil.isValidIP(ip)) {
                    // Derive /24 subnet
                    String[] octets = ip.split("\\.");
                    String subnet = octets[0] + "." + octets[1] + "." + octets[2] + ".0/24";
                    subnets.add(subnet);
                }
            }
        }
        
        return new ArrayList<>(subnets);
    }
}
