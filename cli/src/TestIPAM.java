import com.ipam.*;
import com.ipam.models.*;
import com.ipam.utils.*;

import java.util.*;

/**
 * IPAM Unit Tests
 * Tests core functionality without database
 */
public class TestIPAM {
    
    private static int passed = 0;
    private static int failed = 0;
    
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║           IPAM UNIT TESTS                        ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        System.out.println();
        
        testIPConversion();
        testCIDRParsing();
        testSubnetCalculation();
        testIPAllocation();
        testUtilization();
        testGapFinding();
        
        System.out.println();
        System.out.println("═".repeat(60));
        System.out.println(String.format("RESULTS: %d passed, %d failed", passed, failed));
        System.out.println("═".repeat(60));
        
        if (failed > 0) {
            System.exit(1);
        }
    }
    
    private static void testIPConversion() {
        System.out.println("TEST: IP Conversion");
        System.out.println("─".repeat(60));
        
        // IP to Long
        test("10.0.0.1 -> long", 
            IPAddressUtil.ipToLong("10.0.0.1") == 167772161L);
        
        // Long to IP
        test("167772161 -> 10.0.0.1",
            IPAddressUtil.longToIP(167772161L).equals("10.0.0.1"));
        
        // Round trip
        String ip = "192.168.1.100";
        test("Round trip: " + ip,
            IPAddressUtil.longToIP(IPAddressUtil.ipToLong(ip)).equals(ip));
        
        System.out.println();
    }
    
    private static void testCIDRParsing() {
        System.out.println("TEST: CIDR Parsing");
        System.out.println("─".repeat(60));
        
        String[] parts = IPAddressUtil.parseCIDR("10.20.0.0/24");
        test("Parse 10.20.0.0/24 network", parts[0].equals("10.20.0.0"));
        test("Parse 10.20.0.0/24 prefix", parts[1].equals("24"));
        
        test("Prefix /24 -> mask",
            IPAddressUtil.prefixToMask(24).equals("255.255.255.0"));
        
        test("Prefix /16 -> mask",
            IPAddressUtil.prefixToMask(16).equals("255.255.0.0"));
        
        System.out.println();
    }
    
    private static void testSubnetCalculation() {
        System.out.println("TEST: Subnet Calculation");
        System.out.println("─".repeat(60));
        
        Subnet subnet = new Subnet("10.20.0.0/24");
        
        test("Network address", subnet.getNetworkAddress().equals("10.20.0.0"));
        test("Broadcast address", subnet.getBroadcastAddress().equals("10.20.0.255"));
        test("First usable IP", subnet.getFirstUsableIP().equals("10.20.0.1"));
        test("Last usable IP", subnet.getLastUsableIP().equals("10.20.0.254"));
        test("Total IPs", subnet.getTotalIPs() == 256);
        test("Usable IPs", subnet.getUsableIPs() == 254);
        
        test("Contains 10.20.0.50", subnet.contains("10.20.0.50"));
        test("Does not contain 10.21.0.1", !subnet.contains("10.21.0.1"));
        
        System.out.println();
    }
    
    private static void testIPAllocation() {
        System.out.println("TEST: IP Allocation");
        System.out.println("─".repeat(60));
        
        Subnet subnet = new Subnet("10.30.0.0/29"); // Only 6 usable IPs
        Set<String> used = new HashSet<>();
        
        // First IP should be 10.30.0.1
        String first = IPAllocator.findNextAvailable(subnet, used);
        test("First available IP", first.equals("10.30.0.1"));
        
        // Mark as used
        used.add("10.30.0.1");
        used.add("10.30.0.2");
        
        // Next should be 10.30.0.3
        String next = IPAllocator.findNextAvailable(subnet, used);
        test("Next available IP", next.equals("10.30.0.3"));
        
        // Fill subnet
        used.add("10.30.0.3");
        used.add("10.30.0.4");
        used.add("10.30.0.5");
        used.add("10.30.0.6");
        
        // Should be exhausted
        String exhausted = IPAllocator.findNextAvailable(subnet, used);
        test("Subnet exhausted", exhausted == null);
        
        System.out.println();
    }
    
    private static void testUtilization() {
        System.out.println("TEST: Utilization Calculation");
        System.out.println("─".repeat(60));
        
        Subnet subnet = new Subnet("10.40.0.0/24");
        Set<String> used = new HashSet<>();
        
        // Empty subnet
        double util1 = IPAllocator.calculateUtilization(subnet, used);
        test("0% utilization", util1 == 0.0);
        
        // 50% full (127 of 254)
        for (int i = 1; i <= 127; i++) {
            used.add("10.40.0." + i);
        }
        double util2 = IPAllocator.calculateUtilization(subnet, used);
        test("50% utilization", Math.abs(util2 - 50.0) < 0.1);
        
        System.out.println();
    }
    
    private static void testGapFinding() {
        System.out.println("TEST: Gap Finding");
        System.out.println("─".repeat(60));
        
        Subnet subnet = new Subnet("10.50.0.0/28"); // 14 usable IPs
        Set<String> used = new HashSet<>();
        
        // Create gaps: use 1-3, skip 4-6, use 7-9, skip 10-14
        used.add("10.50.0.1");
        used.add("10.50.0.2");
        used.add("10.50.0.3");
        used.add("10.50.0.7");
        used.add("10.50.0.8");
        used.add("10.50.0.9");
        
        List<String[]> gaps = IPAllocator.findGaps(subnet, used);
        
        test("Found 2 gaps", gaps.size() == 2);
        
        if (gaps.size() >= 2) {
            test("First gap starts at 10.50.0.4", gaps.get(0)[0].equals("10.50.0.4"));
            test("First gap ends at 10.50.0.6", gaps.get(0)[1].equals("10.50.0.6"));
            test("Second gap starts at 10.50.0.10", gaps.get(1)[0].equals("10.50.0.10"));
        }
        
        System.out.println();
    }
    
    private static void test(String name, boolean condition) {
        if (condition) {
            System.out.println("  ✅ " + name);
            passed++;
        } else {
            System.out.println("  ❌ " + name);
            failed++;
        }
    }
}
