import com.dnsmanager.services.CleanupService;
import com.dnsmanager.config.DatabaseConfig;
import java.util.List;
import java.util.Map;

public class Test8_Cleanup_Fixed {
    public static void main(String[] args) throws Exception {
        DatabaseConfig.initialize();
        CleanupService cleanup = new CleanupService();
        
        System.out.println("TEST 8: Cleanup Service");
        System.out.println("=".repeat(60));
        
        // Find records by pattern
        System.out.println("Finding records with pattern 'test%'...");
        List<Map<String, Object>> records = cleanup.findByPattern("test%");
        
        System.out.println("Found " + records.size() + " record(s)");
        
        if (!records.isEmpty()) {
            // Validate for deletion
            CleanupService.DeletionValidationResult result = cleanup.validateForDeletion(records);
            
            System.out.println("\nDeletion Analysis:");
            System.out.println("  Safe to delete: " + result.getSafeCount());
            System.out.println("  Blocked (active): " + result.getBlockedCount());
            
            if (result.hasBlockedRecords()) {
                System.out.println("\n⚠️  Some records are active and blocked from deletion");
            }
        }
        
        DatabaseConfig.close();
        System.out.println("\n✅ Cleanup service test complete!");
    }
}
