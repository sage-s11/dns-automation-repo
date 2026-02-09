package com.dnsmanager.zonecommands;

import com.dnsmanager.services.ZoneManagerService;

/**
 * Command to add a new DNS zone
 * Usage: ./dns zone add example.com 192.168.1.17
 */
public class ZoneAddCommand {
    private final ZoneManagerService zoneManager;
    
    public ZoneAddCommand(ZoneManagerService zoneManager) {
        this.zoneManager = zoneManager;
    }
    
    public void execute(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: ./dns zone add <domain> <nameserver-ip>");
            System.err.println("Example: ./dns zone add example.com 192.168.1.17");
            System.exit(1);
        }
        
        String zoneName = args[0];
        String nsIpAddress = args[1];
        
        System.out.println("➕ Creating zone: " + zoneName);
        System.out.println("   Nameserver IP: " + nsIpAddress);
        System.out.println();
        
        try {
            zoneManager.createZone(zoneName, nsIpAddress);
            
            System.out.println();
            System.out.println("🎉 Zone created successfully!");
            System.out.println();
            System.out.println("Next steps:");
            System.out.println("  1. Add records: ./dns add www 10.10.10.10 --zone " + zoneName);
            System.out.println("  2. Test DNS: dig @127.0.0.1 -p 1053 " + zoneName + " +short");
            
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            throw e;
        }
    }
    
    public String getUsage() {
        return "zone add <domain> <nameserver-ip> - Create a new DNS zone";
    }
}
