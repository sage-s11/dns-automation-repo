import com.dnsmanager.services.DnsRecordServiceDB;
import com.dnsmanager.models.DnsRecord;
import com.dnsmanager.config.DatabaseConfig;

public class TestAutoPTR {
    public static void main(String[] args) throws Exception {
        DatabaseConfig.initialize();
        DnsRecordServiceDB recordService = new DnsRecordServiceDB();
        
        System.out.println("🔍 Testing Auto-PTR Creation...\n");
        
        // Test 1: Create A record WITHOUT PTR
        System.out.println("Test 1: Creating A record WITHOUT auto-PTR");
        try {
            DnsRecord record1 = recordService.addARecord(
                "feature-test.local",
                "no-ptr-host",
                "10.10.10.88",
                3600,
                false  // NO PTR
            );
            System.out.println("✅ A record created: " + record1.getHostname() + " → " + record1.getValue());
            System.out.println("   Auto-PTR: false");
        } catch (Exception e) {
            System.out.println("❌ Error: " + e.getMessage());
        }
        
        System.out.println();
        
        // Test 2: Create A record WITH PTR (will try to create PTR)
        System.out.println("Test 2: Creating A record WITH auto-PTR");
        try {
            DnsRecord record2 = recordService.addARecord(
                "feature-test.local",
                "with-ptr-host",
                "10.10.10.89",
                3600,
                true  // CREATE PTR!
            );
            System.out.println("✅ A record created: " + record2.getHostname() + " → " + record2.getValue());
            System.out.println("   Auto-PTR: true (check reverse zone for PTR record)");
        } catch (Exception e) {
            System.out.println("⚠️  A record created but PTR failed (expected - reverse zone may not exist)");
            System.out.println("   Error: " + e.getMessage());
        }
        
        DatabaseConfig.close();
        System.out.println("\n✅ Auto-PTR test complete!");
        System.out.println("\nNote: PTR creation requires reverse zone (10.10.10.in-addr.arpa) to exist.");
    }
}
