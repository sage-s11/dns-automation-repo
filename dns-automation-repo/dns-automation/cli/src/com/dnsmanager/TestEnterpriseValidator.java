package com.dnsmanager;

import com.dnsmanager.services.EnterpriseNetworkValidator;
import com.dnsmanager.services.EnterpriseNetworkValidator.IPValidationResult;
import java.util.*;

public class TestEnterpriseValidator {
    public static void main(String[] args) {
        
        System.out.println("🏢 Enterprise Network Validator Test\n");
        System.out.println("=".repeat(70));
        System.out.println();
        
        EnterpriseNetworkValidator validator = new EnterpriseNetworkValidator();
        
        // Test IPs across different scenarios
        List<String> testIPs = Arrays.asList(
            "127.0.0.1",      // Localhost (should detect via TCP)
            "8.8.8.8",        // Google DNS (should detect via ping)
            "1.1.1.1",        // Cloudflare DNS
            "192.168.1.1",    // Typical router
            "10.10.10.50",    // Non-existent (should be safe)
            "10.10.10.51",    // Non-existent
            "172.16.0.1"      // Non-existent
        );
        
        System.out.println("Testing individual IPs:");
        System.out.println("-".repeat(70));
        
        for (String ip : testIPs.subList(0, 3)) {
            IPValidationResult result = validator.validateIP(ip);
            System.out.println(result);
        }
        
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println("Testing batch validation (parallel):");
        System.out.println("=".repeat(70));
        
        Map<String, IPValidationResult> results = validator.validateIPs(testIPs);
        
        System.out.println();
        System.out.println("Results:");
        System.out.println("-".repeat(70));
        
        for (String ip : testIPs) {
            IPValidationResult result = results.get(ip);
            if (result != null) {
                System.out.println(result);
            }
        }
        
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println("Summary: " + 
            EnterpriseNetworkValidator.getSummary(results));
        System.out.println("=".repeat(70));
    }
}
