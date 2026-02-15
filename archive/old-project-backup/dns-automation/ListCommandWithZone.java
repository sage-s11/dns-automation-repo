package com.dnsmanager.commands;

import com.dnsmanager.services.ZoneFileServiceDynamic;
import com.dnsmanager.services.BindConfigService;
import com.dnsmanager.models.DnsRecord;
import java.util.List;

/**
 * Updated ListCommand with --zone flag support
 * Usage: ./dns list [--zone <zonename>]
 */
public class ListCommandWithZone implements Command {
    private final ZoneFileServiceDynamic zoneService;
    private final BindConfigService bindConfigService;
    private final String defaultZone;
    
    public ListCommandWithZone(ZoneFileServiceDynamic zoneService,
                               BindConfigService bindConfigService,
                               String defaultZone) {
        this.zoneService = zoneService;
        this.bindConfigService = bindConfigService;
        this.defaultZone = defaultZone;
    }
    
    @Override
    public void execute(String[] args) throws Exception {
        String zoneName = defaultZone;
        
        // Parse --zone flag
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--zone") && i + 1 < args.length) {
                zoneName = args[i + 1];
                break;
            }
        }
        
        System.out.println("📋 DNS Records - Zone: " + zoneName);
        System.out.println("─".repeat(60));
        
        List<DnsRecord> records = zoneService.getRecords(zoneName);
        
        if (records.isEmpty()) {
            System.out.println("No A records found in this zone.");
            System.out.println();
            System.out.println("Add a record:");
            System.out.println("  ./dns add www 10.10.10.10 --zone " + zoneName);
        } else {
            System.out.printf("%-20s %-10s %-15s%n", "Hostname", "Type", "Value");
            System.out.println("─".repeat(60));
            
            for (DnsRecord record : records) {
                System.out.printf("%-20s %-10s %-15s%n", 
                    record.getHostname(), 
                    record.getType(), 
                    record.getValue());
            }
            
            System.out.println("─".repeat(60));
            System.out.println("Total records: " + records.size());
        }
        
        // Show available zones
        System.out.println();
        List<String> zones = bindConfigService.listZones();
        if (zones.size() > 1) {
            System.out.println("💡 Other zones available:");
            for (String zone : zones) {
                if (!zone.equals(zoneName)) {
                    System.out.println("   ./dns list --zone " + zone);
                }
            }
        }
    }
    
    @Override
    public String getUsage() {
        return "list [--zone <zonename>] - List all DNS records in a zone";
    }
}
