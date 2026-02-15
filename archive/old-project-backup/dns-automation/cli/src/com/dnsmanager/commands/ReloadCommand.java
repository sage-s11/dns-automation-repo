package com.dnsmanager.commands;

import com.dnsmanager.services.*;

public class ReloadCommand implements Command {
    private final ZoneFileService zoneService;
    private final DnsServerService serverService;
    private final String zoneName;
    
    public ReloadCommand(ZoneFileService zoneService, DnsServerService serverService, String zoneName) {
        this.zoneService = zoneService;
        this.serverService = serverService;
        this.zoneName = zoneName;
    }
    
    @Override
    public void execute(String[] args) throws Exception {
        System.out.println("Reloading zone: " + zoneName);
        
        if (serverService.reloadZone()) {
            System.out.println("✓ Zone reloaded successfully!");
        } else {
            throw new RuntimeException("Zone reload failed");
        }
    }
    
    @Override
    public String getUsage() {
        return "reload - Reload the DNS zone";
    }
}
