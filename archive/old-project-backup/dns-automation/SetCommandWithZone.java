package com.dnsmanager.commands;

import com.dnsmanager.services.ZoneFileServiceDynamic;
import com.dnsmanager.services.DnsServerService;
import com.dnsmanager.services.BackupService;
import com.dnsmanager.utils.ValidationUtils;
import java.nio.file.Path;

/**
 * Updated SetCommand with --zone flag support
 * Smart command: Adds if doesn't exist, updates if exists
 * Usage: ./dns set <hostname> <ip> [--zone <zonename>] [--force]
 */
public class SetCommandWithZone implements Command {
    private final ZoneFileServiceDynamic zoneService;
    private final DnsServerService serverService;
    private final BackupService backupService;
    private final String defaultZone;
    
    public SetCommandWithZone(ZoneFileServiceDynamic zoneService,
                              DnsServerService serverService,
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
            System.err.println("Usage: dns set <hostname> <ip> [--zone <zonename>] [--force]");
            System.err.println("Example: dns set www 10.10.10.50");
            System.err.println("Example: dns set api 10.10.10.60 --zone example.com");
            System.err.println();
            System.err.println("This command will:");
            System.err.println("  - Add the record if it doesn't exist");
            System.err.println("  - Update the record if it already exists");
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
        boolean exists = zoneService.recordExists(zoneName, hostname);
        
        // Confirmation if not forced and updating
        if (exists && !force) {
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
            if (exists) {
                // Update existing record
                zoneService.updateRecord(zoneName, hostname, ip);
                System.out.println("✓ Record updated successfully!");
            } else {
                // Add new record
                zoneService.addRecord(zoneName, hostname, ip);
                System.out.println("✓ Record added successfully!");
            }
            
            // Reload DNS
            serverService.reloadZone(zoneName);
            
            System.out.println("  Zone: " + zoneName);
            System.out.println("  " + hostname + " → " + ip);
            
        } catch (Exception e) {
            System.err.println("❌ Error, rolling back...");
            backupService.restoreLatestBackup(zoneFile);
            throw e;
        }
    }
    
    @Override
    public String getUsage() {
        return "set <hostname> <ip> [--zone <zonename>] [--force] - Add or update DNS record (smart command)";
    }
}
