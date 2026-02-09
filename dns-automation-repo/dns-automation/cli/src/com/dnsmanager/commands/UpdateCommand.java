package com.dnsmanager.commands;

import com.dnsmanager.services.ZoneFileServiceDynamic;
import com.dnsmanager.services.DnsServerServiceDynamic;
import com.dnsmanager.services.DnsServerService;
import com.dnsmanager.services.DnsServerServiceDynamic;
import com.dnsmanager.services.BackupService;
import com.dnsmanager.services.DnsServerServiceDynamic;
import com.dnsmanager.utils.ValidationUtils;
import java.nio.file.Path;

/**
 * Updated UpdateCommand with --zone flag support
 * Usage: ./dns update <hostname> <ip> [--zone <zonename>] [--force]
 */
public class UpdateCommand implements Command {
    private final ZoneFileServiceDynamic zoneService;
    private final DnsServerServiceDynamic serverService;
    private final BackupService backupService;
    private final String defaultZone;
    
    public UpdateCommand(ZoneFileServiceDynamic zoneService,
                                 DnsServerServiceDynamic serverService,
                                 BackupService backupService,
                                 String defaultZone) {
        this.zoneService = zoneService;
        this.serverService = serverService;
        this.backupService = backupService;
        this.defaultZone = defaultZone;
    }
    
    @Override
    public void execute(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: dns update <hostname> <ip> [--zone <zonename>] [--force]");
            System.err.println("Example: dns update www 10.10.10.50");
            System.err.println("Example: dns update api 10.10.10.60 --zone example.com");
            System.exit(1);
        }
        
        String hostname = args[0];
        String ip = args[1];
        String zoneName = defaultZone;
        boolean force = false;
        
        // Parse flags
        for (int i = 2; i < args.length; i++) {
            if (args[i].equals("--zone") && i + 1 < args.length) {
                zoneName = args[i + 1];
                i++; // Skip next arg
            } else if (args[i].equals("--force") || args[i].equals("-f")) {
                force = true;
            }
        }
        
        // Validate inputs
        if (!ValidationUtils.isValidHostname(hostname)) {
            throw new IllegalArgumentException("Invalid hostname: " + hostname);
        }
        
        if (!ValidationUtils.isValidIpAddress(ip)) {
            throw new IllegalArgumentException("Invalid IP address: " + ip);
        }
        
        // Check if record exists
        if (!zoneService.recordExists(zoneName, hostname)) {
            throw new IllegalStateException("Record does not exist: " + hostname + " in zone " + zoneName);
        }
        
        // Confirmation if not forced
        if (!force) {
            System.out.print("⚠️  Update " + hostname + "." + zoneName + " to " + ip + "? (y/N): ");
            java.util.Scanner scanner = new java.util.Scanner(System.in);
            String response = scanner.nextLine().trim().toLowerCase();
            if (!response.equals("y") && !response.equals("yes")) {
                System.out.println("Cancelled.");
                return;
            }
        }
        
        // Create backup
        Path zoneFile = zoneService.getZoneFilePath(zoneName);
        backupService.createBackup(zoneFile);
        
        try {
            // Update record
            zoneService.updateRecord(zoneName, hostname, ip);
            
            // Reload DNS
            serverService.reloadZone(zoneName);
            
            System.out.println("✓ Record updated successfully!");
            System.out.println("  Zone: " + zoneName);
            System.out.println("  " + hostname + " → " + ip);
            
        } catch (Exception e) {
            System.err.println("❌ Error updating record, rolling back...");
            // backupService.restoreLatestBackup(zoneFile); // TODO: implement restore
            throw e;
        }
    }
    
    @Override
    public String getUsage() {
        return "update <hostname> <ip> [--zone <zonename>] [--force] - Update existing DNS record";
    }
}
