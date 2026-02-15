import com.dnsmanager.services.ZoneServiceDB;
import com.dnsmanager.models.Zone;
import com.dnsmanager.config.DatabaseConfig;

public class TestZones {
    public static void main(String[] args) throws Exception {
        DatabaseConfig.initialize();
        ZoneServiceDB zoneService = new ZoneServiceDB();
        
        System.out.println("🔍 Testing Zone Management...\n");
        
        // Test 1: Create zone
        System.out.println("Test 1: Creating zone 'feature-test.local'");
        try {
            Zone zone = zoneService.createZone("feature-test.local", "10.10.10.1");
            System.out.println("✅ Zone created: " + zone.getName() + " (ID: " + zone.getId() + ")");
        } catch (Exception e) {
            System.out.println("⚠️  Zone might already exist");
        }
        
        // Test 2: Get zone
        System.out.println("\nTest 2: Retrieving zone");
        Zone zone = zoneService.getZone("feature-test.local");
        if (zone != null) {
            System.out.println("✅ Zone retrieved: " + zone.getName());
            System.out.println("   NS IP: " + zone.getNsIp());
            System.out.println("   Serial: " + zone.getSerial());
        } else {
            System.out.println("❌ Zone not found");
        }
        
        DatabaseConfig.close();
        System.out.println("\n✅ Zone management tests complete!");
    }
}
