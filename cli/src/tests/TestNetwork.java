import com.dnsmanager.services.EnterpriseNetworkValidator;
import com.dnsmanager.services.EnterpriseNetworkValidator.IPValidationResult;

public class TestNetwork {
    public static void main(String[] args) {
        EnterpriseNetworkValidator v = new EnterpriseNetworkValidator();
        System.out.println("🔍 Testing Network Validator...\n");
        
        System.out.println("Test 1: Localhost (127.0.0.1)");
        IPValidationResult r1 = v.validateIP("127.0.0.1");
        System.out.println("  Result: " + (r1.isActive ? "✅ Detected as active" : "❌ Not detected"));
        
        System.out.println("\nTest 2: Non-existent (10.10.99.99)");
        IPValidationResult r2 = v.validateIP("10.10.99.99");
        System.out.println("  Result: " + (!r2.isActive ? "✅ Marked as safe" : "⚠️  Detected as active"));
        
        System.out.println("\n✅ Network validator tests complete!");
    }
}
