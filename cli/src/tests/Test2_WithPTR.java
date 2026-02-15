import com.dnsmanager.services.DnsRecordServiceDB;
import com.dnsmanager.models.DnsRecord;
import com.dnsmanager.config.DatabaseConfig;

public class Test2_WithPTR {
    public static void main(String[] args) throws Exception {
        DatabaseConfig.initialize();
        DnsRecordServiceDB recordService = new DnsRecordServiceDB();
        
        System.out.println("TEST 2: Creating A record WITH AUTO-PTR");
        System.out.println("=".repeat(60));
        
        DnsRecord record = recordService.addARecord(
            "feature-test.local",
            "test2-with-ptr",
            "10.10.10.102",
            3600,
            true  // CREATE PTR!
        );
        
        System.out.println("✅ SUCCESS!");
        System.out.println("   Hostname: " + record.getHostname());
        System.out.println("   IP: " + record.getValue());
        System.out.println("   Zone: feature-test.local");
        
        DatabaseConfig.close();
    }
}
