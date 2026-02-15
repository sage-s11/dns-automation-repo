package com.dnsmanager.services;

import java.io.IOException;

/**
 * Updated DnsServerService - Works with any zone dynamically
 */
public class DnsServerServiceDynamic {
    private final String containerName;
    private final int rndcPort;
    
    public DnsServerServiceDynamic(String containerName, int rndcPort) {
        this.containerName = containerName;
        this.rndcPort = rndcPort;
    }
    
    /**
     * Reload a specific zone
     */
    public void reloadZone(String zoneName) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(
            "podman", "exec", containerName, 
            "rndc", "-p", String.valueOf(rndcPort), 
            "reload", zoneName
        );
        
        pb.redirectErrorStream(true);
        
        Process process = pb.start();
        
        // Read output
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
                System.err.println("rndc output: " + output.toString());
                throw new IOException("Failed to reload zone " + zoneName + " (exit code: " + exitCode + ")");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while reloading zone", e);
        }
    }
    
    /**
     * Check if DNS server is running
     */
    public boolean isRunning() throws IOException {
        ProcessBuilder pb = new ProcessBuilder(
            "podman", "ps", "--filter", "name=" + containerName, "--format", "{{.Names}}"
        );
        
        Process process = pb.start();
        
        java.io.BufferedReader reader = new java.io.BufferedReader(
            new java.io.InputStreamReader(process.getInputStream()));
        
        String output = reader.readLine();
        
        try {
            process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while checking DNS server status", e);
        }
        
        return output != null && output.contains(containerName);
    }
    
    /**
     * Get DNS server status
     */
    public String getStatus() throws IOException {
        ProcessBuilder pb = new ProcessBuilder(
            "podman", "exec", containerName,
            "rndc", "-p", String.valueOf(rndcPort),
            "status"
        );
        
        Process process = pb.start();
        
        java.io.BufferedReader reader = new java.io.BufferedReader(
            new java.io.InputStreamReader(process.getInputStream()));
        
        StringBuilder output = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
        }
        
        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("Failed to get DNS server status (exit code: " + exitCode + ")");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while getting DNS server status", e);
        }
        
        return output.toString();
    }
}
