package com.dnsmanager.zonecommands;

import com.dnsmanager.services.ZoneManagerService;

/**
 * Command to delete a DNS zone
 * Usage: ./dns zone delete example.com [--force]
 */
public class ZoneDeleteCommand {
    private final ZoneManagerService zoneManager;
    
    public ZoneDeleteCommand(ZoneManagerService zoneManager) {
        this.zoneManager = zoneManager;
    }
    
    public void execute(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: ./dns zone delete <domain> [--force]");
            System.err.println("Example: ./dns zone delete example.com");
            System.exit(1);
        }
        
        String zoneName = args[0];
        boolean force = false;
        
        // Check for --force flag
        for (int i = 1; i < args.length; i++) {
            if (args[i].equals("--force") || args[i].equals("-f")) {
                force = true;
                break;
            }
        }
        
        System.out.println("🗑️  Deleting zone: " + zoneName);
        System.out.println();
        
        try {
            zoneManager.deleteZone(zoneName, force);
            System.out.println();
            System.out.println("✓ Zone deleted successfully!");
            
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            throw e;
        }
    }
    
    public String getUsage() {
        return "zone delete <domain> [--force] - Delete a DNS zone";
    }
}
