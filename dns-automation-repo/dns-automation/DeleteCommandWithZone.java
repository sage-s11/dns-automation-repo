package com.dnsmanager.commands;

import com.dnsmanager.services.ZoneFileServiceDynamic;
import com.dnsmanager.services.DnsServerService;
import com.dnsmanager.services.BackupService;
import java.nio.file.Path;

/**
 * Updated DeleteCommand with --zone flag support
 * Usage: ./dns delete <hostname> [--zone <zonename>] [--force]
 */
public class DeleteCommandWithZone implements Command {
    private final ZoneFileServiceDynamic zoneService;
    private final DnsServerService serverService;
    private final BackupService backupService;
    private final String defaultZone;
    
    public DeleteCommandWithZone(ZoneFileServiceDynamic zoneService,
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
        if (args.length < 1) {
            System.err.println("Usage: dns delete <hostname> [--zone <zonename>] [--force]");
            System.err.println("Example: dns delete www");
            System.err.println("Example: dns delete api --zone example.com --force");
            System.exit(1);
        }
        
        String hostname = args[0];
        String zoneName = defaultZone;
        boolean force = false;
        
        // Parse flags
        for (int i = 1; i < args.length; i++) {
            if (args[i].equals("--zone") && i + 1 < args.length) {
                zoneName = args[i + 1];
                i++; // Skip next arg
            } else if (args[i].equals("--force") || args[i].equals("-f")) {
                force = true;
            }
        }
        
        // Check if record exists
        if (!zoneService.recordExists(zoneName, hostname)) {
            throw new IllegalStateException("Record does not exist: " + hostname + " in zone " + zoneName);
        }
        
        // Confirmation if not forced
        if (!force) {
            System.out.print("⚠️  Delete " + hostname + "." + zoneName + "? This cannot be undone! (y/N): ");
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
            // Delete record
            zoneService.deleteRecord(zoneName, hostname);
            
            // Reload DNS
            serverService.reloadZone(zoneName);
            
            System.out.println("✓ Record deleted successfully!");
            System.out.println("  Zone: " + zoneName);
            System.out.println("  Deleted: " + hostname);
            
        } catch (Exception e) {
            System.err.println("❌ Error deleting record, rolling back...");
            backupService.restoreLatestBackup(zoneFile);
            throw e;
        }
    }
    
    @Override
    public String getUsage() {
        return "delete <hostname> [--zone <zonename>] [--force] - Delete DNS record";
    }
}
