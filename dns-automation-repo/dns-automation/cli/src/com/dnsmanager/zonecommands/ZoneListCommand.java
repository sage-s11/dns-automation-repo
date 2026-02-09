package com.dnsmanager.zonecommands;

import com.dnsmanager.services.ZoneManagerService;
import java.util.List;

/**
 * Command to list all DNS zones
 * Usage: ./dns zone list
 */
public class ZoneListCommand {
    private final ZoneManagerService zoneManager;
    
    public ZoneListCommand(ZoneManagerService zoneManager) {
        this.zoneManager = zoneManager;
    }
    
    public void execute(String[] args) throws Exception {
        System.out.println("📋 DNS Zones:");
        System.out.println();
        
        try {
            List<String> zones = zoneManager.listZones();
            
            if (zones.isEmpty()) {
                System.out.println("  No zones configured.");
                System.out.println();
                System.out.println("Create a zone: ./dns zone add example.com 192.168.1.17");
            } else {
                for (int i = 0; i < zones.size(); i++) {
                    System.out.printf("  %d. %s%n", i + 1, zones.get(i));
                }
                System.out.println();
                System.out.println("Total zones: " + zones.size());
            }
            
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            throw e;
        }
    }
    
    public String getUsage() {
        return "zone list - List all configured DNS zones";
    }
}
