package com.ipam.commands;

import com.ipam.IPAMService;
import com.ipam.models.UtilizationReport;
import com.dnsmanager.config.DatabaseConfig;

import java.util.List;

/**
 * CLI Command: ipam-usage
 * Show subnet utilization statistics
 */
public class IPAMUsageCommand {
    
    public static void main(String[] args) {
        try {
            DatabaseConfig.initialize();
            IPAMService ipam = new IPAMService();
            
            if (args.length == 0) {
                // Show all subnets
                List<String> subnets = ipam.getAllSubnets();
                printAllSubnets(ipam, subnets);
            } else {
                // Show specific subnet
                String cidr = args[0];
                UtilizationReport report = ipam.getUtilization(cidr);
                printReport(report);
                
                // Show gaps if requested
                if (args.length > 1 && args[1].equals("--gaps")) {
                    printGaps(ipam, cidr);
                }
            }
            
            DatabaseConfig.close();
            
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void printReport(UtilizationReport report) {
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║       SUBNET UTILIZATION REPORT                  ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Subnet:            " + report.getSubnet());
        System.out.println("Total IPs:         " + report.getTotalIPs());
        System.out.println("Usable IPs:        " + report.getUsableIPs());
        System.out.println();
        System.out.println("Allocated:         " + report.getAllocatedIPs() + 
                          " (" + String.format("%.1f%%", report.getUtilizationPercent()) + ")");
        System.out.println("Available:         " + report.getAvailableIPs());
        System.out.println();
        System.out.println("Status:            " + getStatusWithColor(report.getStatus()));
        System.out.println("Next Available IP: " + 
                          (report.getNextAvailableIP() != null ? report.getNextAvailableIP() : "None"));
        System.out.println();
        
        printUtilizationBar(report.getUtilizationPercent());
    }
    
    private static void printUtilizationBar(double percent) {
        int barWidth = 40;
        int filled = (int) (barWidth * percent / 100.0);
        
        System.out.print("Utilization: [");
        for (int i = 0; i < barWidth; i++) {
            if (i < filled) {
                System.out.print("█");
            } else {
                System.out.print("░");
            }
        }
        System.out.println("] " + String.format("%.1f%%", percent));
        System.out.println();
    }
    
    private static String getStatusWithColor(String status) {
        switch (status) {
            case "CRITICAL": return "🔴 " + status;
            case "HIGH": return "🟠 " + status;
            case "MODERATE": return "🟡 " + status;
            case "LOW": return "🟢 " + status;
            default: return status;
        }
    }
    
    private static void printAllSubnets(IPAMService ipam, List<String> subnets) throws Exception {
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║         ALL SUBNETS UTILIZATION                  ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        System.out.println();
        
        if (subnets.isEmpty()) {
            System.out.println("No subnets found in database.");
            return;
        }
        
        System.out.println(String.format("%-20s %-10s %-10s %-10s", 
                                        "Subnet", "Allocated", "Available", "Status"));
        System.out.println("─".repeat(60));
        
        for (String subnet : subnets) {
            UtilizationReport report = ipam.getUtilization(subnet);
            System.out.println(String.format("%-20s %-10d %-10d %s", 
                                            subnet, 
                                            report.getAllocatedIPs(),
                                            report.getAvailableIPs(),
                                            getStatusEmoji(report.getStatus())));
        }
        System.out.println();
    }
    
    private static String getStatusEmoji(String status) {
        switch (status) {
            case "CRITICAL": return "🔴";
            case "HIGH": return "🟠";
            case "MODERATE": return "🟡";
            case "LOW": return "🟢";
            default: return "⚪";
        }
    }
    
    private static void printGaps(IPAMService ipam, String cidr) throws Exception {
        List<String[]> gaps = ipam.findGaps(cidr);
        
        System.out.println("IP ALLOCATION GAPS:");
        System.out.println("─".repeat(60));
        
        if (gaps.isEmpty()) {
            System.out.println("No gaps found - fully allocated or fully empty");
        } else {
            for (int i = 0; i < gaps.size(); i++) {
                String[] gap = gaps.get(i);
                long start = com.ipam.utils.IPAddressUtil.ipToLong(gap[0]);
                long end = com.ipam.utils.IPAddressUtil.ipToLong(gap[1]);
                long size = end - start + 1;
                
                System.out.println(String.format("Gap %d: %s - %s (%d IPs)", 
                                                i + 1, gap[0], gap[1], size));
            }
        }
        System.out.println();
    }
}
