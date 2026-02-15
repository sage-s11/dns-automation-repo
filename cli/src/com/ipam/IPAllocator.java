package com.ipam;

import com.ipam.models.Subnet;
import com.ipam.utils.IPAddressUtil;

import java.util.*;

/**
 * IP Allocation Algorithm
 * Finds gaps in IP allocation within a subnet
 */
public class IPAllocator {
    
    /**
     * Find next available IP in subnet
     * Algorithm: Linear scan through subnet range, skip used IPs
     */
    public static String findNextAvailable(Subnet subnet, Set<String> usedIPs) {
        long current = IPAddressUtil.ipToLong(subnet.getFirstUsableIP());
        long last = IPAddressUtil.ipToLong(subnet.getLastUsableIP());
        
        while (current <= last) {
            String candidate = IPAddressUtil.longToIP(current);
            if (!usedIPs.contains(candidate)) {
                return candidate;
            }
            current++;
        }
        
        return null; // Subnet exhausted
    }
    
    /**
     * Find multiple available IPs
     */
    public static List<String> findAvailable(Subnet subnet, Set<String> usedIPs, int count) {
        List<String> available = new ArrayList<>();
        long current = IPAddressUtil.ipToLong(subnet.getFirstUsableIP());
        long last = IPAddressUtil.ipToLong(subnet.getLastUsableIP());
        
        while (current <= last && available.size() < count) {
            String candidate = IPAddressUtil.longToIP(current);
            if (!usedIPs.contains(candidate)) {
                available.add(candidate);
            }
            current++;
        }
        
        return available;
    }
    
    /**
     * Find all gaps in allocation
     * Returns list of [start, end] pairs
     */
    public static List<String[]> findGaps(Subnet subnet, Set<String> usedIPs) {
        List<String[]> gaps = new ArrayList<>();
        long current = IPAddressUtil.ipToLong(subnet.getFirstUsableIP());
        long last = IPAddressUtil.ipToLong(subnet.getLastUsableIP());
        
        String gapStart = null;
        
        while (current <= last) {
            String ip = IPAddressUtil.longToIP(current);
            
            if (!usedIPs.contains(ip)) {
                if (gapStart == null) {
                    gapStart = ip;
                }
            } else {
                if (gapStart != null) {
                    gaps.add(new String[]{gapStart, IPAddressUtil.longToIP(current - 1)});
                    gapStart = null;
                }
            }
            current++;
        }
        
        // Close final gap
        if (gapStart != null) {
            gaps.add(new String[]{gapStart, IPAddressUtil.longToIP(last)});
        }
        
        return gaps;
    }
    
    /**
     * Calculate utilization percentage
     */
    public static double calculateUtilization(Subnet subnet, Set<String> usedIPs) {
        long usable = subnet.getUsableIPs();
        if (usable == 0) return 0.0;
        
        long used = usedIPs.size();
        return (used * 100.0) / usable;
    }
    
    /**
     * Check if subnet is exhausted
     */
    public static boolean isExhausted(Subnet subnet, Set<String> usedIPs) {
        return usedIPs.size() >= subnet.getUsableIPs();
    }
}
