package com.dnsmanager.zonecommands;

import com.dnsmanager.services.ZoneManagerService;

/**
 * Command to show DNS zone details
 * Usage: ./dns zone show example.com
 */
public class ZoneShowCommand {
    private final ZoneManagerService zoneManager;
    
    public ZoneShowCommand(ZoneManagerService zoneManager) {
        this.zoneManager = zoneManager;
    }
    
    public void execute(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: ./dns zone show <domain>");
            System.err.println("Example: ./dns zone show example.com");
            System.exit(1);
        }
        
        String zoneName = args[0];
        
        try {
            zoneManager.showZone(zoneName);
            
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            throw e;
        }
    }
    
    public String getUsage() {
        return "zone show <domain> - Show zone details and statistics";
    }
}
