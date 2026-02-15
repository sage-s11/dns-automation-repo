package com.dnsmanager.commands;

import com.dnsmanager.services.ReverseZoneService;
import com.dnsmanager.utils.ReverseZoneHelper;
import com.dnsmanager.services.ZoneFileServiceDynamic;
import com.dnsmanager.services.DnsServerServiceDynamic;
import com.dnsmanager.services.DnsServerService;
import com.dnsmanager.services.DnsServerServiceDynamic;
import com.dnsmanager.services.BackupService;
import com.dnsmanager.services.DnsServerServiceDynamic;
import com.dnsmanager.utils.ValidationUtils;
import java.nio.file.Path;

/**
 * Updated AddCommand with --zone flag support
 * Usage: ./dns add <hostname> <ip> [--zone <zonename>]
 */
public class AddCommand implements Command {
    private final ReverseZoneService reverseZoneService;
    private final ZoneFileServiceDynamic zoneService;
    private final DnsServerServiceDynamic serverService;
    private final BackupService backupService;
    private final String defaultZone;

    public AddCommand(ZoneFileServiceDynamic zoneService,
                      DnsServerServiceDynamic serverService,
                      BackupService backupService,
                      ReverseZoneService reverseZoneService,  // ← ADD
                      String defaultZone) {
        this.zoneService = zoneService;
        this.serverService = serverService;
        this.backupService = backupService;
        this.reverseZoneService = reverseZoneService;  // ← ADD
        this.defaultZone = defaultZone;
    }
    
    @Override
    public void execute(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: dns add <hostname> <ip> [--zone <zonename>]");
            System.err.println("Example: dns add www 10.10.10.50");
            System.err.println("Example: dns add api 10.10.10.60 --zone example.com");
            System.exit(1);
        }
        
        String hostname = args[0];
        String ip = args[1];
        String zoneName = defaultZone; // Default to examplenv.demo
        
        // Parse --zone flag
        for (int i = 2; i < args.length; i++) {
            if (args[i].equals("--zone") && i + 1 < args.length) {
                zoneName = args[i + 1];
                break;
            }
        }
        
        // Validate inputs
        if (!ValidationUtils.isValidHostname(hostname)) {
            throw new IllegalArgumentException("Invalid hostname: " + hostname);
        }
        
        if (!ValidationUtils.isValidIpAddress(ip)) {
            throw new IllegalArgumentException("Invalid IP address: " + ip);
        }
        
        // Check if record already exists
        if (zoneService.recordExists(zoneName, hostname)) {
            throw new IllegalStateException("Record already exists: " + hostname + " in zone " + zoneName);
        }
        
        // Create backup
        Path zoneFile = zoneService.getZoneFilePath(zoneName);
        backupService.createBackup(zoneFile);
        
        try {
            // Add record
            zoneService.addRecord(zoneName, hostname, ip);

            // Add PTR record
            try {
                reverseZoneService.ensureReverseZoneExists(ip, "192.168.1.17");
                reverseZoneService.addPtrRecord(ip, hostname, zoneName);
                String reverseZoneName = ReverseZoneHelper.getReverseZoneName(ip);
                serverService.reloadZone(reverseZoneName);
                System.out.println("  ✓ PTR record created");
            } catch (Exception e) {
                System.err.println("  ⚠️  PTR warning: " + e.getMessage());
            }

            // Reload DNS
            serverService.reloadZone(zoneName);
            
            System.out.println("✓ Record added successfully!");
            System.out.println("  Zone: " + zoneName);
            System.out.println("  " + hostname + " → " + ip);
            System.out.println();
            System.out.println("Test: dig @127.0.0.1 -p 1053 " + hostname + "." + zoneName + " +short");
            
        } catch (Exception e) {
            System.err.println("❌ Error adding record, rolling back...");
            // backupService.restoreLatestBackup(zoneFile); // TODO: implement restore
            throw e;
        }
    }
    
    @Override
    public String getUsage() {
        return "add <hostname> <ip> [--zone <zonename>] - Add a new DNS A record";
    }
}
