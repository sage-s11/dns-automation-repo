package com.dnsmanager;

import com.dnsmanager.services.NetworkValidator;
import com.dnsmanager.services.NetworkValidator.NetworkStatus;
import java.util.*;

public class TestNetworkValidator {
    public static void main(String[] args) {
        
        System.out.println("🔍 Testing Network Validator\n");
        System.out.println("=".repeat(70));
        
        NetworkValidator validator = new NetworkValidator();
        
        // Test 1: Localhost (should respond)
        System.out.println("Test 1: Localhost (127.0.0.1)");
        long start = System.currentTimeMillis();
        NetworkStatus local = validator.checkIP("127.0.0.1");
        long elapsed = System.currentTimeMillis() - start;
        
        System.out.println("  Responds: " + local.responds);
        System.out.println("  Time: " + elapsed + "ms");
        System.out.println("  Response time: " + local.responseTime + "ms");
        System.out.println();
        
        // Test 2: Google DNS (should respond)
        System.out.println("Test 2: Google DNS (8.8.8.8)");
        start = System.currentTimeMillis();
        NetworkStatus google = validator.checkIP("8.8.8.8");
        elapsed = System.currentTimeMillis() - start;
        
        System.out.println("  Responds: " + google.responds);
        System.out.println("  Time: " + elapsed + "ms");
        System.out.println("  Response time: " + google.responseTime + "ms");
        System.out.println();
        
        // Test 3: Non-existent IP (should NOT respond)
        System.out.println("Test 3: Non-existent IP (10.10.10.50)");
        start = System.currentTimeMillis();
        NetworkStatus nonexist = validator.checkIP("10.10.10.50");
        elapsed = System.currentTimeMillis() - start;
        
        System.out.println("  Responds: " + nonexist.responds);
        System.out.println("  Time: " + elapsed + "ms");
        System.out.println("  Response time: " + nonexist.responseTime + "ms");
        System.out.println();
        
        // Test 4: Parallel check (5 IPs)
        System.out.println("Test 4: Parallel check (5 IPs)");
        List<String> ips = Arrays.asList(
            "127.0.0.1",
            "8.8.8.8",
            "10.10.10.50",
            "10.10.10.51",
            "10.10.10.99"
        );
        
        start = System.currentTimeMillis();
        Map<String, NetworkStatus> results = validator.checkIPs(ips);
        elapsed = System.currentTimeMillis() - start;
        
        System.out.println("  Total time for 5 IPs: " + elapsed + "ms");
        System.out.println("  Results:");
        for (String ip : ips) {
            NetworkStatus status = results.get(ip);
            if (status != null) {
                System.out.println("    " + ip + " → " + 
                    (status.responds ? "✅ responds" : "❌ no response") +
                    " (" + status.responseTime + "ms)");
            } else {
                System.out.println("    " + ip + " → ⚠️  timeout");
            }
        }
        
        System.out.println();
        System.out.println("=".repeat(70));
        
        // Verdict
        if (!google.responds && !local.responds) {
            System.out.println("⚠️  WARNING: Java's isReachable() may not work on your system!");
            System.out.println("   This requires ICMP permissions or root privileges.");
            System.out.println("   Consider using external ping command instead.");
        } else {
            System.out.println("✅ Network validation is working correctly!");
            System.out.println("   Parallel execution saved: ~" + 
                ((ips.size() * 1000) - elapsed) + "ms");
        }
    }
}

