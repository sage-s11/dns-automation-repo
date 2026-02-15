import com.dnsmanager.services.CleanupService;
import com.dnsmanager.config.DatabaseConfig;

public class Test8_Cleanup {
    public static void main(String[] args) throws Exception {
        DatabaseConfig.initialize();
        CleanupService cleanup = new CleanupService();
        
        System.out.println("TEST 8: Cleanup Service (Stale Record Analysis)");
        System.out.println("=".repeat(60));
        
        cleanup.analyzeStaleRecords("verification-test.local", 365);
        
        DatabaseConfig.close();
        System.out.println("\n✅ Cleanup analysis complete!");
    }
}
