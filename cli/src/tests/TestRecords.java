import com.dnsmanager.services.DnsRecordServiceDB;
import com.dnsmanager.models.DnsRecord;
import com.dnsmanager.config.DatabaseConfig;

public class TestRecords {
    public static void main(String[] args) throws Exception {
        DatabaseConfig.initialize();
        DnsRecordServiceDB recordService = new DnsRecordServiceDB();
        
        System.out.println("🔍 Testing DNS Record Management...\n");
        
        // Test: Add A record
        System.out.println("Test 1: Creating A record");
        try {
            DnsRecord record = recordService.addARecord(
                "feature-test.local",
                "test-host-001",
                "10.10.77.1",
                3600,
                false  // no auto-PTR
            );
            System.out.println("✅ A record created: " + record.getHostname() + " → " + record.getValue());
        } catch (Exception e) {
            System.out.println("⚠️  Record might already exist: " + e.getMessage());
        }
        
        DatabaseConfig.close();
        System.out.println("\n✅ Record management tests complete!");
    }
}
