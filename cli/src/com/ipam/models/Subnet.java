package com.ipam.models;

import com.ipam.utils.IPAddressUtil;
import com.ipam.utils.SubnetUtil;

/**
 * Immutable Subnet model
 * Represents a CIDR subnet with all calculated properties
 */
public class Subnet {
    private final String cidr;
    private final String networkAddress;
    private final String broadcastAddress;
    private final String firstUsableIP;
    private final String lastUsableIP;
    private final String subnetMask;
    private final int prefixLength;
    private final long totalIPs;
    private final long usableIPs;
    private final long networkLong;
    private final long broadcastLong;
    
    public Subnet(String cidr) {
        this.cidr = cidr;
        
        String[] parts = IPAddressUtil.parseCIDR(cidr);
        this.prefixLength = Integer.parseInt(parts[1]);
        
        this.networkAddress = SubnetUtil.getNetworkAddress(cidr);
        this.broadcastAddress = SubnetUtil.getBroadcastAddress(cidr);
        this.firstUsableIP = SubnetUtil.getFirstUsableIP(cidr);
        this.lastUsableIP = SubnetUtil.getLastUsableIP(cidr);
        this.subnetMask = IPAddressUtil.prefixToMask(prefixLength);
        this.totalIPs = SubnetUtil.getTotalIPs(prefixLength);
        this.usableIPs = SubnetUtil.getUsableIPs(prefixLength);
        this.networkLong = IPAddressUtil.ipToLong(networkAddress);
        this.broadcastLong = IPAddressUtil.ipToLong(broadcastAddress);
    }
    
    public String getCidr() { return cidr; }
    public String getNetworkAddress() { return networkAddress; }
    public String getBroadcastAddress() { return broadcastAddress; }
    public String getFirstUsableIP() { return firstUsableIP; }
    public String getLastUsableIP() { return lastUsableIP; }
    public String getSubnetMask() { return subnetMask; }
    public int getPrefixLength() { return prefixLength; }
    public long getTotalIPs() { return totalIPs; }
    public long getUsableIPs() { return usableIPs; }
    public long getNetworkLong() { return networkLong; }
    public long getBroadcastLong() { return broadcastLong; }
    
    public boolean contains(String ip) {
        long ipLong = IPAddressUtil.ipToLong(ip);
        return ipLong >= networkLong && ipLong <= broadcastLong;
    }
    
    public boolean isUsableIP(String ip) {
        return SubnetUtil.isUsableIP(ip, cidr);
    }
    
    @Override
    public String toString() {
        return String.format("Subnet[%s, usable: %d IPs]", cidr, usableIPs);
    }
}
