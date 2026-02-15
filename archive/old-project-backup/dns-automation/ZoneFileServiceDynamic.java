package com.dnsmanager.services;

import com.dnsmanager.models.DnsRecord;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Updated ZoneFileService - Works with any zone dynamically
 * Replaces hardcoded zone with zone parameter
 */
public class ZoneFileServiceDynamic {
    private final Path zonesDirectory;
    
    public ZoneFileServiceDynamic(String zonesDir) {
        this.zonesDirectory = Paths.get(zonesDir);
    }
    
    /**
     * Get zone file path for a specific zone
     */
    public Path getZoneFilePath(String zoneName) {
        return zonesDirectory.resolve("db." + zoneName);
    }
    
    /**
     * Read all records from a zone
     */
    public List<DnsRecord> getRecords(String zoneName) throws IOException {
        Path zoneFile = getZoneFilePath(zoneName);
        
        if (!Files.exists(zoneFile)) {
            throw new FileNotFoundException("Zone file not found: " + zoneFile);
        }
        
        List<String> lines = Files.readAllLines(zoneFile);
        List<DnsRecord> records = new ArrayList<>();
        
        for (String line : lines) {
            String trimmed = line.trim();
            
            // Skip empty lines, comments, directives, SOA
            if (trimmed.isEmpty() || 
                trimmed.startsWith(";") || 
                trimmed.startsWith("$") ||
                trimmed.contains("SOA") ||
                trimmed.contains("Serial") ||
                trimmed.contains("Refresh") ||
                trimmed.contains("Retry") ||
                trimmed.contains("Expire") ||
                trimmed.contains("Minimum")) {
                continue;
            }
            
            // Parse A records
            if (trimmed.contains("\tIN\tA\t") || trimmed.contains(" IN A ")) {
                String[] parts = trimmed.split("\\s+");
                if (parts.length >= 4) {
                    String hostname = parts[0];
                    String ip = parts[3];
                    records.add(new DnsRecord(hostname, "A", ip));
                }
            }
        }
        
        return records;
    }
    
    /**
     * Add a new record to zone
     */
    public void addRecord(String zoneName, String hostname, String ip) throws IOException {
        Path zoneFile = getZoneFilePath(zoneName);
        List<String> lines = Files.readAllLines(zoneFile);
        
        // Check if record already exists
        for (String line : lines) {
            if (line.contains(hostname + "\t") && line.contains("\tA\t")) {
                throw new IllegalStateException("Record already exists: " + hostname);
            }
        }
        
        // Bump serial
        bumpSerial(zoneName);
        
        // Re-read after serial bump
        lines = Files.readAllLines(zoneFile);
        
        // Add record at end
        lines.add(hostname + "\tIN\tA\t" + ip);
        
        // Write back with proper permissions
        writeZoneFile(zoneFile, lines);
    }
    
    /**
     * Update existing record
     */
    public void updateRecord(String zoneName, String hostname, String newIp) throws IOException {
        Path zoneFile = getZoneFilePath(zoneName);
        List<String> lines = Files.readAllLines(zoneFile);
        
        boolean found = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.contains(hostname + "\t") && line.contains("\tA\t")) {
                lines.set(i, hostname + "\tIN\tA\t" + newIp);
                found = true;
                break;
            }
        }
        
        if (!found) {
            throw new IllegalStateException("Record not found: " + hostname);
        }
        
        // Bump serial
        bumpSerial(zoneName);
        
        // Re-read after serial bump
        lines = Files.readAllLines(zoneFile);
        
        // Update the record again (serial bump re-read the file)
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.contains(hostname + "\t") && line.contains("\tA\t")) {
                lines.set(i, hostname + "\tIN\tA\t" + newIp);
                break;
            }
        }
        
        writeZoneFile(zoneFile, lines);
    }
    
    /**
     * Delete a record
     */
    public void deleteRecord(String zoneName, String hostname) throws IOException {
        Path zoneFile = getZoneFilePath(zoneName);
        List<String> lines = Files.readAllLines(zoneFile);
        
        boolean found = false;
        List<String> newLines = new ArrayList<>();
        
        for (String line : lines) {
            if (line.contains(hostname + "\t") && line.contains("\tA\t")) {
                found = true;
                // Skip this line (delete it)
                continue;
            }
            newLines.add(line);
        }
        
        if (!found) {
            throw new IllegalStateException("Record not found: " + hostname);
        }
        
        // Bump serial
        bumpSerial(zoneName);
        
        // Re-read after serial bump
        newLines = Files.readAllLines(zoneFile);
        
        // Remove the record again
        List<String> finalLines = new ArrayList<>();
        for (String line : newLines) {
            if (line.contains(hostname + "\t") && line.contains("\tA\t")) {
                continue;
            }
            finalLines.add(line);
        }
        
        writeZoneFile(zoneFile, finalLines);
    }
    
    /**
     * Bump serial number
     */
    public void bumpSerial(String zoneName) throws IOException {
        Path zoneFile = getZoneFilePath(zoneName);
        List<String> lines = Files.readAllLines(zoneFile);
        
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.contains("Serial")) {
                String trimmed = line.trim();
                String[] parts = trimmed.split(";");
                if (parts.length > 0) {
                    String serialStr = parts[0].trim();
                    try {
                        long serial = Long.parseLong(serialStr);
                        long newSerial = serial + 1;
                        lines.set(i, "        " + newSerial + " ; Serial");
                        break;
                    } catch (NumberFormatException e) {
                        // Skip if not a valid serial
                    }
                }
            }
        }
        
        writeZoneFile(zoneFile, lines);
    }
    
    /**
     * Write zone file with proper permissions
     */
    private void writeZoneFile(Path zoneFile, List<String> lines) throws IOException {
        Files.write(zoneFile, lines);
        
        // Set permissions to 644 (rw-r--r--)
        try {
            Set<java.nio.file.attribute.PosixFilePermission> perms = 
                java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--");
            Files.setPosixFilePermissions(zoneFile, perms);
        } catch (UnsupportedOperationException e) {
            // Windows doesn't support POSIX permissions, skip
        }
    }
    
    /**
     * Check if record exists
     */
    public boolean recordExists(String zoneName, String hostname) throws IOException {
        List<DnsRecord> records = getRecords(zoneName);
        for (DnsRecord record : records) {
            if (record.getHostname().equals(hostname)) {
                return true;
            }
        }
        return false;
    }
}
