package com.dnsmanager.services;

import java.io.IOException;
import java.nio.file.Path;

public class DnsServerService {
    private final String containerName;
    private final String zoneName;
    private final int rndcPort;
    
    public DnsServerService(String containerName, String zoneName, int rndcPort) {
        this.containerName = containerName;
        this.zoneName = zoneName;
        this.rndcPort = rndcPort;
    }
    
    public boolean reloadZone() throws IOException, InterruptedException {
        // Reload all zones instead of specific zone to avoid permission issues
        ProcessBuilder pb = new ProcessBuilder(
            "podman", "exec", containerName,
            "rndc", "-p", String.valueOf(rndcPort), "reload"
        );
        
        Process process = pb.start();
        int exitCode = process.waitFor();
        return exitCode == 0;
    }
    
    public boolean validateZone(Path zoneFile) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
            "podman", "exec", containerName,
            "named-checkzone", zoneName, "/zones/" + zoneFile.getFileName()
        );
        pb.inheritIO();
        
        Process process = pb.start();
        int exitCode = process.waitFor();
        return exitCode == 0;
    }
    
    public void printStatus() throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
            "podman", "exec", containerName,
            "rndc", "-p", String.valueOf(rndcPort), "status"
        );
        pb.inheritIO();
        
        Process process = pb.start();
        process.waitFor();
    }
}
