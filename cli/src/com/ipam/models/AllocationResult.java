package com.ipam.models;

/**
 * Immutable result of IP allocation operation
 */
public class AllocationResult {
    private final boolean success;
    private final String ipAddress;
    private final String message;
    private final String subnet;
    
    private AllocationResult(boolean success, String ipAddress, String message, String subnet) {
        this.success = success;
        this.ipAddress = ipAddress;
        this.message = message;
        this.subnet = subnet;
    }
    
    public static AllocationResult success(String ip, String subnet) {
        return new AllocationResult(true, ip, "IP allocated successfully", subnet);
    }
    
    public static AllocationResult failure(String message, String subnet) {
        return new AllocationResult(false, null, message, subnet);
    }
    
    public boolean isSuccess() { return success; }
    public String getIpAddress() { return ipAddress; }
    public String getMessage() { return message; }
    public String getSubnet() { return subnet; }
    
    @Override
    public String toString() {
        if (success) {
            return String.format("✅ %s (subnet: %s)", ipAddress, subnet);
        } else {
            return String.format("❌ %s (subnet: %s)", message, subnet);
        }
    }
}
