package com.dnsmanager.services;

import com.dnsmanager.utils.ReverseZoneHelper;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * Service for managing reverse DNS zones and PTR records
 * Handles automatic PTR record creation/updates/deletion
 */
public class ReverseZoneService {
    private final Path zonesDirectory;
    private final ZoneFileServiceDynamic zoneFileService;
    
    public ReverseZoneService(String zonesDir, ZoneFileServiceDynamic zoneFileService) {
        this.zonesDirectory = Paths.get(zonesDir);
        this.zoneFileService = zoneFileService;
    }
    
    /**
     * Check if reverse zone exists for given IP
     */
    public boolean reverseZoneExists(String ipAddress) {
        String reverseZoneName = ReverseZoneHelper.getReverseZoneName(ipAddress);
        Path reverseZoneFile = zonesDirectory.resolve("db." + reverseZoneName);
        return Files.exists(reverseZoneFile);
    }
    
    /**
     * Create reverse zone if it doesn't exist
     */
    public void ensureReverseZoneExists(String ipAddress, String nsIpAddress) throws IOException {
        String reverseZoneName = ReverseZoneHelper.getReverseZoneName(ipAddress);
        Path reverseZoneFile = zonesDirectory.resolve("db." + reverseZoneName);
        
        if (Files.exists(reverseZoneFile)) {
            return; // Already exists
        }
        
        // Create reverse zone file
        createReverseZoneFile(reverseZoneName, nsIpAddress);
    }
    
    /**
     * Add PTR record to reverse zone
     */
    public void addPtrRecord(String ipAddress, String hostname, String forwardZone) throws IOException {
        String reverseZoneName = ReverseZoneHelper.getReverseZoneName(ipAddress);
        String ptrHost = ReverseZoneHelper.getPtrHost(ipAddress);
        String fqdn = ReverseZoneHelper.createFqdn(hostname, forwardZone);
        
        Path reverseZoneFile = zonesDirectory.resolve("db." + reverseZoneName);
        
        if (!Files.exists(reverseZoneFile)) {
            throw new IllegalStateException("Reverse zone does not exist: " + reverseZoneName);
        }
        
        List<String> lines = Files.readAllLines(reverseZoneFile);
        
        // Check if PTR already exists
        for (String line : lines) {
            if (line.contains(ptrHost + "\t") && line.contains("\tPTR\t")) {
                throw new IllegalStateException("PTR record already exists for " + ipAddress);
            }
        }
        
        // Bump serial
        lines = bumpSerial(reverseZoneFile, lines);
        
        // Add PTR record
        lines.add(ptrHost + "\tIN\tPTR\t" + fqdn);
        
        // Write back
        writeZoneFile(reverseZoneFile, lines);
    }
    
    /**
     * Update PTR record in reverse zone
     */
    public void updatePtrRecord(String ipAddress, String newHostname, String forwardZone) throws IOException {
        String reverseZoneName = ReverseZoneHelper.getReverseZoneName(ipAddress);
        String ptrHost = ReverseZoneHelper.getPtrHost(ipAddress);
        String fqdn = ReverseZoneHelper.createFqdn(newHostname, forwardZone);
        
        Path reverseZoneFile = zonesDirectory.resolve("db." + reverseZoneName);
        
        if (!Files.exists(reverseZoneFile)) {
            // No reverse zone, just skip (not critical)
            return;
        }
        
        List<String> lines = Files.readAllLines(reverseZoneFile);
        boolean found = false;
        
        // Find and update PTR record
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.contains(ptrHost + "\t") && line.contains("\tPTR\t")) {
                lines.set(i, ptrHost + "\tIN\tPTR\t" + fqdn);
                found = true;
                break;
            }
        }
        
        if (!found) {
            // PTR doesn't exist, add it
            lines = bumpSerial(reverseZoneFile, lines);
            lines.add(ptrHost + "\tIN\tPTR\t" + fqdn);
        } else {
            // Bump serial after update
            lines = bumpSerial(reverseZoneFile, lines);
        }
        
        writeZoneFile(reverseZoneFile, lines);
    }
    
    /**
     * Delete PTR record from reverse zone
     */
    public void deletePtrRecord(String ipAddress) throws IOException {
        String reverseZoneName = ReverseZoneHelper.getReverseZoneName(ipAddress);
        String ptrHost = ReverseZoneHelper.getPtrHost(ipAddress);
        
        Path reverseZoneFile = zonesDirectory.resolve("db." + reverseZoneName);
        
        if (!Files.exists(reverseZoneFile)) {
            // No reverse zone, nothing to delete
            return;
        }
        
        List<String> lines = Files.readAllLines(reverseZoneFile);
        List<String> newLines = new ArrayList<>();
        boolean found = false;
        
        // Remove PTR record
        for (String line : lines) {
            if (line.contains(ptrHost + "\t") && line.contains("\tPTR\t")) {
                found = true;
                continue; // Skip this line
            }
            newLines.add(line);
        }
        
        if (found) {
            // Bump serial
            newLines = bumpSerial(reverseZoneFile, newLines);
            writeZoneFile(reverseZoneFile, newLines);
        }
    }
    
    /**
     * Create a reverse zone file
     */
    private void createReverseZoneFile(String reverseZoneName, String nsIpAddress) throws IOException {
        Path reverseZoneFile = zonesDirectory.resolve("db." + reverseZoneName);
        
        // Generate initial serial
        long serial = generateInitialSerial();
        
        List<String> lines = new ArrayList<>();
        lines.add("$TTL 86400");
        lines.add("@   IN  SOA ns1." + reverseZoneName + ". admin." + reverseZoneName + ". (");
        lines.add("        " + serial + " ; Serial");
        lines.add("        3600       ; Refresh");
        lines.add("        1800       ; Retry");
        lines.add("        604800     ; Expire");
        lines.add("        86400 )    ; Minimum");
        lines.add("");
        lines.add("; Nameserver");
        lines.add("@   IN  NS  ns1." + reverseZoneName + ".");
        lines.add("");
        lines.add("; PTR Records");
        lines.add("");
        
        writeZoneFile(reverseZoneFile, lines);
    }
    
    /**
     * Bump serial number in zone file
     */
    private List<String> bumpSerial(Path zoneFile, List<String> lines) throws IOException {
        List<String> newLines = new ArrayList<>();
        
        for (String line : lines) {
            if (line.contains("Serial")) {
                String trimmed = line.trim();
                String[] parts = trimmed.split(";");
                if (parts.length > 0) {
                    String serialStr = parts[0].trim();
                    try {
                        long serial = Long.parseLong(serialStr);
                        long newSerial = serial + 1;
                        newLines.add("        " + newSerial + " ; Serial");
                        continue;
                    } catch (NumberFormatException e) {
                        // Keep original line if can't parse
                    }
                }
            }
            newLines.add(line);
        }
        
        return newLines;
    }
    
    /**
     * Generate initial serial number (YYYYMMDD01)
     */
    private long generateInitialSerial() {
        java.time.LocalDate today = java.time.LocalDate.now();
        return Long.parseLong(String.format("%04d%02d%02d01", 
            today.getYear(), 
            today.getMonthValue(), 
            today.getDayOfMonth()));
    }
    
    /**
     * Write zone file with proper permissions
     */
    private void writeZoneFile(Path zoneFile, List<String> lines) throws IOException {
        Files.write(zoneFile, lines);
        
        // Set permissions to 644
        try {
            Set<java.nio.file.attribute.PosixFilePermission> perms = 
                java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--");
            Files.setPosixFilePermissions(zoneFile, perms);
        } catch (UnsupportedOperationException e) {
            // Windows doesn't support POSIX permissions
        }
    }
}
