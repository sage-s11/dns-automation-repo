import com.dnsmanager.services.ZoneServiceDB;
import com.dnsmanager.models.Zone;
import com.dnsmanager.config.DatabaseConfig;

public class Test3_ZoneCreate {
    public static void main(String[] args) throws Exception {
        DatabaseConfig.initialize();
        ZoneServiceDB zoneService = new ZoneServiceDB();
        
        System.out.println("TEST 3: Creating new zone");
        System.out.println("=".repeat(60));
        
        String zoneName = "verification-test.local";
        
        Zone zone = zoneService.createZone(zoneName, "10.10.10.1");
        
        System.out.println("✅ SUCCESS!");
        System.out.println("   Zone: " + zone.getName());
        System.out.println("   ID: " + zone.getId());
        System.out.println("   NS IP: " + zone.getNsIp());
        System.out.println("   Serial: " + zone.getSerial());
        
        DatabaseConfig.close();
    }
}
