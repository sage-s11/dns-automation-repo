package com.dnsmanager.services;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Service for managing BIND9 configuration file (named.conf)
 */
public class BindConfigService {
    private final Path configFile;
    
    public BindConfigService(String configPath) {
        this.configFile = Paths.get(configPath);
    }
    
    /**
     * Add a zone to named.conf
     */
    public void addZone(String zoneName, String zoneFilePath) throws IOException {
        List<String> lines = Files.readAllLines(configFile);
        
        // Check if zone already exists
        if (zoneExists(lines, zoneName)) {
            throw new IllegalStateException("Zone '" + zoneName + "' already exists in config");
        }
        
        // Find position to insert (before closing brace or at end)
        int insertIndex = lines.size();
        for (int i = lines.size() - 1; i >= 0; i--) {
            if (lines.get(i).trim().equals("};")) {
                insertIndex = i + 1;
                break;
            }
        }
        
        // Create zone block
        List<String> zoneBlock = createZoneBlock(zoneName, zoneFilePath);
        
        // Insert zone block
        lines.addAll(insertIndex, zoneBlock);
        
        // Write back
        Files.write(configFile, lines);
    }
    
    /**
     * Remove a zone from named.conf
     */
    public void removeZone(String zoneName) throws IOException {
        List<String> lines = Files.readAllLines(configFile);
        List<String> newLines = new ArrayList<>();
        
        boolean inZoneBlock = false;
        boolean isTargetZone = false;
        int braceCount = 0;
        
        for (String line : lines) {
            String trimmed = line.trim();
            
            // Check if entering a zone block
            if (trimmed.startsWith("zone ") && trimmed.contains(zoneName)) {
                inZoneBlock = true;
                isTargetZone = true;
                braceCount = 0;
                continue; // Skip this line
            }
            
            if (inZoneBlock) {
                // Count braces to know when zone block ends
                for (char c : trimmed.toCharArray()) {
                    if (c == '{') braceCount++;
                    if (c == '}') braceCount--;
                }
                
                // If we're back to 0, zone block ended
                if (braceCount < 0) {
                    inZoneBlock = false;
                    isTargetZone = false;
                    continue; // Skip closing brace with semicolon
                }
                
                // Skip lines inside target zone
                if (isTargetZone) {
                    continue;
                }
            }
            
            newLines.add(line);
        }
        
        Files.write(configFile, newLines);
    }
    
    /**
     * List all zones in named.conf
     */
    public List<String> listZones() throws IOException {
        List<String> lines = Files.readAllLines(configFile);
        List<String> zones = new ArrayList<>();
        
        Pattern zonePattern = Pattern.compile("zone\\s+\"([^\"]+)\"");
        
        for (String line : lines) {
            Matcher matcher = zonePattern.matcher(line);
            if (matcher.find()) {
                String zoneName = matcher.group(1);
                // Skip reverse zones (they end with .arpa)
                if (!zoneName.endsWith(".arpa")) {
                    zones.add(zoneName);
                }
            }
        }
        
        return zones;
    }
    
    /**
     * Check if zone exists in config
     */
    private boolean zoneExists(List<String> lines, String zoneName) {
        Pattern zonePattern = Pattern.compile("zone\\s+\"" + Pattern.quote(zoneName) + "\"");
        
        for (String line : lines) {
            if (zonePattern.matcher(line).find()) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Create zone configuration block
     */
    private List<String> createZoneBlock(String zoneName, String zoneFilePath) {
        List<String> block = new ArrayList<>();
        block.add("");
        block.add("zone \"" + zoneName + "\" {");
        block.add("    type master;");
        block.add("    file \"/" + zoneFilePath + "\";");
        block.add("    allow-update { none; };");
        block.add("};");
        
        return block;
    }
    
    /**
     * Reload BIND configuration (tells BIND to reread named.conf)
     */
    public void reloadConfig() throws IOException {
    ProcessBuilder pb = new ProcessBuilder(
        "podman", "exec", "bind9-demo", "rndc", "-p", "1953", "reconfig"
    );
    
    pb.redirectErrorStream(true); // Combine stdout and stderr
    
    Process process = pb.start();
    
    // Read output for debugging
    java.io.BufferedReader reader = new java.io.BufferedReader(
        new java.io.InputStreamReader(process.getInputStream()));
    String line;
    StringBuilder output = new StringBuilder();
    while ((line = reader.readLine()) != null) {
        output.append(line).append("\n");
    }
    
    try {
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            System.err.println("Command output: " + output.toString());
            throw new IOException("Failed to reload BIND config (exit code: " + exitCode + ")");
        }
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IOException("Interrupted while reloading BIND config", e);
    }
}
}
