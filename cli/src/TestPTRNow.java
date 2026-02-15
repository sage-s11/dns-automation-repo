import com.dnsmanager.services.DnsRecordServiceDB;
import com.dnsmanager.models.DnsRecord;
import com.dnsmanager.config.DatabaseConfig;

public class TestPTRNow {
    public static void main(String[] args) throws Exception {
        DatabaseConfig.initialize();
        DnsRecordServiceDB recordService = new DnsRecordServiceDB();
        
        System.out.println("🔍 Testing AUTO-PTR (Fixed Version)...\n");
        
        DnsRecord record = recordService.addARecord(
            "feature-test.local",
            "auto-ptr-test",
            "10.10.10.77",
            3600,
            true  // CREATE PTR!
        );
        
        System.out.println("✅ A record created: " + record.getHostname() + " → " + record.getValue());
        
        DatabaseConfig.close();
        
        System.out.println("\nNow check database for PTR record:");
        System.out.println("hostname='77', zone='10.10.10.in-addr.arpa', value='auto-ptr-test.feature-test.local.'");
    }
}
