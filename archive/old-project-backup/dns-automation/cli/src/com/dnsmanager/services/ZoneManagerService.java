package com.dnsmanager.services;

import com.dnsmanager.models.Zone;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * Service for managing DNS zones (create, list, delete)
 */
public class ZoneManagerService {
    private final Path zonesDirectory;
    private final BindConfigService bindConfigService;
    
    public ZoneManagerService(String zonesDir, BindConfigService bindConfigService) {
        this.zonesDirectory = Paths.get(zonesDir);
        this.bindConfigService = bindConfigService;
    }
    
    /**
     * Create a new zone
     */
    public void createZone(String zoneName, String nsIpAddress) throws IOException {
        // Validate zone name
        if (!isValidZoneName(zoneName)) {
            throw new IllegalArgumentException("Invalid zone name: " + zoneName);
        }
        
        // Create Zone object
        Zone zone = new Zone(zoneName, nsIpAddress);
        
        // Check if zone file already exists
        Path zoneFilePath = zonesDirectory.resolve("db." + zoneName);
        if (Files.exists(zoneFilePath)) {
            throw new IllegalStateException("Zone file already exists: " + zoneFilePath);
        }
        
        // Create zone file from template
        createZoneFile(zone, zoneFilePath);
        
        // Add to BIND config
        bindConfigService.addZone(zoneName, "zones/db." + zoneName);
        
        // Reload BIND config
        bindConfigService.reloadConfig();
        
        System.out.println("✓ Zone created: " + zoneName);
        System.out.println("  Zone file: " + zoneFilePath);
        System.out.println("  Nameserver: " + zone.getNsHostname() + "." + zoneName + " (" + nsIpAddress + ")");
    }
    
    /**
     * Delete a zone
     */
    public void deleteZone(String zoneName, boolean force) throws IOException {
        Path zoneFilePath = zonesDirectory.resolve("db." + zoneName);
        
        if (!Files.exists(zoneFilePath)) {
            throw new IllegalStateException("Zone file does not exist: " + zoneFilePath);
        }
        
        // Confirmation if not forced
        if (!force) {
            System.out.print("⚠️  Delete zone '" + zoneName + "'? This cannot be undone! (y/N): ");
            Scanner scanner = new Scanner(System.in);
            String response = scanner.nextLine().trim().toLowerCase();
            if (!response.equals("y") && !response.equals("yes")) {
                System.out.println("Cancelled.");
                return;
            }
        }
        
        // Create backup before deletion
        Path backupPath = zonesDirectory.resolve("backups/db." + zoneName + "." + 
            new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".deleted");
        Files.createDirectories(backupPath.getParent());
        Files.copy(zoneFilePath, backupPath);
        
        // Remove from BIND config
        bindConfigService.removeZone(zoneName);
        
        // Delete zone file
        Files.delete(zoneFilePath);
        
        // Reload BIND config
        bindConfigService.reloadConfig();
        
        System.out.println("✓ Zone deleted: " + zoneName);
        System.out.println("  Backup saved: " + backupPath);
    }
    
    /**
     * List all zones
     */
    public List<String> listZones() throws IOException {
        return bindConfigService.listZones();
    }
    
    /**
     * Show zone details
     */
    public void showZone(String zoneName) throws IOException {
        Path zoneFilePath = zonesDirectory.resolve("db." + zoneName);
        
        if (!Files.exists(zoneFilePath)) {
            throw new IllegalStateException("Zone not found: " + zoneName);
        }
        
        // Read and parse zone file
        List<String> lines = Files.readAllLines(zoneFilePath);
        
        System.out.println("Zone: " + zoneName);
        System.out.println("File: " + zoneFilePath);
        System.out.println();
        
        // Parse SOA
        boolean inSOA = false;
        for (String line : lines) {
            String trimmed = line.trim();
            
            if (trimmed.contains("SOA")) {
                inSOA = true;
                System.out.println("SOA Record:");
            }
            
            if (inSOA) {
                if (trimmed.contains("Serial")) {
                    System.out.println("  Serial: " + trimmed.split(";")[0].trim());
                } else if (trimmed.contains("Refresh")) {
                    System.out.println("  Refresh: " + trimmed.split(";")[0].trim());
                } else if (trimmed.contains("Retry")) {
                    System.out.println("  Retry: " + trimmed.split(";")[0].trim());
                } else if (trimmed.contains("Expire")) {
                    System.out.println("  Expire: " + trimmed.split(";")[0].trim());
                } else if (trimmed.contains("Minimum")) {
                    System.out.println("  Minimum: " + trimmed.split(";")[0].trim().replace(")", "").trim());
                    inSOA = false;
                }
            }
        }
        
        // Count records
        int aRecords = 0;
        int nsRecords = 0;
        int otherRecords = 0;
        
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith(";") || 
                trimmed.startsWith("$") || trimmed.contains("SOA")) {
                continue;
            }
            
            if (trimmed.contains(" A ")) aRecords++;
            else if (trimmed.contains(" NS ")) nsRecords++;
            else otherRecords++;
        }
        
        System.out.println();
        System.out.println("Records:");
        System.out.println("  A records: " + aRecords);
        System.out.println("  NS records: " + nsRecords);
        System.out.println("  Other: " + otherRecords);
    }
    
    /**
     * Create zone file from template
     */
    private void createZoneFile(Zone zone, Path filePath) throws IOException {
        List<String> lines = new ArrayList<>();
        
        lines.add("$TTL 86400");
        lines.add("@   IN  SOA " + zone.getNsHostname() + "." + zone.getName() + ". " + zone.getAdminEmail() + ". (");
        lines.add("        " + zone.getSerial() + " ; Serial");
        lines.add("        3600       ; Refresh");
        lines.add("        1800       ; Retry");
        lines.add("        604800     ; Expire");
        lines.add("        86400 )    ; Minimum");
        lines.add("");
        lines.add("; Nameserver record");
        lines.add("@   IN  NS  " + zone.getNsHostname() + "." + zone.getName() + ".");
        lines.add("");
        lines.add("; Nameserver A record");
        lines.add(zone.getNsHostname() + "\tIN\tA\t" + zone.getNsIpAddress());
        lines.add("");
        lines.add("; Additional A records");
        lines.add("; Add your records below");
        lines.add("");
        
        // Write with proper permissions
        Files.write(filePath, lines);
        
        // Set permissions to 644
        Set<java.nio.file.attribute.PosixFilePermission> perms = 
            java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--");
        Files.setPosixFilePermissions(filePath, perms);
    }
    
    /**
     * Validate zone name
     */
    private boolean isValidZoneName(String zoneName) {
        // Basic validation: alphanumeric, dots, hyphens
        // Must not start/end with dot or hyphen
        // Must contain at least one dot (TLD)
        if (zoneName == null || zoneName.isEmpty()) {
            return false;
        }
        
        if (!zoneName.matches("^[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?)*$")) {
            return false;
        }
        
        if (!zoneName.contains(".")) {
            return false;
        }
        
        return true;
    }
}
