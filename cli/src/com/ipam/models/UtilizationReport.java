package com.ipam.models;

/**
 * Immutable utilization report for a subnet
 */
public class UtilizationReport {
    private final String subnet;
    private final long totalIPs;
    private final long usableIPs;
    private final long allocatedIPs;
    private final long availableIPs;
    private final double utilizationPercent;
    private final String nextAvailableIP;
    
    public UtilizationReport(String subnet, long totalIPs, long usableIPs, 
                            long allocatedIPs, String nextAvailableIP) {
        this.subnet = subnet;
        this.totalIPs = totalIPs;
        this.usableIPs = usableIPs;
        this.allocatedIPs = allocatedIPs;
        this.availableIPs = usableIPs - allocatedIPs;
        this.utilizationPercent = usableIPs > 0 ? (allocatedIPs * 100.0) / usableIPs : 0.0;
        this.nextAvailableIP = nextAvailableIP;
    }
    
    public String getSubnet() { return subnet; }
    public long getTotalIPs() { return totalIPs; }
    public long getUsableIPs() { return usableIPs; }
    public long getAllocatedIPs() { return allocatedIPs; }
    public long getAvailableIPs() { return availableIPs; }
    public double getUtilizationPercent() { return utilizationPercent; }
    public String getNextAvailableIP() { return nextAvailableIP; }
    
    public String getStatus() {
        if (utilizationPercent >= 95) return "CRITICAL";
        if (utilizationPercent >= 80) return "HIGH";
        if (utilizationPercent >= 50) return "MODERATE";
        return "LOW";
    }
    
    @Override
    public String toString() {
        return String.format(
            "Subnet: %s\n" +
            "Total IPs: %d\n" +
            "Usable IPs: %d\n" +
            "Allocated: %d (%.1f%%)\n" +
            "Available: %d\n" +
            "Status: %s\n" +
            "Next IP: %s",
            subnet, totalIPs, usableIPs, allocatedIPs, utilizationPercent,
            availableIPs, getStatus(), nextAvailableIP != null ? nextAvailableIP : "None"
        );
    }
}
