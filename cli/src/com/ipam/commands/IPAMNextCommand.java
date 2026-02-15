package com.ipam.commands;

import com.ipam.IPAMService;
import com.ipam.models.AllocationResult;
import com.dnsmanager.config.DatabaseConfig;

import java.util.List;

/**
 * CLI Command: ipam-next
 * Find next available IP in subnet
 */
public class IPAMNextCommand {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            printUsage();
            System.exit(1);
        }
        
        String cidr = args[0];
        int count = 1;
        
        // Optional: request multiple IPs
        if (args.length > 1) {
            try {
                count = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid count: " + args[1]);
                System.exit(1);
            }
        }
        
        try {
            DatabaseConfig.initialize();
            IPAMService ipam = new IPAMService();
            
            if (count == 1) {
                AllocationResult result = ipam.getNextAvailableIP(cidr);
                printResult(result);
            } else {
                List<String> ips = ipam.getAvailableIPs(cidr, count);
                printMultipleResults(cidr, ips, count);
            }
            
            DatabaseConfig.close();
            
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void printResult(AllocationResult result) {
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║         NEXT AVAILABLE IP                        ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        System.out.println();
        
        if (result.isSuccess()) {
            System.out.println("✅ Next Available IP: " + result.getIpAddress());
            System.out.println("   Subnet: " + result.getSubnet());
        } else {
            System.out.println("❌ " + result.getMessage());
            System.out.println("   Subnet: " + result.getSubnet());
        }
        System.out.println();
    }
    
    private static void printMultipleResults(String cidr, List<String> ips, int requested) {
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║      AVAILABLE IP ADDRESSES                      ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Subnet: " + cidr);
        System.out.println("Requested: " + requested);
        System.out.println("Found: " + ips.size());
        System.out.println();
        
        if (ips.isEmpty()) {
            System.out.println("❌ No available IPs in subnet");
        } else {
            System.out.println("Available IPs:");
            for (int i = 0; i < ips.size(); i++) {
                System.out.println("  " + (i + 1) + ". " + ips.get(i));
            }
            
            if (ips.size() < requested) {
                System.out.println();
                System.out.println("⚠️  Only " + ips.size() + " of " + requested + " requested IPs available");
            }
        }
        System.out.println();
    }
    
    private static void printUsage() {
        System.out.println("Usage: IPAMNextCommand <cidr> [count]");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java IPAMNextCommand 10.20.0.0/24");
        System.out.println("  java IPAMNextCommand 10.20.0.0/24 5");
    }
}
