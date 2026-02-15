package com.ipam.commands;

import com.ipam.IPAMService;
import com.ipam.models.Subnet;
import com.dnsmanager.config.DatabaseConfig;

/**
 * CLI Command: ipam-calc
 * Calculate subnet information from CIDR
 */
public class IPAMCalcCommand {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            printUsage();
            System.exit(1);
        }
        
        String cidr = args[0];
        
        try {
            IPAMService ipam = new IPAMService();
            Subnet subnet = ipam.calculateSubnet(cidr);
            
            printSubnetInfo(subnet);
            
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static void printSubnetInfo(Subnet subnet) {
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║           SUBNET CALCULATOR                      ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("CIDR Notation:      " + subnet.getCidr());
        System.out.println("Subnet Mask:        " + subnet.getSubnetMask());
        System.out.println("Prefix Length:      /" + subnet.getPrefixLength());
        System.out.println();
        System.out.println("Network Address:    " + subnet.getNetworkAddress());
        System.out.println("Broadcast Address:  " + subnet.getBroadcastAddress());
        System.out.println();
        System.out.println("First Usable IP:    " + subnet.getFirstUsableIP());
        System.out.println("Last Usable IP:     " + subnet.getLastUsableIP());
        System.out.println();
        System.out.println("Total IP Addresses: " + subnet.getTotalIPs());
        System.out.println("Usable IPs:         " + subnet.getUsableIPs());
        System.out.println();
    }
    
    private static void printUsage() {
        System.out.println("Usage: IPAMCalcCommand <cidr>");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java IPAMCalcCommand 10.20.0.0/24");
        System.out.println("  java IPAMCalcCommand 192.168.1.0/28");
        System.out.println("  java IPAMCalcCommand 172.16.0.0/16");
    }
}
