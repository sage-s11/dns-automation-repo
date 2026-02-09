package com.dnsmanager.services;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Enterprise-grade network validation
 * Multi-method IP activity detection across subnets
 */
public class EnterpriseNetworkValidator {
    
    // Configuration
    private static final int TCP_TIMEOUT_MS = 200;
    private static final int PING_TIMEOUT_SEC = 1;
    private static final int MAX_PING_ATTEMPTS = 3;
    private static final int PARALLEL_THREADS = 20;
    
    // Common enterprise ports (ordered by likelihood)
    private static final int[] COMMON_PORTS = {
        443,   // HTTPS (web servers)
        80,    // HTTP
        22,    // SSH (Linux servers)
        3389,  // RDP (Windows servers)
        445,   // SMB (Windows file sharing)
        3306,  // MySQL
        5432,  // PostgreSQL
        1521,  // Oracle
        8080,  // Alt HTTP
        8443,  // Alt HTTPS
        25,    // SMTP
        53,    // DNS
        389,   // LDAP
        636    // LDAPS
    };
    
    public enum ValidationMethod {
        TCP_PORT_SCAN,
        ICMP_PING,
        DATABASE_CHECK,
        NONE
    }
    
    public static class IPValidationResult {
        public String ip;
        public boolean isActive;
        public ValidationMethod detectionMethod;
        public String details;
        public long responseTimeMs;
        public List<Integer> openPorts = new ArrayList<>();
        public boolean hasExistingDNS;
        public boolean hasDHCPLease;
        
        public String getRiskLevel() {
            if (!isActive) return "SAFE";
            if (hasExistingDNS) return "LOW";  // Has DNS = managed
            if (hasDHCPLease) return "MEDIUM";  // DHCP lease = temporary
            return "HIGH";  // Active but no DNS/DHCP = unknown device!
        }
        
        @Override
        public String toString() {
            if (!isActive) {
                return ip + ": Not responding (safe to use)";
            }
            
            StringBuilder sb = new StringBuilder();
            sb.append(ip).append(": ⚠️  ACTIVE");
            sb.append(" [").append(detectionMethod).append("]");
            
            if (!openPorts.isEmpty()) {
                sb.append(" Ports: ").append(openPorts);
            }
            
            if (hasExistingDNS) {
                sb.append(" ✅ Has DNS");
            } else if (hasDHCPLease) {
                sb.append(" ⚠️  DHCP lease only");
            } else {
                sb.append(" ❌ No DNS/DHCP (UNKNOWN DEVICE!)");
            }
            
            sb.append(" Risk: ").append(getRiskLevel());
            
            return sb.toString();
        }
    }
    
    /**
     * Validate single IP (comprehensive check)
     */
    public IPValidationResult validateIP(String ip) {
        IPValidationResult result = new IPValidationResult();
        result.ip = ip;
        result.isActive = false;
        
        long startTime = System.currentTimeMillis();
        
        // PHASE 1: TCP Port Scan (fastest, works across subnets)
        List<Integer> openPorts = scanCommonPorts(ip);
        if (!openPorts.isEmpty()) {
            result.isActive = true;
            result.detectionMethod = ValidationMethod.TCP_PORT_SCAN;
            result.openPorts = openPorts;
            result.details = "Open ports detected: " + openPorts;
            result.responseTimeMs = System.currentTimeMillis() - startTime;
            return result;
        }
        
        // PHASE 2: ICMP Ping with retry (handles packet loss)
        if (pingWithRetry(ip)) {
            result.isActive = true;
            result.detectionMethod = ValidationMethod.ICMP_PING;
            result.details = "Responds to ICMP ping";
            result.responseTimeMs = System.currentTimeMillis() - startTime;
            return result;
        }
        
        // PHASE 3: Not detected
        result.isActive = false;
        result.detectionMethod = ValidationMethod.NONE;
        result.details = "No response detected";
        result.responseTimeMs = System.currentTimeMillis() - startTime;
        
        return result;
    }
    
    /**
     * Scan common ports (fast, parallel)
     */
    private List<Integer> scanCommonPorts(String ip) {
        List<Integer> openPorts = new ArrayList<>();
        
        // Only scan first few ports for speed (most critical services)
        int portsToScan = Math.min(COMMON_PORTS.length, 5);
        
        for (int i = 0; i < portsToScan; i++) {
            int port = COMMON_PORTS[i];
            
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(ip, port), TCP_TIMEOUT_MS);
                openPorts.add(port);
                // Found open port - device is definitely active!
                break;  // No need to check more ports
            } catch (IOException e) {
                // Port closed or timeout, continue
            }
        }
        
        return openPorts;
    }
    
    /**
     * Ping with adaptive retry (handles packet loss)
     */
    private boolean pingWithRetry(String ip) {
        for (int attempt = 1; attempt <= MAX_PING_ATTEMPTS; attempt++) {
            if (singlePing(ip)) {
                return true;  // Success!
            }
            
            // First attempt failed - could be packet loss
            // But don't retry if this is already the last attempt
            if (attempt < MAX_PING_ATTEMPTS) {
                try {
                    Thread.sleep(100);  // Small delay before retry
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        
        return false;  // All attempts failed
    }
    
    /**
     * Single ping attempt
     */
    private boolean singlePing(String ip) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "ping", "-c", "1", "-W", String.valueOf(PING_TIMEOUT_SEC), ip
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            return process.waitFor() == 0;
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Validate multiple IPs in parallel (FAST!)
     */
    public Map<String, IPValidationResult> validateIPs(List<String> ips) {
        Map<String, IPValidationResult> results = new ConcurrentHashMap<>();
        
        if (ips.isEmpty()) {
            return results;
        }
        
        System.out.println("🔍 Validating " + ips.size() + " IPs...");
        long startTime = System.currentTimeMillis();
        
        // Parallel execution
        ExecutorService executor = Executors.newFixedThreadPool(
            Math.min(PARALLEL_THREADS, ips.size())
        );
        
        List<Future<IPValidationResult>> futures = new ArrayList<>();
        
        for (String ip : ips) {
            futures.add(executor.submit(() -> validateIP(ip)));
        }
        
        // Collect results
        int completed = 0;
        for (Future<IPValidationResult> future : futures) {
            try {
                IPValidationResult result = future.get(10, TimeUnit.SECONDS);
                results.put(result.ip, result);
                completed++;
                
                // Progress indicator
                if (completed % 10 == 0 || completed == ips.size()) {
                    System.out.print("\r  Progress: " + completed + "/" + ips.size());
                }
                
            } catch (TimeoutException e) {
                System.err.println("\n  ⚠️  Timeout validating IP");
            } catch (Exception e) {
                System.err.println("\n  ⚠️  Error: " + e.getMessage());
            }
        }
        
        executor.shutdown();
        try {
            executor.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
        
        long elapsed = System.currentTimeMillis() - startTime;
        System.out.println("\n✅ Validation complete in " + (elapsed / 1000.0) + "s");
        
        return results;
    }
    
    /**
     * Get validation summary
     */
    public static String getSummary(Map<String, IPValidationResult> results) {
        int total = results.size();
        int active = 0;
        int safeToUse = 0;
        int highRisk = 0;
        
        for (IPValidationResult result : results.values()) {
            if (result.isActive) {
                active++;
                if (result.getRiskLevel().equals("HIGH")) {
                    highRisk++;
                }
            } else {
                safeToUse++;
            }
        }
        
        return String.format(
            "Total: %d | Active: %d | Safe: %d | High Risk: %d",
            total, active, safeToUse, highRisk
        );
    }
}

