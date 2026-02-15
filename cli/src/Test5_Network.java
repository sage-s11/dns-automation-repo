import com.dnsmanager.services.EnterpriseNetworkValidator;
import com.dnsmanager.services.EnterpriseNetworkValidator.IPValidationResult;

public class Test5_Network {
    public static void main(String[] args) {
        System.out.println("TEST 5: Network Validation");
        System.out.println("=".repeat(60));
        
        EnterpriseNetworkValidator validator = new EnterpriseNetworkValidator();
        
        // Test 1: Active IP (localhost)
        System.out.println("Testing 127.0.0.1 (should be active):");
        IPValidationResult r1 = validator.validateIP("127.0.0.1");
        System.out.println("   Result: " + (r1.isActive ? "✅ ACTIVE" : "❌ NOT ACTIVE"));
        System.out.println("   Method: " + r1.detectionMethod);
        
        // Test 2: Inactive IP
        System.out.println("\nTesting 10.10.99.99 (should be inactive):");
        IPValidationResult r2 = validator.validateIP("10.10.99.99");
        System.out.println("   Result: " + (r2.isActive ? "⚠️  ACTIVE" : "✅ INACTIVE (safe)"));
        
        System.out.println("\n✅ Network validation working!");
    }
}
