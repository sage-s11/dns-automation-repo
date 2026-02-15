import com.dnsmanager.services.DnsRecordServiceDB;
import com.dnsmanager.models.DnsRecord;
import com.dnsmanager.config.DatabaseConfig;

public class Test1_SingleRecord {
    public static void main(String[] args) throws Exception {
        DatabaseConfig.initialize();
        DnsRecordServiceDB recordService = new DnsRecordServiceDB();
        
        System.out.println("TEST 1: Creating single A record WITHOUT PTR");
        System.out.println("=".repeat(60));
        
        DnsRecord record = recordService.addARecord(
            "feature-test.local",
            "test1-no-ptr",
            "10.10.10.101",
            3600,
            false  // NO PTR
        );
        
        System.out.println("✅ SUCCESS!");
        System.out.println("   Hostname: " + record.getHostname());
        System.out.println("   IP: " + record.getValue());
        System.out.println("   Zone: feature-test.local");
        System.out.println("   PTR Created: NO");
        
        DatabaseConfig.close();
    }
}
