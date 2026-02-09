package com.dnsmanager.services;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Network validation - ping checks, reverse DNS lookups
 * Uses parallel execution for speed
 */
public class NetworkValidator {
    
    private static final int PING_TIMEOUT_MS = 1000;  // 1 second
    private static final int THREAD_POOL_SIZE = 20;   // Parallel pings
    
    public static class NetworkStatus {
        public String ip;
        public boolean responds;      // Ping responds
        public boolean hasReverseDNS; // PTR record exists
        public long responseTime;     // Milliseconds
        public String warning;
        
        public boolean isSafe() {
            // Safe if IP doesn't respond OR has reverse DNS
            return !responds || hasReverseDNS;
        }
        
        @Override
        public String toString() {
            if (!responds) {
                return "Not responding (safe)";
            } else if (hasReverseDNS) {
                return "Active with DNS (safe)";
            } else {
                return "⚠️  Active without DNS (may be in use!)";
            }
        }
    }
    
    /**
     * Check single IP (fast ping)
     */
    public NetworkStatus checkIP(String ip) {
        NetworkStatus status = new NetworkStatus();
        status.ip = ip;
        status.responds = false;
        status.hasReverseDNS = false;
        
        try {
            InetAddress address = InetAddress.getByName(ip);
            
            long start = System.currentTimeMillis();
            status.responds = address.isReachable(PING_TIMEOUT_MS);
            status.responseTime = System.currentTimeMillis() - start;
            
            // Check reverse DNS (only if IP responds)
            if (status.responds) {
                try {
                    String hostname = address.getCanonicalHostName();
                    // If hostname != IP, then reverse DNS exists
                    status.hasReverseDNS = !hostname.equals(ip);
                } catch (Exception e) {
                    status.hasReverseDNS = false;
                }
                
                if (!status.hasReverseDNS) {
                    status.warning = "IP responds but has no reverse DNS (may be active device without DNS record)";
                }
            }
            
        } catch (IOException e) {
            status.responds = false;
        }
        
        return status;
    }
    
    /**
     * Check multiple IPs in parallel (FAST!)
     */
    public Map<String, NetworkStatus> checkIPs(List<String> ips) {
        Map<String, NetworkStatus> results = new ConcurrentHashMap<>();
        
        if (ips.isEmpty()) {
            return results;
        }
        
        // Use thread pool for parallel pings
        ExecutorService executor = Executors.newFixedThreadPool(
            Math.min(THREAD_POOL_SIZE, ips.size())
        );
        
        List<Future<NetworkStatus>> futures = new ArrayList<>();
        
        // Submit all ping tasks
        for (String ip : ips) {
            Future<NetworkStatus> future = executor.submit(() -> checkIP(ip));
            futures.add(future);
        }
        
        // Collect results
        for (Future<NetworkStatus> future : futures) {
            try {
                NetworkStatus status = future.get(PING_TIMEOUT_MS + 500, TimeUnit.MILLISECONDS);
                results.put(status.ip, status);
            } catch (TimeoutException e) {
                // Ping timed out - treat as not responding (safe)
            } catch (Exception e) {
                // Other error - treat as not responding
            }
        }
        
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
        
        return results;
    }
    
    /**
     * Quick check if IP appears to be in use
     */
    public boolean isIPInUse(String ip) {
        try {
            InetAddress address = InetAddress.getByName(ip);
            return address.isReachable(500);  // 500ms timeout
        } catch (IOException e) {
            return false;
        }
    }
}
