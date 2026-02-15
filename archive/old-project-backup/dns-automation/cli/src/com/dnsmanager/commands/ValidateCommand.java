package com.dnsmanager.commands;

import com.dnsmanager.services.*;

public class ValidateCommand implements Command {
    private final ZoneFileService zoneService;
    private final DnsServerService serverService;
    
    public ValidateCommand(ZoneFileService zoneService, DnsServerService serverService) {
        this.zoneService = zoneService;
        this.serverService = serverService;
    }
    
    @Override
    public void execute(String[] args) throws Exception {
        System.out.println("Validating zone file: " + zoneService.getZoneFile());
        
        if (serverService.validateZone(zoneService.getZoneFile())) {
            System.out.println("\n✓ Zone file is valid");
        } else {
            System.err.println("\n✗ Zone file validation failed");
        }
    }
    
    @Override
    public String getUsage() {
        return "validate - Validate zone file syntax";
    }
}
