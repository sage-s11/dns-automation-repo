package com.dnsmanager.services;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;

public class ZoneFileService {
    private final Path zoneFile;
    
    public ZoneFileService(String zoneName) {
        this.zoneFile = Paths.get("zones", "db." + zoneName);
    }
    
    public Path getZoneFile() {
        return zoneFile;
    }
    
    public List<String> readLines() throws IOException {
        return Files.readAllLines(zoneFile);
    }
    
    public List<com.dnsmanager.models.DnsRecord> parseRecords() throws IOException {
        List<String> lines = readLines();
        List<com.dnsmanager.models.DnsRecord> records = new ArrayList<>();
        
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith(";") || 
                trimmed.startsWith("$") || trimmed.startsWith("@") ||
                trimmed.contains("SOA")) {
                continue;
            }
            
            String[] parts = trimmed.split("\\s+");
            if (parts.length >= 3) {
                String hostname = parts[0];
                String type = parts[1].equals("IN") ? parts[2] : parts[1];
                String value = parts[1].equals("IN") ? parts[3] : parts[2];
                
                records.add(new com.dnsmanager.models.DnsRecord(hostname, type, value));
            }
        }
        
        return records;
    }
    
    public void writeLines(List<String> lines) throws IOException {
        // Write to temporary file first
        Path tempFile = Paths.get(zoneFile.toString() + ".tmp");
        Files.write(tempFile, lines);
        
        // Set permissions to 644 (rw-r--r--)
        Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-r--r--");
        Files.setPosixFilePermissions(tempFile, perms);
        
        // Move temp file to actual zone file (atomic operation)
        Files.move(tempFile, zoneFile, StandardCopyOption.REPLACE_EXISTING);
        
        // Ensure final file has correct permissions
        Files.setPosixFilePermissions(zoneFile, perms);
    }
    
    public long bumpSerial() throws IOException {
        List<String> lines = readLines();
        long newSerial = 0;
        
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.contains("; Serial")) {
                String[] parts = line.trim().split("\\s+");
                long currentSerial = Long.parseLong(parts[0]);
                
                String today = new java.text.SimpleDateFormat("yyyyMMdd").format(new Date());
                long todayPrefix = Long.parseLong(today) * 100;
                
                if (currentSerial >= todayPrefix && currentSerial < todayPrefix + 100) {
                    newSerial = currentSerial + 1;
                } else {
                    newSerial = todayPrefix + 1;
                }
                
                String newLine = line.replaceFirst("\\d+", String.valueOf(newSerial));
                lines.set(i, newLine);
                break;
            }
        }
        
        writeLines(lines);
        return newSerial;
    }
    
    public void addOrUpdateRecord(String hostname, String type, String value) throws IOException {
        List<String> lines = readLines();
        boolean recordUpdated = false;
        
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.startsWith(hostname + "\t") || line.startsWith(hostname + " ")) {
                lines.set(i, String.format("%s\tIN\t%s\t%s", hostname, type, value));
                recordUpdated = true;
                break;
            }
        }
        
        if (!recordUpdated) {
            int insertIndex = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).contains("; Minimum")) {
                    insertIndex = i + 1;
                    break;
                }
            }
            
            if (insertIndex > 0) {
                lines.add(insertIndex, String.format("%s\tIN\t%s\t%s", hostname, type, value));
            } else {
                lines.add(String.format("%s\tIN\t%s\t%s", hostname, type, value));
            }
        }
        
        writeLines(lines);
    }
}
